package unipi.mirs;

import java.io.IOException;
import java.nio.IntBuffer;

import unipi.mirs.components.CompressedPostingList;
import unipi.mirs.components.PostingList;
import unipi.mirs.utilities.VariableByteEncoder;

public class Tester {
  public static void main(String[] args) throws IOException {
    int[] mylist = new int[] { 1321, 42, 4125, 12123, 41, 124, 5, 124, 5, 124, 15, 124, 214, 25, 215, 14, 524, 51, 241,
        125, 3, 234, 312, 54, 12 };
    CompressedPostingList cpl = CompressedPostingList.from(PostingList.from(IntBuffer.wrap(mylist)));
    int n;
    System.out.println(cpl.getBuffer());
    while ((n = VariableByteEncoder.decodeInt(cpl.getBuffer())) != -1) {
      System.out.print(n + " ");
    }
  }
}
