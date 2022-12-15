package unipi.mirs;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import unipi.mirs.utilities.VariableByteEncoder;

public class Tester {
  public static void main(String[] args) {
    IntBuffer mylist = IntBuffer.wrap(new int[] { 315, 12, 414, 598177674 });
    ByteBuffer encodedList = VariableByteEncoder.encodeList(mylist);
    int n = 0;
    while ((n = VariableByteEncoder.decodeInt(encodedList)) != -1) {
      System.out.println(n);
    }
  }
}
