package embeddings;

import org.junit.Test;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;

import static embeddings.ComparisonUtils.EPS;
import static embeddings.ComparisonUtils.get;
import static embeddings.ComparisonUtils.readNextLine;

/**
 * Compare for correctness
 */
public class GloVeComparisonTest {

  @Test public void cmpGlove() {
    EmbeddingsParser ep = EmbeddingsParser.parse("./data/glove.bin");
    HashMap<BufferedBytes, BufferedBytes> map = new HashMap<>();
    for(HashMap m: ep._maps) map.putAll(m);
    float scale = 1.f/(float)Math.pow(10,ep._scale);
    float[] res = new float[ep._vec_sz];

    double maxErr=-Double.MAX_VALUE;
    long start = System.currentTimeMillis();
    try(BufferedInputStream bis = new BufferedInputStream(new FileInputStream(new File("./data/glove.840B.300d.txt")))) {
      byte[] lineBytes;
      int line_idx=0;
      while( (lineBytes=readNextLine(bis))!= null ) {

        // Same note from blog post: https://spenai.org/bravepineapple/faster_em#aside:
        // In exploring these embeddings I stumbled into an issue of a duplicate
        // pair in the raw embeddings. Namely lines 138,701 and 140,649 (1-indexed absolute file lines here)
        // are the same unicode replacement character repeated 334 times. That is, in the file a sequence of 334
        // triplets of bytes -17 -65 -67 or 0xEF 0xBF 0xBD appears. There's another instance of 335 triplets at
        // line 141,505, but that sequence occurs a single time.
        //
        // Unfortunately, the duplicate words don't have duplicate embeddings. Since it's unlikely that these
        // vectors are even useful, and for the sake of keeping one's sanity I've excluded the instance at 138,701
        // from comparisons in my unit tests (but it still exists in the compressed embeddings for better or worse).
        if( line_idx==138700 ) {
          line_idx++;
          continue;
        }
        GloveEmbedding e = GloveEmbedding.fromBytes(lineBytes);
        get(map,e._word,res,scale,ep._shift,ep._vec_sz);
        for(int i=0;i<res.length;++i) {
          double err = Math.abs(res[i] - e._vec[i]);
          maxErr = Math.max(maxErr,err);
          if( err > EPS ) {
            System.out.println("word: " + e._word);
            System.out.println("actual: " + Arrays.toString(e._vec));
            System.out.println("comprs: " + Arrays.toString(res));
            System.out.println("index: " + i);
            System.out.println("line_index: " + line_idx);
            throw new RuntimeException("Too much error");
          }
        }
        Arrays.fill(res,0);
        line_idx++;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("Elapsed: " + (System.currentTimeMillis() - start)/1000. + " seconds");
    System.out.println("Max err: " + maxErr);
  }

  private static class GloveEmbedding  extends ComparisonUtils.Embedding {

    // nasty looking line parser!
    static GloveEmbedding fromBytes(byte[] lineBytes) {
      GloveEmbedding e = new GloveEmbedding();
      // parse out the bytes from the back to the front
      int em_idx=299;
      float[] em = e._vec = new float[300];

      int i = lineBytes.length-1;
      if( lineBytes[i] == '\b' ) i--;
      if( lineBytes[i] == '\b' ) i--;
      int x;
      while( i>=0 ) {
        x=i;
        boolean sci=false; // scientific notation?
        while( i>=0 && lineBytes[i]!=' ' )
          sci |= lineBytes[i--]=='e';
        int fidx=0;
        if( em_idx>=0 ) {
          if( sci ) {
            em[em_idx--] = Float.parseFloat(new String(Arrays.copyOfRange(lineBytes,i+1,x+1)));
          } else {
            // parse each byte into a digit
            double d = 0;
            double di = 10; // divide by powers of 10 according to digit index
            boolean pos=true; // positive/negative
            int ii=i+1;
            if( lineBytes[ii] == '-' ) {
              pos=false;
              ii++;
            }
            d=c2i[lineBytes[ii++]];
            if( ii < x ) {
              assert lineBytes[ii] == '.' : new String(Arrays.copyOfRange(lineBytes,i+1,x)) + "; em_idx=" + em_idx + "; full bits: " + new String(lineBytes);
              ii++;
              for(; ii<=x;++ii) {
                d += c2i[lineBytes[ii]] / di;
                di*=10;
              }
            }
            if( !pos ) d=-d;
            em[em_idx--] = (float)d;
          }
        } else {
          //parse the word
          byte[] field = new byte[x-i];
          for(int ii=i+1;ii<=x;++ii)
            field[fidx++] = lineBytes[ii];
          e._word = new BufferedBytes(field);
        }
        i--;
      }
      assert em_idx <= 0;
      assert i<0;
      return e;
    }
  }
}
