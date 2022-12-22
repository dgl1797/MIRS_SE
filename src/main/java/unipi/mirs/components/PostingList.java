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

  // PRIVATE DATA
  private final int postingSize = 2;
  private IntBuffer postingList = null;
  private int occurrences = 0;

  // PUBLIC DATA
  public int totalLength = 0;
  public double upperBound = 0;
  public String term = "";

  private PostingList() {}

  // GETTERS
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

  public int occurrences() {
    return this.occurrences;
  }

  /**
   * function to increase the number of occurrences of the term that the posting list is representing
   */
  public void increaseOccurrences() {
    this.occurrences += 1;
  }

  public void close() {
    this.postingList = null;
  }

  /**
   * Generates a posting list from an intbuffer
   * 
   * @param ib the intbuffer
   * @return the posting list instance created
   */
  public static PostingList from(IntBuffer ib) {
    PostingList mypostinglist = new PostingList();
    mypostinglist.postingList = IntBuffer.wrap(ib.array());
    mypostinglist.totalLength = ~~(mypostinglist.postingList.capacity() / 2);
    return mypostinglist;
  }

  /**
   * Creates a Posting list instance by reading the inverted index bytes for the term
   * 
   * @param term          the term to which the posting list refers to
   * @param startPosition the starting byte in the inverted index
   * @param plLength      the number of postings
   * @param stopnostem    whether or not to read the posting list from the filtered index
   * @return the Posting list instance
   * @throws IOException
   */
  public static PostingList openList(String term, long startPosition, int plLength, boolean stopnostem)
      throws IOException {

    // INITIALIZE BASIC PARAMETERS OF THE INSTANCE
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

      // return the posting list instance
      return postinglist;
    } catch (IOException e) {
      ConsoleUX.ErrorLog("OpenList function error, cannot open file " + invertedIndexPath.toString() + ":\n"
          + e.getStackTrace().toString());
      return null;
    }
  }

  /**
   * advances the iterator of the intbuffer representing the postinglist
   * 
   * @return true if a next element exists, false otherwise
   */
  public boolean next() {
    try {
      this.postingList.position(this.postingList.position() + postingSize);
      if (this.postingList.position() >= (this.totalLength * 2))
        return false;
      return true;
    } catch (IndexOutOfBoundsException e) {
      ConsoleUX.ErrorLog("Error during next() function, array out of bound:\n" + e.getMessage().toString());
    }
    return false;
  }

  /**
   * finds the next Greater or EQual docid relatively to the passed argument by binary searching over the remaining
   * portion of the posting list
   * 
   * @param docid the docid on which to perform nextGEQ
   * @return
   */
  public boolean nextGEQ(int docid) {

    // if the posting list is already placed on a GEQ docid returns without changing list's iterator
    if (this.postingList.get(this.postingList.position()) >= docid) {
      return true;
    }

    // if the posting list is over immediately returns false
    if ((this.postingList.position() + postingSize) >= this.postingList.capacity())
      return false;

    // checks the immediately next docid to not perform more iterations than a simple next would
    if (this.postingList.get(this.postingList.position() + postingSize) >= docid) {
      this.postingList.position(this.postingList.position() + postingSize);
      return true;
    }

    // initializes binary search parameters
    int rightOffset = this.postingList.capacity();
    int rightPosition = ((int) rightOffset / 2);

    // checks if the last docid of the list is lower or equal to the argument
    if (this.postingList.get(rightOffset - postingSize) < docid) {
      return false;
    } else if (this.postingList.get(rightOffset - postingSize) == docid) {
      this.postingList.position(rightOffset - postingSize);
      return true;
    }

    // initializes middle and left offsets for the binary search
    int leftOffset = this.postingList.position() + postingSize;
    int leftPosition = ((int) (leftOffset) / 2);
    int middlePosition = (~~((leftPosition + rightPosition) / 2));
    int middleOffset = middlePosition * 2;

    // binary search
    while (this.postingList.get(middleOffset) != docid) {
      // if the element should be between right and left but there is no element it means the nextGEQ is the right's docid
      if ((rightPosition - leftPosition) == 1) {
        if (rightOffset == this.postingList.capacity())
          return false;
        this.postingList.position(rightOffset);
        return true;
      }

      // re-computes the middle of the list relatively to left and right
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

    // if the while ends it means the middle element is == docid so the iterator is placed on it
    this.postingList.position(middleOffset);
    return true;
  }

  public boolean isover() {
    return this.postingList.position() >= this.postingList.capacity();
  }

  /**
   * BM25 Scoring implementation
   * 
   * @param ndocs  collection size
   * @param doclen document's length
   * @param avdl   average document length in the collection
   * @return the computed BM25 score
   */
  public double score(int ndocs, int doclen, double avdl) {
    int tf = getFreq();
    return occurrences * ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / avdl)) + tf)
        * Math.log10((double) ndocs / (double) this.totalLength));
  }

  /**
   * TFIDF Scoring implementation
   * 
   * @param ndocs collection size
   * @return the computed TFIDF score
   */
  public double tfidf(int ndocs) {
    int tf = getFreq();
    return occurrences * (1 + (Math.log10(tf))) * (Math.log10((double) ndocs / (double) this.totalLength));
  }

  @Override
  public int compareTo(PostingList p2) {
    return Double.compare(this.upperBound, p2.upperBound);
  }
}
