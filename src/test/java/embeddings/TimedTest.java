package embeddings;

import org.junit.Test;

public class TimedTest {
  @Test public void testTimeGlove() {
    long start = System.currentTimeMillis();
    EmbeddingsParser.parse("./data/glove.bin");
    System.out.println( (System.currentTimeMillis() - start)/1000. + " seconds to read glove embeddings");
  }

  @Test public void testTimeGoogle() {
    long start = System.currentTimeMillis();
    EmbeddingsParser.parse("./data/googl.bin");
    System.out.println( (System.currentTimeMillis() - start)/1000. + " seconds to read glove embeddings");
  }
}
