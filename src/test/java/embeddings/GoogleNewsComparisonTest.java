package embeddings;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import static embeddings.ComparisonUtils.EPS;
import static embeddings.ComparisonUtils.get;
import static embeddings.ComparisonUtils.readNextLine;

/**
 * Compare for correctness
 */
public class GoogleNewsComparisonTest {

  @Test public void cmpGoogl() {
    EmbeddingsParser ep = EmbeddingsParser.parse("./data/googl.bin");
    HashMap<BufferedBytes, BufferedBytes> map = new HashMap<>();
    for(HashMap m: ep._maps) map.putAll(m);
    float scale = 1.f/(float)Math.pow(10,ep._scale);
    float[] res = new float[ep._vec_sz];

    double maxErr=-Double.MAX_VALUE;
    try(BufferedInputStream fs = new BufferedInputStream(new FileInputStream(new File("./data/GoogleNews-vectors-negative300.csv")))) {
      byte[] lineBytes;
      int line_idx=0;
      int i=0;
      GoogleEmbedding e=null;
      try {
        while( (lineBytes=readNextLine(fs))!= null ) {
          e = GoogleEmbedding.fromBytes(lineBytes);
          get(map, e._word, res, scale, ep._shift, ep._vec_sz);
          for (i=0; i < res.length; ++i) {
            double err = Math.abs(res[i] - e._vec[i]);
            maxErr = Math.max(maxErr, err);
            if (err > EPS) {
              throw new RuntimeException("Too much error");
            }
          }
          Arrays.fill(res, 0);
          line_idx++;
        }
      } catch(Exception ex) {
        System.out.println("word: " + e._word);
        System.out.println("actual: " + Arrays.toString(e._vec));
        System.out.println("comprs: " + Arrays.toString(res));
        System.out.println("index: " + i);
        System.out.println("line_index: " + line_idx);
        throw new RuntimeException(ex);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.out.println("Max err: " + maxErr);
  }

  private static class GoogleEmbedding extends ComparisonUtils.Embedding {

    // nasty looking line parser! This one is specific to Google 8GB csv
    static GoogleEmbedding fromBytes(byte[] lineBytes) {
      GoogleEmbedding e = new GoogleEmbedding();
      // capture the line
      int em_idx=299;
      float[] em = e._vec = new float[300];
      int i = lineBytes.length-1;
      i--; // skip LF
      i--; // skip a \b
      i--; // skip a \b
      int x;
      while( --i>=0 ) {  // decrement i to skip the whitespace
        x=i;
        while( i>=0 && lineBytes[i]!=' ' ) i--;
        int fidx=0;
        if( em_idx>= 0 ) {
          double d=0;
          // parsing each byte into a digit
          double di=10; // dividing by powers of 10 according to digit index
          boolean pos=true; // positive/negative
          int ii=i+1;
          if( lineBytes[ii]=='-' ) {
            pos=false;
            ii++;
          }
          // numbers are 0.xxxx
          // parse the 0 and assert
          assert lineBytes[ii]=='0' : Arrays.toString(Arrays.copyOfRange(lineBytes, i + 1, x)) + "; full bits: " + new String(lineBytes);
          ii++;
          // parse the . and assert
          assert lineBytes[ii]=='.' : Arrays.toString(Arrays.copyOfRange(lineBytes, i + 1, x)) + "; full bits: " + new String(lineBytes);
          ii++;
          // parse the remaining digits in the string
          for(;ii<=x;++ii) {
            d += c2i[lineBytes[ii]] / di;
            di *= 10.;
          }
          if( !pos ) d=-d;
          em[em_idx--]=(float)d;
        } else {
          // parsing the word
          byte[] field = new byte[x-i];
          for(int ii=i+1;ii<=x;++ii)
            field[fidx++] = lineBytes[ii];
          e._word = new BufferedBytes(field);
        }
      }
      assert em_idx <= 0;
      assert i<0;
      return e;
    }
  }
}
