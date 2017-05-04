package embeddings;


import java.util.Arrays;
import java.util.HashMap;

public class WordEmbeddings {
  private static final double SHIFT=1./1e6;

  public enum WORD_EM {
    GLOVE(new Parser("./lib/w2vec_models/glove.bin",1005,2).parse_bin()),
    GOOGL(new Parser("./lib/w2vec_models/googl.bin",208,1).parse_bin());
    private final WordEmbeddings _em;
    WORD_EM(HashMap<BufferedBytes,BufferedBytes>[] maps) { _em=new WordEmbeddings(maps); }
    public void get(String w, float[] res) {_em.get(new BufferedBytes(w),res); }
  }
  private transient HashMap<BufferedBytes,BufferedBytes> _map;
  private WordEmbeddings(HashMap<BufferedBytes,BufferedBytes>[] maps) {
    _map=new HashMap<>();
    for (HashMap<BufferedBytes, BufferedBytes> map : maps) _map.putAll(map);
  }

  void get(BufferedBytes s,double[] res) {
    Arrays.fill(res,0);
    BufferedBytes bs= _map.get(s);
    if( bs==null )
      return;
    int off=bs.getOffset();
    byte[] buf = bs.getBuffer();
    int idx=0;
    int i=off;
    for(;i<off+4*300;i+=4)
      res[idx++]=(double)readInt(i,buf)*SHIFT;
  }

  int readInt(int pos, byte[] buf) {
    return ( buf[pos++] & 0xFF ) | (buf[pos++] & 0xFF) <<8 | (buf[pos++] & 0xFF) << 16 | (buf[pos] & 0xFF) << 24;
  }
}
