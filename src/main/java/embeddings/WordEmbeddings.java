package embeddings;

import java.util.*;
import java.util.concurrent.CountedCompleter;

import static embeddings.EmbeddingsParser.NBYTES;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Sample class for using word embeddings.
 * Provides an enum that statically loads embeddings
 */
public class WordEmbeddings {
  private float _scale;
  private int _shift;
  private short _vec_sz;
  private int _nchks;

  public enum EMBEDDINGS {
    GLOVE(EmbeddingsParser.parse("./data/glove.bin")),
    GOOGL(EmbeddingsParser.parse("./data/googl.bin"));

    private final WordEmbeddings _em;
    private EMBEDDINGS(EmbeddingsParser ep) {
      _em=new WordEmbeddings(ep._maps);
      _em._scale = 1.f/(float)Math.pow(10,ep._scale);
      _em._shift = ep._shift;
      _em._vec_sz = ep._vec_sz;
      _em._nchks = ep._nchks;
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

    /**
     * Compute the cosine distance for this word and each word in the vocabulary. Return the top n most similar words.
     * @param word find similar words to this word
     * @param n return this number of words similar to word
     * @return return n most similar words
     */
    public SimilarWord[] mostSimilar(String word, int n) {
      if( n<=0 ) throw new IllegalArgumentException("n must be >= 1; got: " + n);
      if( _em._map.get(new BufferedBytes(word.getBytes(UTF_8)))==null ) throw new IllegalArgumentException(word + " is not in the word embeddings vocabulary");
      CompareTask ct = new CompareTask(word,n,_em);
      ct.invoke();
      SimilarWord[] res = new SimilarWord[n];
      while(!ct._res.isEmpty()) res[--n] = ct._res.poll();
      return res;
    }
  }

  private transient HashMap<BufferedBytes,BufferedBytes> _map;
  // takes the result of a parse_bin call and flattens all maps into a single map
  private WordEmbeddings(HashMap<BufferedBytes,BufferedBytes>[] maps) {
    _map=new HashMap<>();
    for (HashMap<BufferedBytes, BufferedBytes> map : maps) _map.putAll(map);
  }


  public void get(String w, float[] res) {
    get(new BufferedBytes(w.getBytes(UTF_8)),res);
  }

  private void get(BufferedBytes s, float[] res) {
    Arrays.fill(res,0);
    BufferedBytes bb= _map.get(s);
    if( bb==null )
      return;
    int off=bb._off + bb._len; // _off is the start of the string, _len is the length of the string
    byte[] buf = bb._buf;
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

  /**
   * Divide-conquer-combine using ForkJoin.
   *
   * Split up the collection of words to compare until sub-collection "leaf" has ~ nwords/nchunks elements.
   * Several instances of CompareTask will be created to spread the work out of F/J threads. Pairs of tasks
   * reduce their results together all the way back to the original fork point via onCompletion (where the original
   * top-level task has no completer).
   */
  private static class CompareTask extends CountedCompleter {

    CompareTask _left, _rite;
    int _lo, _hi;
    boolean _rootTask; // top level fork point; all results reduced here
    private final BufferedBytes[] _keys;
    private final WordEmbeddings _em;
    private final BufferedBytes _theWord;
    private final int _chkSize; // number of items ina "leaf" node

    final int _n;
    PriorityQueue<SimilarWord> _res;
    final float[] _wordEm;

    CompareTask(String word, int n, WordEmbeddings em) {
      _theWord = new BufferedBytes(word.getBytes(UTF_8));
      _lo=0;
      _hi=em._map.size();
      _chkSize= _hi/em._nchks;
      _keys = em._map.keySet().toArray(new BufferedBytes[_hi]);
      _em = em;
      _rootTask=true;
      _n=n;
      _wordEm = new float[em._vec_sz];
      em.get(_theWord,_wordEm);
      _res = new PriorityQueue<>(_n);
    }

    CompareTask(CompareTask cc) {
      super(cc);
      _theWord=cc._theWord;
      _chkSize=cc._chkSize;
      _rootTask=false;
      _keys=cc._keys;
      _em=cc._em;
      _lo=cc._lo;
      _hi=cc._hi;
      _n=cc._n;
      _wordEm=cc._wordEm;
      _res=new PriorityQueue<>(_n);
      setPendingCount(0);
    }
    @Override public void compute() {
      if( _hi - _lo >= _chkSize ) {
        final int mid = (_lo+_hi)>>>1;
        _left = new CompareTask(this);
        _rite = new CompareTask(this);
        _left._hi = mid;
        _rite._lo = mid;
        addToPendingCount(1);  // forks left, add this to await completion
        if( !isCompletedAbnormally() ) _left.fork();
        if( !isCompletedAbnormally() ) _rite.compute();
        return;
      }
      if( _hi > _lo ) compute1();
      tryComplete();
    }

    @Override public void onCompletion(CountedCompleter cc) {
      reduce(_left); _left=null;
      reduce(_rite); _rite=null;
    }

    void reduce(CompareTask that) {
      if( that==null ) return;
      for(SimilarWord sw: that._res) {
        if( _res.size()==_n ) _res.poll();
        _res.add(sw);
      }
    }

    void compute1() {
      float[] ems = new float[_em._vec_sz];
      while(_lo < _hi) {
        BufferedBytes word = _keys[_lo++];
        if( word.equals(_theWord) ) continue; // don't include the word of interest
        _em.get(word,ems);
        if( _res.size() == _n ) _res.poll();  // drop the "least" value from heap
        float dist = cosine_distance(_wordEm,ems);
        _res.add(new SimilarWord(word,dist));
      }
    }

    static float cosine_distance(float[] a, float[] b) {
      float sum_ab = 0;
      float sum_a2 = 0;
      float sum_b2 = 0;
      for(int i=0;i<a.length;++i) {
        float aa = Float.isNaN(a[i])?0:a[i];
        float bb = Float.isNaN(b[i])?0:b[i];
        sum_ab += aa*bb;
        sum_a2 += aa*aa;
        sum_b2 += bb*bb;
      }
      float sim = (float) (sum_ab / (Math.sqrt(sum_a2) * Math.sqrt(sum_b2) ));
      return 1-sim;
    }
  }

  private static class SimilarWord implements Comparable<SimilarWord> {
    BufferedBytes _word;
    float _dist; // cosine similarity
    SimilarWord(BufferedBytes bb, float distance) { _word=bb; _dist=distance; }
    @Override public int compareTo(SimilarWord o) { return Double.compare(o._dist,_dist); } // reverse sort
  }
}
