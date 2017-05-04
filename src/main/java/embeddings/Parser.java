package embeddings;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

public class Parser {
  public ReadTask[] _rtasks;
  ParseBitsTask[] _pbits;
  final int _bytesPerChk;
  final int _bytesPerLine;
  int _nchks;
  long _nbytes;
  String _path;
  int _strlen;
  int _sz;
  public Parser(String path, int strlen, int sz) {
    File f = new File(path);
    long nbytes = f.length();
    _sz=sz;
    _strlen=strlen;
    _bytesPerLine = _strlen + _sz + 4*300;
    _bytesPerChk = _bytesPerLine * (int)Math.ceil(ReadTask.DEFAULT_CHK_SIZE/(double)_bytesPerLine);
    int nchks = (int)Math.ceil((double)nbytes/(double)_bytesPerChk);
    _rtasks = new ReadTask[nchks];
    _nchks=nchks;
    _nbytes=nbytes;
    _path=path;
  }

  public HashMap<BufferedBytes, BufferedBytes>[] parse_bin() {
    long start = System.currentTimeMillis();
    raw_parse();
    System.out.println("Disk to RAM read in " + (System.currentTimeMillis() - start)/1000. + " seconds" );
    return parse_bits();
  }

  public void raw_parse() {
    ArrayList<ReadTask> rtasks = new ArrayList<>();
    for(int i=0;i<_rtasks.length;++i) {
      long fileOffset = (long)i * _bytesPerChk;
      int actualChkSize = i==_rtasks.length-1 ? (int)(_nbytes - _bytesPerChk * (_nchks-1)): _bytesPerChk;
      _rtasks[i] = new ReadTask(i,_path,fileOffset,actualChkSize);
      rtasks.add(_rtasks[i]);
    }
    ForkJoinTask.invokeAll(rtasks);
  }

  private HashMap<BufferedBytes, BufferedBytes>[] parse_bits() {
    long start = System.currentTimeMillis();
    _pbits = new ParseBitsTask[_rtasks.length];
    ArrayList<ParseBitsTask> ptasks = new ArrayList<>();
    HashMap<BufferedBytes,BufferedBytes> maps[] = new HashMap[_pbits.length];
    for(int i=0;i<_pbits.length;++i) {
      _pbits[i] = new ParseBitsTask(i,_rtasks[i]._chk,_strlen,_sz);
      ptasks.add(_pbits[i]);
      maps[i] = _pbits[i]._rows;
    }
    ForkJoinTask.invokeAll(ptasks);
    System.out.println("Finished reading raw bits in: " + (System.currentTimeMillis() - start)/1000. + " seconds");
    int cnt=0;
    for(int i=0;i<maps.length;++i) {
      cnt += maps[i].size();
    }
    System.out.println("Got " + cnt + " embeddings.");
    return maps;
  }

  public static class ParseBitsTask extends RecursiveAction {
    byte[] _in;
    int _cidx;
    int _strLen;
    int _sz;
    final HashMap<BufferedBytes, BufferedBytes> _rows;
    ParseBitsTask(int cidx, byte[] in, int strLen, int sz) {
      _in=in;
      _cidx=cidx;
      _strLen=strLen;
      _sz=sz;
      _rows= new HashMap<>();
    }
    @Override protected void compute() {
      int pos=0;
      while(pos < _in.length) {
        int start=pos;
        int sz;
        if( _sz==1 ) {
          sz = _in[pos++] & 0xFF;
        } else {
          sz = (_in[pos++] & 0xFF) | (_in[pos++] & 0xFF) << 8;
        }
        BufferedBytes bs = new BufferedBytes(_in,pos,sz);
        pos+=sz;
        pos+=_strLen-sz;
        _rows.put(bs,new BufferedBytes(_in,pos,4*300));
        pos+=4*300;
        assert pos-start == _strLen + _sz + 4*300;
      }
    }
  }

  static class ReadTask extends RecursiveAction {
    int _cidx;
    byte[] _chk;
    int _chkSize;
    private static final int DEFAULT_CHK_SIZE=1<<(20+2); // 4MB chunk sizes
    final long _off;
    final String _path;
    ByteBuffer _bb=null;
    ReadTask(int cidx, String path, long offset, int chkSize) {
      _cidx=cidx;_path=path; _off=offset; _chkSize=chkSize;
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

  public static void main(String[] args) {
    long s = System.currentTimeMillis();
    Parser r2 = new Parser("./lib/w2vec_models/glove.bin",1005,2);
    HashMap<BufferedBytes, BufferedBytes>[] glove = r2.parse_bin();
    r2._pbits = null; r2._rtasks=null;
    Parser r = new Parser("./lib/w2vec_models/googl.bin",208,1);
    HashMap<BufferedBytes, BufferedBytes>[] googl = r.parse_bin();
    r._pbits=null; r._rtasks=null;
    System.out.println("Total time to read vecs: " + (System.currentTimeMillis() - s)/1000. + " seconds");
    System.out.println(googl.length);
    System.out.println(glove.length);
  }
}
