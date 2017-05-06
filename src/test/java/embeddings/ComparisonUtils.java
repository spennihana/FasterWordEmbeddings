package embeddings;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;

import static embeddings.EmbeddingsParser.NBYTES;

/**
 * Used by comparison tests
 */
public class ComparisonUtils {
  static final double EPS=1e-6;  // error threshold

  /**
   * Read next line of bytes (cannot use BufferedReader.readLine() since it will do a lossy conversion to a String!)
   *
   * @param br BufferedInputStream supports mark and reset
   * @return the next line in bytes
   * @throws IOException
   */
  static byte[] readNextLine(BufferedInputStream br) throws IOException {
    br.mark(1<<20);  // ridiculously large read limit
    int nbytes=0;
    byte b;
    while( (b= (byte)br.read())!=-1 && b!='\r' && b!= '\n' ) nbytes++;
    if( nbytes==0 ) return null;
    br.reset();
    byte[] lineBytes = new byte[nbytes+1]; // always read the LF
    br.read(lineBytes);
    return lineBytes;
  }

  static void get(HashMap<BufferedBytes, BufferedBytes> map, BufferedBytes b, float[] res, float scale, int shift, int vec_sz) {
    BufferedBytes bb = map.get(b);
    int off = bb._off + bb._len;
    byte[] buf = bb._buf;
    int idx=0;
    int i=off;
    for(;i<off+NBYTES*vec_sz;i+=NBYTES) {
      // decode the embedding value by combining 3 bytes, then shift & scale
      int r =
          (buf[i  ] & 0xFF)      |
          (buf[i+1] & 0xFF) << 8 |
          (buf[i+2] & 0xFF) << 16;
      res[idx++] = ((r + shift)*scale);
    }
  }

  static abstract class Embedding {
    BufferedBytes _word;
    float[] _vec;
    static final int[] c2i = new int[128];
    static {
      c2i['0'] = 0;
      c2i['1'] = 1;
      c2i['2'] = 2;
      c2i['3'] = 3;
      c2i['4'] = 4;
      c2i['5'] = 5;
      c2i['6'] = 6;
      c2i['7'] = 7;
      c2i['8'] = 8;
      c2i['9'] = 9;
    }
    static Embedding fromBytes(byte[] lineBytes) {
      throw new RuntimeException("unimpl");
    }
  }
}
