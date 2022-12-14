package unipi.mirs.utilities;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class VariableByteEncoder {

  private static ByteBuffer encode(int n) {
    ArrayList<Integer> bytes = new ArrayList<>();

    // keep looping until there is a n != 0 to be encoded and save the 7 bits representation in the array's head
    do {
      bytes.add(0, n % 128);
      n >>= 7;
    } while (n != 0);

    // add a 1 bit in the head of the last byte to state the stream is over
    bytes.set(bytes.size() - 1, bytes.get(bytes.size() - 1) | 128);

    // allocate the bytebuffer to be returned
    ByteBuffer bb = ByteBuffer.allocate(bytes.size());
    for (int x : bytes) {
      bb.put(ByteBuffer.allocate(4).putInt(x).position(3).get());
    }
    return ByteBuffer.wrap(bb.array());
  }

  public static ByteBuffer encodeList(int... list) {
    ArrayList<ByteBuffer> bstream = new ArrayList<>();

    // save bytebuffers keeping track of the total number of bytes to be saved
    int nbytes = 0;
    for (int n : list) {
      ByteBuffer tmp = ByteBuffer.wrap(encode(n).array());
      nbytes += tmp.capacity();
      bstream.add(tmp);
    }

    // allocate a bytebuffer containing the entire compressed list
    ByteBuffer result = ByteBuffer.allocate(nbytes);
    for (ByteBuffer bb : bstream) {
      result.put(bb);
    }
    return ByteBuffer.wrap(result.array());
  }

  static public int decodeInt(ByteBuffer bb) {
    int n = 0;

    // advances the bytebuffer if it can until it finds the stopping bit at the start of the stream (1)
    while (bb.position() < bb.capacity()) {
      int currentByte = Byte.toUnsignedInt(bb.get());
      if ((currentByte >> 7) == 0) {
        n = n * 128 + currentByte;
      } else {
        n = 128 * n + (currentByte - 128);
        return n;
      }
    }
    return -1; // endof stream
  }
}
