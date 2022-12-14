package unipi.mirs;

import java.nio.ByteBuffer;

import unipi.mirs.utilities.VariableByteEncoder;

public class Tester {
  public static void main(String[] args) {
    int[] mylist = { 315 };
    ByteBuffer encodedList = VariableByteEncoder.encodeList(mylist);
    int n = 0;
    while ((n = VariableByteEncoder.decodeInt(encodedList)) != -1) {
      System.out.println(n);
    }
  }
}
