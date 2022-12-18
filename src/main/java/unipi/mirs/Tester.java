package unipi.mirs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import unipi.mirs.utilities.VariableByteEncoder;

public class Tester {
  public static void main(String[] args) throws IOException {
    int[] mylist = new int[] { 1321, 42, 4125, 12123, 41 };
    ByteBuffer bb = ByteBuffer.allocate(mylist.length * Integer.BYTES);
    for (int i = 0; i < mylist.length; i++) {
      bb.putInt(mylist[i]);
    }
    IntBuffer ib = ByteBuffer.wrap(bb.array()).asIntBuffer();
    ByteBuffer compressed = VariableByteEncoder.encodeList(ib);
    while (compressed.position() < compressed.capacity()) {
      int nextint = VariableByteEncoder.decodeInt(compressed);
      System.out.println(nextint);
    }
  }
}
