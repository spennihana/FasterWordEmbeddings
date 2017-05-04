package embeddings;

import java.util.Arrays;

/**
 * Simple wrapper class for a byte[].
 *
 * This class has more general applications than for holding word embeddings, but that is its
 * primary use here.
 *
 * Class is not thread-safe for mutations and instances should be treated as final.
 * Fields are provided for performances reasons to eliminate unnecessary getters which
 * carry significant penalty when invoked from hot inner loops millions/billions of times.
 */
public class BufferedBytes implements Comparable<BufferedBytes> {
  byte [] _buf;
  int _off;
  int _len;

  public BufferedBytes(byte[] buf, int off, int len) {
    assert len >= 0 : "Length should be >= 0 " + len;
    _buf = buf;
    _off = off;
    _len = len;
  }

  public BufferedBytes() { }
  public BufferedBytes(byte[] buf) { this(buf,0,buf.length); }
  BufferedBytes(BufferedBytes from) { this(Arrays.copyOfRange(from._buf,from._off,from._off+from._len)); }

  @Override public int compareTo( BufferedBytes bb ) {
    int len = Math.min(_len,bb._len);
    for( int i=0; i<len; i++ ) {
      int x = (_buf[_off+i] & 0xFF) - (bb._buf[bb._off+i] & 0xFF);
      if( x != 0 ) return x;
    }
    return _len - bb._len;
  }

  @Override public int hashCode(){
    int hash = 0;
    int n = _off + _len;
    for (int i = _off; i < n; ++i)
      hash = 31 * hash + (char)_buf[i];
    return hash;
  }

  @Override public boolean equals(Object o){
    if( !(o instanceof BufferedBytes) ) return false;
    BufferedBytes bb = (BufferedBytes)o;
    if (bb._len != _len) return false;
    for (int i = 0; i < _len; ++i)
      if (_buf[_off + i] != bb._buf[bb._off + i]) return false;
    return true;
  }
}