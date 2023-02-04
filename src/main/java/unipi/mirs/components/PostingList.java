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
  private IntBuffer didlist = null;
  private IntBuffer frqlist = null;
  private int occurrences = 0;

  // PUBLIC DATA
  public int totalLength = 0;
  public double upperBound = 0;
  public String term = "";

  private PostingList() {}

  // GETTERS
  public int getDIDPointer() {
    return this.didlist.position();
  }

  public int getFRQPointer() {
    return this.frqlist.position();
  }

  public int getDocID() {
    return this.didlist.get(this.didlist.position());
  }

  public int getFreq() {
    return this.frqlist.get(this.frqlist.position());
  }

  public IntBuffer getDIDBuffer() {
    return this.didlist;
  }

  public IntBuffer getFRQBuffer() {
    return this.frqlist;
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
    this.didlist = null;
    this.frqlist = null;
  }

  /**
   * Generates a posting list from an intbuffer
   * 
   * @param ib the intbuffer
   * @return the posting list instance created
   * @throws IOException
   */
  public static PostingList from(IntBuffer didb, IntBuffer frqb) throws IOException {
    PostingList mypostinglist = new PostingList();
    mypostinglist.didlist = IntBuffer.wrap(didb.array());
    mypostinglist.frqlist = IntBuffer.wrap(frqb.array());
    if (mypostinglist.didlist.capacity() != mypostinglist.frqlist.capacity())
      throw new IOException("Capacities missmatching");
    mypostinglist.totalLength = mypostinglist.didlist.capacity();
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
  public static PostingList openList(String term, long dstartPosition, long fstartPosition, int plLength,
      boolean stopnostem) throws IOException {

    // INITIALIZE BASIC PARAMETERS OF THE INSTANCE
    PostingList postinglist = new PostingList();
    postinglist.occurrences = 1;
    postinglist.term = term;

    // TAKE THE POSTING LIST FROM THE CORRECT FILE
    int bytelength = plLength * Integer.BYTES;
    byte[] dl = new byte[bytelength];
    byte[] fl = new byte[bytelength];

    // SET THE FILE PATHS
    String OUTPUT_LOCATION = stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();
    Path didPath = Paths.get(OUTPUT_LOCATION, "docids.dat");
    Path frqPath = Paths.get(OUTPUT_LOCATION, "frequencies.dat");

    FileInputStream didr = null;
    FileInputStream frqr = null;

    // READ THE STREAM
    try {
      didr = new FileInputStream(didPath.toString());
      frqr = new FileInputStream(frqPath.toString());

      // skip to the start position
      didr.skip(dstartPosition);
      frqr.skip(fstartPosition);

      // read the upperbound
      postinglist.upperBound = ByteBuffer.wrap(didr.readNBytes(Double.BYTES)).asDoubleBuffer().get();

      // read the docids list
      didr.read(dl);
      postinglist.didlist = ByteBuffer.wrap(dl).asIntBuffer();
      postinglist.totalLength = plLength;

      // read the frequencies list
      frqr.read(fl);
      postinglist.frqlist = ByteBuffer.wrap(fl).asIntBuffer();
    } catch (IOException e) {
      ConsoleUX.ErrorLog("OpenList function error, cannot open file " + didPath.toString() + " or " + frqPath.toString()
          + ":\n" + e.getStackTrace().toString());
      postinglist = null;
    } finally {
      if (didr != null)
        didr.close();
      if (frqr != null)
        frqr.close();
    }
    // return the posting list instance
    return postinglist;
  }

  /**
   * advances the iterator of the intbuffer representing the postinglist
   * 
   * @return true if a next element exists, false otherwise
   */
  public boolean next() {
    try {
      this.didlist.get();
      this.frqlist.get();
      if (this.didlist.position() >= (this.totalLength))
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
    if (this.didlist.get(this.didlist.position()) >= docid) {
      return true;
    }

    // if the posting list is over immediately returns false
    if ((this.didlist.position() + 1) >= this.didlist.capacity())
      return false;

    // checks the immediately next docid to not perform more iterations than a simple next would
    if (this.didlist.get(this.didlist.position() + 1) >= docid) {
      this.didlist.position(this.didlist.position() + 1);
      this.frqlist.position(this.didlist.position());
      return true;
    }

    // initializes binary search parameters
    int rightPosition = this.didlist.capacity();

    // checks if the last docid of the list is lower or equal to the argument
    if (this.didlist.get(rightPosition - 1) < docid) {
      return false;
    } else if (this.didlist.get(rightPosition - 1) == docid) {
      this.didlist.position(rightPosition - 1);
      this.frqlist.position(this.didlist.position());
      return true;
    }

    // initializes middle and left offsets for the binary search
    int leftPosition = this.didlist.position() + 1;
    int middlePosition = (~~((leftPosition + rightPosition) / 2));

    // binary search
    while (this.didlist.get(middlePosition) != docid) {
      // if the element should be between right and left but there is no element it means the nextGEQ is the right's docid
      if ((rightPosition - leftPosition) == 1) {
        if (rightPosition == this.didlist.capacity())
          return false;
        this.didlist.position(rightPosition);
        this.frqlist.position(this.didlist.position());
        return true;
      }

      // re-computes the middle of the list relatively to left and right
      int midVal = this.didlist.get(middlePosition);
      if (docid > midVal) {
        leftPosition = middlePosition;
      } else {
        rightPosition = middlePosition;
      }
      middlePosition = ~~((leftPosition + rightPosition) / 2);
    }

    // if the while ends it means the middle element is == docid so the iterator is placed on it
    this.didlist.position(middlePosition);
    this.frqlist.position(this.didlist.position());
    return true;
  }

  public boolean isover() {
    return this.didlist.position() >= this.didlist.capacity();
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
