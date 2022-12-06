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
  public Long totalLength = 0L;
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
      this.postingList.position(this.postingList.position() + postingSize);
      if (this.postingList.position() >= (this.totalLength * 2))
        return false;
      return true;
    } catch (IndexOutOfBoundsException e) {
      System.out.println("Error during next() function, array out of bound: " + e.getMessage());
    }
    return false;
  }

  public void close() {
    this.postingList = null;
  }

  public static PostingList openList(long startPosition, long plLength,boolean stopnostem) throws IOException {
    PostingList postinglist = null;
    int bytelength = (int)(plLength) * 2 * Integer.BYTES;
    byte[] pl = new byte[bytelength];
    String invertedIndexStr = String.format("inverted_index.dat");
    String OUTPUT_LOCATION = stopnostem ? Constants.STOPNOSTEM_OUTPUT_DIR.toString() : Constants.OUTPUT_DIR.toString();
    Path invertedIndexPath = Paths.get(OUTPUT_LOCATION, invertedIndexStr);
    try (FileInputStream fileInvInd = new FileInputStream(invertedIndexPath.toString())) {

      fileInvInd.skip(startPosition);
      fileInvInd.read(pl);

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

    if ((this.postingList.position() + postingSize) >= this.postingList.capacity())
      return false;

    if (this.postingList.get(this.postingList.position() + postingSize) >= docid) {
      this.postingList.position(this.postingList.position() + postingSize);
      return true;
    }

    int rightOffset = this.postingList.capacity();
    int rightPosition = ((int) rightOffset / 2);

    if (this.postingList.get(rightOffset - postingSize) < docid) {
      return false;
    }
    int leftOffset = this.postingList.position() + postingSize;
    int leftPosition = ((int) (leftOffset) / 2);
    int middlePosition = ((int) Math.floor((leftPosition + rightPosition) / 2));
    int middleOffset = middlePosition * 2;

    while (this.postingList.get(middleOffset) != docid) {
      if ((rightPosition - leftPosition) == 1) {
        if (rightOffset == this.postingList.capacity())
          return false;
        this.postingList.position(rightOffset);
        return true;
      }

      int midVal = this.postingList.get(middleOffset);
      if (docid > midVal) {
        leftPosition = middlePosition;
        leftOffset = middleOffset;
      } else {
        rightPosition = middlePosition;
        rightOffset = middleOffset;
      }
      middlePosition = (int) Math.floor((leftPosition + rightPosition) / 2);
      middleOffset = middlePosition * 2;
    }

    this.postingList.position(middleOffset);
    return true;
  }

  public boolean isover() {
    return this.postingList.position() >= this.postingList.capacity();
  }

  public double score(int ndocs, int noccurrences, int doclen, double avdl) {
    int tf = getFreq();
    return noccurrences * ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / avdl)) + tf)
        * Math.log10(ndocs / this.totalLength));
  }

  public double tfidf(int ndocs, int noccurences) {
    int tf = getFreq();
    return noccurences * (1 + (Math.log10(tf))) * (Math.log10((double)ndocs / (double)this.totalLength));
  }

}
