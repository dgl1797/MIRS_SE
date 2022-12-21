package unipi.mirs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import unipi.mirs.components.CompressedPostingList;
import unipi.mirs.components.PostingList;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.VariableByteEncoder;

public class Tester {
  public static void main(String[] args) throws IOException {
    int[] mylist = new int[] { 2, 239, 18, 239, 33, 239, 57, 239, 74, 239, 106, 239, 136, 239, 193, 239, 213, 239, 331,
        239, 341, 239, 348, 239, 357, 239, 409, 239, 409, 239, 424, 239, 440, 239, 498, 239, 510, 239, 575, 239, 642,
        239, 653, 239, 682, 239, 691, 239, 701, 239, 708, 239, 803, 239, 877, 239, 879, 239, 894, 239, 508140, 239,
        662257, 239, 804494, 239, 1440259, 239, 1725487, 239, 1770856, 239, 1838081, 239, 1887398, 239, 2319127, 239,
        2436738, 239, 2846627, 239, 3557961, 239, 3753935, 239, 4066056, 239, 4366547, 239, 4479251, 239, 4981661, 239,
        5191841, 239, 5741683, 239, 6752014, 239, 6840492, 239, 7114702, 239, 7166634, 239, 7222577, 239, 7350943, 239,
        7656495, 239, 7782949, 239, 7845699, 239, 8415963, 239, 8551450, 239 };
    CompressedPostingList cpl = CompressedPostingList.from(PostingList.from(IntBuffer.wrap(mylist)));
    // print della cpl:
    int n;
    ByteBuffer iterator = ByteBuffer.wrap(cpl.getBuffer().array());
    boolean incrementStep = false;
    int counter = 0;
    while ((n = VariableByteEncoder.decodeInt(iterator)) != -1) {
      if (counter == 0) {
        ConsoleUX.ErrorLog("" + n + " ", "");
        counter = 1;
      } else {
        ConsoleUX.SuccessLog("" + n + " ", "");
        if (incrementStep)
          counter = (counter + 1) % (cpl.getStep() + 1);
        incrementStep = !incrementStep;
      }
    }
    System.out.println("\nSKIPSTEP: " + cpl.getStep());
    cpl.next();
    cpl.nextGEQ(12);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(14);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(174);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(6);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(129381284);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(1123);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(4132);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(4132);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(4132);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(9);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(145145);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(284576);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(28389);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(112345);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(22234);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(23232);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(23232);
    System.out.println(cpl.getDocID());
    cpl.nextGEQ(8551450);
    System.out.println(cpl.getDocID());
  }
}
