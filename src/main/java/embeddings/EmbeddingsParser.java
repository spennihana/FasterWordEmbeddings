package embeddings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

/**
 * This class parses word embeddings.
 *
 * Raw bytes of the word embeddings file are read into RAM with concurrent invocations of DiskReadTask.
 *
 * All of the hot loops are branch free, do no GC, use mostly simple (low-instruction count) ops, and rely on
 * primitives as much as possible.
 */
public class EmbeddingsParser {

  public static final int NBYTES=3; // all vector values are 3 bytes long

  // output
  HashMap<BufferedBytes, BufferedBytes>[] _maps;

  // header pieces
  private boolean _str_type; // true => strlen is 2 bytes; false => strlen is 1 byte
  short _vec_sz; // number of elements in a vector
  byte _scale; // power-of-10 exponent
  int _shift; // add this back to each vector value
  short _nchks; // number of independent tasks
  private long[] _offs; // read file at these byte offsets (zero'd after the header!)

  private String _path; // path to the embeddings
  private long _nbytes;
  private DiskReadTask [] _rtasks;
  private BuildEmbeddingsTask[] _ptasks;

  public static EmbeddingsParser parse(String path) {
    return new EmbeddingsParser(path).readFromDisk().buildEmbeddingsMap();
  }

  private EmbeddingsParser(String path) {
    File f = new File(_path=path);
    _nbytes = f.length();
    parseHeader(f);
    _rtasks = new DiskReadTask[_nchks]; // raw disk read to RAM tasks
    _ptasks = new BuildEmbeddingsTask[_nchks]; // map building tasks
  }

  private void parseHeader(File f) {
    try( FileInputStream fs = new FileInputStream(f) ) {
      _str_type = read1(fs)==1;
      _vec_sz   = read2(fs);
      _scale    = read1(fs);
      _shift    = read3(fs);
      _nchks    = read2(fs);
      _offs = new long[_nchks];
      long headerBytes = 1L + 2L + 1L + 3L + 2L + (_nchks-1)*8L;
      int i=1;
      _offs[0] = headerBytes;
      while(i < _offs.length)
        _offs[i++] = read8(fs) + headerBytes;
    } catch( Exception e) {
      throw new RuntimeException(e);
    }
  }

  private int   read1U(FileInputStream fs) throws IOException { return read1(fs) & 0xFF; }
  private byte  read1(FileInputStream fs) throws IOException { return (byte)fs.read(); }
  private short read2(FileInputStream fs) throws IOException { return (short)(read1U(fs) | read1U(fs) << 8); }
  private int   read3(FileInputStream fs) throws IOException { return read1U(fs) | read1U(fs) << 8 | read1(fs) << 16; }
  private long  read8(FileInputStream fs) throws IOException {
    return ((((long)read1(fs) & 0xFF)       ) |
           (( (long)read1(fs) & 0xFF) << 8  ) |
           (( (long)read1(fs) & 0xFF) << 16 ) |
           (( (long)read1(fs) & 0xFF) << 24 ) |
           (( (long)read1(fs) & 0xFF) << 32 ) |
           (( (long)read1(fs) & 0xFF) << 40 ) |
           (( (long)read1(fs) & 0xFF) << 48 ) |
           (( (long)read1(fs) & 0xFF) << 56 ));
  }

  private EmbeddingsParser readFromDisk() {
    long start = System.currentTimeMillis();
    ArrayList<DiskReadTask> rtasks = new ArrayList<>();
    for(int i=0;i<_rtasks.length;++i) {
      boolean last = i==_rtasks.length-1;
      int chkSize = last ? (int)(_nbytes - _offs[i]) : (int)(_offs[i+1] - _offs[i]);
      _rtasks[i] = new DiskReadTask(i,_path,_offs[i],chkSize);
      rtasks.add(_rtasks[i]);
    }
    ForkJoinTask.invokeAll(rtasks);
    System.out.println("Disk to RAM read in " + (System.currentTimeMillis() - start)/1000. + " seconds" );
    return this;
  }

  /**
   * The first part of the parallel parse. An instance of this class reads in a small
   * chunk of data (4MB roughly) using the FileChannel API to set a Random Access starting
   * offset to read from.
   */
  private static class DiskReadTask extends RecursiveAction {
    int _cidx;
    byte[] _chk;
    int _chkSize;
    final long _off;
    final String _path;
    ByteBuffer _bb=null;
    DiskReadTask(int cidx, String path, long offset, int chkSize) {
      _cidx=cidx;
      _path=path;
      _off=offset;
      _chkSize=chkSize;
      _chk=new byte[_chkSize];
    }
    @Override protected void compute() {
      try( FileInputStream s = new FileInputStream(new File(_path))) {
        FileChannel fc = s.getChannel();
        fc.position(_off);
        fc.read(_bb=ByteBuffer.wrap(_chk));
      } catch( Exception e) {
        System.err.println("chunk: " + _cidx + "; bytesToRead: " + _chkSize +"; offset: " + _off);
        throw new RuntimeException(e);
      }
    }
  }

  private EmbeddingsParser buildEmbeddingsMap() {
    long start = System.currentTimeMillis();
    ArrayList<BuildEmbeddingsTask> ptasks = new ArrayList<>();
    HashMap<BufferedBytes,BufferedBytes> maps[] = new HashMap[_nchks];

    for(int i=0;i<_ptasks.length;++i) {
      _ptasks[i] = new BuildEmbeddingsTask(i,_rtasks[i]._chk,_str_type,_vec_sz);
      ptasks.add(_ptasks[i]);
      maps[i] = _ptasks[i]._embeddings;
    }
    ForkJoinTask.invokeAll(ptasks);
    double elapsed = (System.currentTimeMillis() - start)/1000.;
    int cnt=0;
    for (HashMap<BufferedBytes, BufferedBytes> map : maps)
      cnt += map.size();
    System.out.println("Processed " + cnt + " embeddings in " + elapsed + " seconds.");
    _maps=maps;
    return this;
  }

  /**
   * This is the core code for creating the necessary layer over the raw bytes.
   * The compute method is branch free and doesn't do any GC.
   */
  private static class BuildEmbeddingsTask extends RecursiveAction {
    byte[] _in;
    int _cidx;
    int _stype; // 1 when needing to parse an additional byte for string length; otherwise 0
    int _vsz;
    HashMap<BufferedBytes, BufferedBytes> _embeddings;
    BuildEmbeddingsTask(int cidx, byte[] in, boolean str_type, int vec_sz) {
      _cidx=cidx;
      _in=in;
      _stype=str_type?1:0;
      _vsz=vec_sz;
      _embeddings=new HashMap<>();
    }
    @Override protected void compute() {
      int pos=0;
      while(pos < _in.length) {
        int start=pos;
        int ssz; // string size

        // these two lines are setup "oddly" to avoid branching code like this:
        //   if( _stype==1 ) ssz = _in[pos++] & 0xFF | (_in[pos++] & 0xFF) << 8;
        //   else            ssz = _in[pos++] & 0xFF;
        // these lines are instead branch free
        ssz = (_in[pos] & 0xFF) + _stype*( (_in[pos+_stype] & 0xFF) << 8);
        pos += (1+_stype);

        BufferedBytes bb = new BufferedBytes(_in,pos,ssz);
        _embeddings.put(bb,bb); // why use two objects when you could use one!
        pos += ssz + NBYTES*_vsz;
        assert pos-start == (1+_stype) + ssz + _vsz*NBYTES;
      }
    }
  }
}
