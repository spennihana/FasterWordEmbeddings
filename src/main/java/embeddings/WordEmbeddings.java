package embeddings;

import java.util.HashMap;
import static embeddings.EmbeddingsParser.NBYTES;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Sample class for using word embeddings.
 * Provides an enum that statically loads embeddings from the specified location.
 */
public class WordEmbeddings {
  private float _scale;
  private int _shift;
  private short _vec_sz;

  public enum EMBEDDINGS {
    GLOVE(EmbeddingsParser.parse("./lib/w2vec_models/glove.bin")),
    GOOGL(EmbeddingsParser.parse("./lib/w2vec_models/googl.bin"));

    private final WordEmbeddings _em;
    private EMBEDDINGS(EmbeddingsParser ep) {
      _em=new WordEmbeddings(ep._maps);
      _em._scale = 1.f/(float)Math.pow(10,ep._scale);
      _em._shift = ep._shift;
      _em._vec_sz = ep._vec_sz;
    }

    /**
     * Fill res with the word embeddings for word w.
     * The expectation is that res is zero'd out before use.
     * This will force the word into UTF_8 bytes.
     * This will allocate a new BufferedBytes instance to wrap the bytes of w.
     *
     * @param w lookup w in the word embeddings
     * @param res fill this float array with word embeddings
     */
    public void get(String w, float[] res) {
      _em.get(new BufferedBytes(w.getBytes(UTF_8)),res);
    }

    /**
     * Fill res with the word embeddings for word w.
     * The expectation is that res is zero'd out before use.
     * This allocates a new BufferedBytes instance to wrap w.
     *
     * @param w lookup w in the word embeddings
     * @param res fill this float array with word embeddings
     */
    public void get(byte[] w, float[] res) {
      _em.get(new BufferedBytes(w),res);
    }


    /**
     * Fill res with the word embeddings for the BufferedBytes instance.
     * This is the best performing option.
     * The expectation is that res is zero'd out before use.
     *
     * @param bb BufferedBytes holding the bytes of the word to lookup
     * @param res fill this float array with the word embeddings
     */
    public void get(BufferedBytes bb, float[] res) {
      _em.get(bb, res);
    }
  }

  private transient HashMap<BufferedBytes,BufferedBytes> _map;
  // takes the result of a parse_bin call and flattens all maps into a single map
  private WordEmbeddings(HashMap<BufferedBytes,BufferedBytes>[] maps) {
    _map=new HashMap<>();
    for (HashMap<BufferedBytes, BufferedBytes> map : maps) _map.putAll(map);
  }

  void get(BufferedBytes s, float[] res) {
    BufferedBytes bs= _map.get(s);
    if( bs==null )
      return;
    int off=bs._off + bs._len; // _off is the start of the string, _len is the length of the string
    byte[] buf = bs._buf;
    int idx=0;
    int i=off;
    for(;i<off+NBYTES*_vec_sz;i+=NBYTES) {
      // decode the embedding value by combining 3 bytes, then shift & scale
      int r = (buf[i  ] & 0xFF)      |
              (buf[i+1] & 0xFF) << 8 |
              (buf[i+2] & 0xFF) << 16;
      res[idx++] = ((r + _shift)*_scale);
    }
  }
}
