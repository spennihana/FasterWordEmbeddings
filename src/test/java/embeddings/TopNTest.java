package embeddings;


import org.junit.Test;

import java.util.Comparator;
import java.util.PriorityQueue;

public class TopNTest {


  @Test public void test1() {
    int[] a = new int[]{1000,900,8,7,3,2,1,90000};
    int n=5;
    PriorityQueue<Integer> q = new PriorityQueue<>(n, new Comparator<Integer>() {
      @Override public int compare(Integer o1, Integer o2) {
        return o2-o1;
      }
    });

    for(int i: a) {
      if( q.size() < n ) q.add(i);
      else {
        if( i < q.peek() ) {
          q.poll();
          q.add(i);
        }
      }
    }

    while(!q.isEmpty()) {
      System.out.println(q.poll());
    }
  }
}
