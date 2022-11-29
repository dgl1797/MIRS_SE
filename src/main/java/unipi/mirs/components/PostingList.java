package unipi.mirs.components;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;

public class PostingList {

  private final int postingSize = 2;
  private IntBuffer postingList = null;
  public int totalLength = 0;

  private PostingList() {}

  public int getPointer() {
    return this.postingList.position();
  }

  public int getDocID() {
    return this.postingList.get(this.postingList.position());
  }

  public int getFreq() {
    return this.postingList.get(this.postingList.position() + 1);
  }

  public boolean next() {
    try {

      this.postingList.position(this.postingList.position() + postingSize);//Arrays.copyOfRange(this.postingList, this.pointer, this.pointer + (Integer.BYTES*2));
      return true;
    } catch (IndexOutOfBoundsException e) {
      System.out.println("Error during next() function, array out of bound: " + e.getMessage());
    }
    return false;
  }

  public void close() {
    this.postingList = null;
  }

  public static PostingList openList(int startPosition, int plLength) throws IOException {
    PostingList postinglist = null;
    byte[] pl = new byte[plLength];
    String invertedIndexStr = String.format("inverted_index.dat");
    Path invertedIndexPath = Paths.get(Constants.OUTPUT_DIR.toString(), invertedIndexStr);
    try (FileInputStream fileInvInd = new FileInputStream(invertedIndexPath.toString())) {

      fileInvInd.read(pl, startPosition, plLength);

      postinglist = new PostingList();
      postinglist.postingList = ByteBuffer.wrap(pl).asIntBuffer();
      postinglist.totalLength = plLength;

      //garbage collector, dovrebbe?
      invertedIndexPath = null;
      invertedIndexStr = null;
      return postinglist;
    } catch (IOException e) {
      ConsoleUX.ErrorLog(
          "OpenList function error, cannot open file " + invertedIndexPath.toString() + "\n" + e.getMessage());
      return null;
    }
  }

  public boolean nextGEQ(int docid) {

    if (this.postingList.get(this.postingList.position()) >= docid) {
      return true;
    }

    if (this.postingList.get(this.postingList.position() + postingSize) >= docid) {
      this.postingList.position(this.postingList.position() + postingSize);
      return true;
    }

    int rightPosition = this.postingList.capacity();

    if (this.postingList.get(rightPosition - 1) < docid) {
      return false;
    }
    int leftPosition = this.postingList.position() + postingSize;
    int middlePosition = (int) Math.floor((leftPosition + rightPosition) / 2);

    while (this.postingList.get(middlePosition) != docid) {
      if ((rightPosition - leftPosition) == 1) {
        this.postingList.position(rightPosition);
        return true;
      }

      int midVal = this.postingList.get(middlePosition);
      if (docid > midVal) {
        leftPosition = middlePosition;
      } else {
        rightPosition = middlePosition;
      }
      middlePosition = (int) Math.floor((leftPosition + rightPosition) / 2);
    }

    this.postingList.position(middlePosition);
    return true;
  }

  public double score(int ndocs, int plLength, int noccurrences, int doclen, double avdl) {
    int tf = getFreq();
    return noccurrences * ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / avdl)) + tf)
        * Math.log10(ndocs / plLength));
  }
}
