package unipi.mirs.utilities;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public final class UtilityFunctions {
  private UtilityFunctions() {};

  /**
   * converts an array of nInts integers encoded inside byte[] b to the corresponding integer values
   * 
   * @param b     the byte[] representation of the nInts integers
   * @param nInts the number of integers contained in the bytes array b
   * @return the integers representation of the byte array b
   */
  public int[] BytesToInts(byte[] b, int nInts) {
    int[] ints = new int[nInts];
    for (int i = 0; i < nInts; i++) {
      ints[i] = ByteBuffer.wrap(b, i * Integer.BYTES, Integer.BYTES).getInt();
    }
    return ints;
  }

  /**
   * converts a binary representation of a posting list to its integer[] representation where Integer[0] is the docid
   * and Integer[1] the term frequency
   * 
   * @param b        the byte representation of the posting list
   * @param plLength the length of the posting list
   * @return the ArrayList<Integer[]> representation of the posting list
   */
  public ArrayList<Integer[]> binaryPLtoIntegerArray(byte[] b, int plLength) {
    ArrayList<Integer[]> list = new ArrayList<>();
    for (int i = 0; i < plLength * 2; i += 2) {
      int docid = ByteBuffer.wrap(b, i * Integer.BYTES, Integer.BYTES).getInt();
      int tf = ByteBuffer.wrap(b, (i + 1) * Integer.BYTES, Integer.BYTES).getInt();
      list.add(new Integer[] { docid, tf });
    }
    return list;
  }

  /**
   * Converts an array of integers into a byte[] representation
   * 
   * @param i the integers array to be serialized
   * @return the serialized byte[] array of the integers array
   */
  public byte[] IntsToBytes(int[] i) {
    ByteBuffer bb = ByteBuffer.allocate(i.length * Integer.BYTES);
    for (int j = 0; j < i.length; j++) {
      bb = bb.putInt(i[j]);
    }
    return bb.array();
  }
}
