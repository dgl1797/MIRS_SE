package unipi.mirs.utilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

public class VariableByteEncoder {

  /**
   * encodes a single integer using VBE
   * 
   * @param n the integer to be encoded
   * @return a ByteBuffer representing the Variable Byte representation of the integer
   */
  public static ByteBuffer encode(int n) {
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

  /**
   * encodes an entire IntBuffer into the ByteBuffer representing the VBE of the list
   * 
   * @param list the integer buffer list to be encoded
   * @return the byte representation of the intbuffer
   */
  public static ByteBuffer encodeList(IntBuffer list) {
    ArrayList<ByteBuffer> bstream = new ArrayList<>();

    // save bytebuffers keeping track of the total number of bytes to be saved
    int nbytes = 0;

    // loops over the entire intbuffer
    while (list.position() < list.capacity()) {
      int n = list.get();
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

  /**
   * Decodes a VBE representation of an integer from the passed ByteBuffer advancing its position
   * 
   * @param bb the bytebuffer from which to take the next integer to be decoded
   * @return the decoded integer
   */
  static public int decodeInt(ByteBuffer bb) {
    int n = 0;

    // advances the bytebuffer if it can until it finds the stopping bit at the start of the stream (1)
    while (bb.position() < bb.capacity()) {
      int currentByte = Byte.toUnsignedInt(bb.get());
      if ((currentByte >> 7) == 0) {
        // brings the actual 7 bits string on the left (*128) and adds the current byte to the right
        n = (n << 7) + currentByte;
      } else {
        // brings the actual 7 bits string on the left (*128) unsetting the first bit
        n = (n << 7) + (currentByte & 127);
        return n;
      }
    }
    return -1; // endof stream
  }

  /**
   * helper function advancing the bytebuffer to the next skip counting the number of bytes in between
   * 
   * @param bb       the bytebuffer to be advanced
   * @param skipstep the step at which to find the next skip
   * @return the number of bytes counted between each skip
   */
  public static int advance(ByteBuffer bb, int skipstep) {
    int counter = 0;
    int nbytes = 0;
    while (counter < skipstep) {
      while ((bb.get() & 128) == 0 && bb.position() < bb.capacity())
        nbytes++;
      nbytes++;
      if (bb.position() >= bb.capacity())
        return nbytes;
      counter += 1;
    }
    return nbytes;
  }
}
