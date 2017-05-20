package embeddings;

import org.junit.Test;

public class SimWords {
  @Test public void testTimeGlove() {
    WordEmbeddings.EMBEDDINGS glove = WordEmbeddings.EMBEDDINGS.GLOVE;
    glove.mostSimilar("hello", 10);
  }
}
