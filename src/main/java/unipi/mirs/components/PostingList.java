package unipi.mirs.components;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;

public class PostingList implements Comparable<PostingList> {
  private final int postingSize = 2;
  private IntBuffer postingList = null;
  private int occurrences = 0;

  public int totalLength = 0;
  public double upperBound = 0;
  public String term = "";

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

  public IntBuffer getBuffer() {
    return this.postingList;
  }

  public void increaseOccurrences() {
    this.occurrences += 1;
  }

  public int occurrences() {
    return this.occurrences;
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

  public static PostingList from(IntBuffer ib) {
    PostingList mypostinglist = new PostingList();
    mypostinglist.postingList = IntBuffer.wrap(ib.array());
    mypostinglist.totalLength = ~~(mypostinglist.postingList.capacity() / 2);
    return mypostinglist;
  }

  public static PostingList openList(String term, long startPosition, int plLength, boolean stopnostem)
      throws IOException {
    PostingList postinglist = new PostingList();
    postinglist.occurrences = 1;
    postinglist.term = term;

    // TAKE THE POSTING LIST FROM THE CORRECT FILE
    int bytelength = plLength * 2 * Integer.BYTES;
    byte[] pl = new byte[bytelength];
    String invertedIndexStr = "inverted_index.dat";
    String OUTPUT_LOCATION = stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();
    Path invertedIndexPath = Paths.get(OUTPUT_LOCATION, invertedIndexStr);

    // READ THE STREAM
    try (FileInputStream fileInvInd = new FileInputStream(invertedIndexPath.toString())) {

      // skip to the start position
      fileInvInd.skip(startPosition);

      // read the upperbound
      postinglist.upperBound = ByteBuffer.wrap(fileInvInd.readNBytes(Double.BYTES)).asDoubleBuffer().get();

      // read the postinglist
      fileInvInd.read(pl);
      postinglist.postingList = ByteBuffer.wrap(pl).asIntBuffer();
      postinglist.totalLength = plLength;

      //garbage collector, dovrebbe?
      invertedIndexPath = null;
      invertedIndexStr = null;

      // return the posting list instance
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
    int middlePosition = (~~((leftPosition + rightPosition) / 2));
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
      middlePosition = ~~((leftPosition + rightPosition) / 2);
      middleOffset = middlePosition * 2;
    }

    this.postingList.position(middleOffset);
    return true;
  }

  public boolean isover() {
    return this.postingList.position() >= this.postingList.capacity();
  }

  public double score(int ndocs, int doclen, double avdl) {
    int tf = getFreq();
    return occurrences * ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / avdl)) + tf)
        * Math.log10((double) ndocs / (double) this.totalLength));
  }

  public double tfidf(int ndocs) {
    int tf = getFreq();
    return occurrences * (1 + (Math.log10(tf))) * (Math.log10((double) ndocs / (double) this.totalLength));
  }

  /**
   * DEBUG
   */

  @Override
  public int compareTo(PostingList p2) {
    return Double.compare(this.upperBound, p2.upperBound);
  }
}
