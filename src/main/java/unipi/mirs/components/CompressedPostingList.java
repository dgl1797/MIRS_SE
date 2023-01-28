package unipi.mirs.components;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.VariableByteEncoder;

public class CompressedPostingList implements Comparable<CompressedPostingList> {

  // PRIVATE DATA
  private ByteBuffer postingList = null;
  private int skipstep;
  private int noccurrences = 0;
  private int DocID = 0;
  private int tf = 0;
  private int lastSkipPosition = 0;
  private int lastSkipOffset = 0;
  private int lastSkipLength = 0;
  private int resettableStep = 0;

  // PUBLIC DATA
  public String term = "";
  public int totalLength = 0;
  public double upperBound = 0;

  private CompressedPostingList() {}

  public boolean isover() {
    return this.postingList.position() >= this.postingList.capacity();
  }

  // GETTERS 
  public ByteBuffer getBuffer() {
    return this.postingList;
  }

  public int getDocID() {
    return DocID;
  }

  public int getStep() {
    return skipstep;
  }

  public int noccurrences() {
    return this.noccurrences;
  }

  /**
   * increases the number of occurrences that the posting list of this term represents
   */
  public void increaseOccurrences() {
    this.noccurrences += 1;
  }

  /**
   * decodes the next posting and places the cursor to the beginning of the next posting's position resetting at every
   * skipstep the skip data
   * 
   * @return true if the next elements exists, false if the buffer is over
   */
  public boolean next() {
    try {
      // immediately returns false if the posting list is over
      if (postingList.position() >= postingList.capacity())
        return false;

      // reads the skip data if the buffer is placed on a skip (one every skipstep)
      if (resettableStep == 0) {
        lastSkipPosition = this.postingList.position();
        lastSkipOffset = VariableByteEncoder.decodeInt(postingList);
        lastSkipLength = this.postingList.position() - lastSkipPosition;
      }
      // reads the posting's docid and term frequency
      DocID = VariableByteEncoder.decodeInt(postingList);
      tf = VariableByteEncoder.decodeInt(postingList);
      resettableStep = (resettableStep + 1) % skipstep;
      return true;
    } catch (IndexOutOfBoundsException iobe) {
      ConsoleUX.ErrorLog("Error during next() function, array out of bound:\n" + iobe.getMessage().toString());
      return false;
    }
  }

  /**
   * Performs a skip to get the next Greater or EQual element in the posting list relatively to docid
   * 
   * @param docid the docid to which perform the skip
   * @return true if there exists a GEQ docid, false otherwise
   */
  public boolean nextGEQ(int docid) {
    // immediately stops if the posting list is over
    if (this.postingList.position() >= this.postingList.capacity())
      return false;

    // returns true without replacing the pointer if the current docid already is >= docid
    if (this.DocID >= docid)
      return true;

    // LAST DOCID CHECK

    // checks the last docid of the posting list to verify the passed argument is inside the posting list 
    int lastPosition = this.postingList.capacity() - 1;
    boolean isNotDocid = true;
    // backwards from last position until the second 1 is caugth
    while ((this.postingList.get(lastPosition - 1) & 128) == 0 || isNotDocid) {
      lastPosition -= 1;
      if ((this.postingList.get(lastPosition) & 128) != 0)
        isNotDocid = false;
    }
    // controls the lastdocid decoding a temporary buffer to not alter the postinglist iterator
    ByteBuffer tmp = this.postingList.slice(lastPosition, this.postingList.capacity() - lastPosition);
    int lastDocID = VariableByteEncoder.decodeInt(tmp);
    if (lastDocID < docid)
      return false;
    // places the iterator on the last item if it is exactly equal to the argument
    if (lastDocID == docid) {
      this.postingList.position(lastPosition);
      this.resettableStep = 1;
      return next();
    }
    // END CHECK

    // CHECK ON NEXT ELEMENT
    // the idea is to not make more iteration than a normal next() if the nextGEQ is the immediately successive docid
    tmp = this.postingList.slice(this.postingList.position(),
        Math.min(32, this.postingList.capacity() - this.postingList.position()));
    if (resettableStep == 0) {
      VariableByteEncoder.decodeInt(tmp);
    }
    if (VariableByteEncoder.decodeInt(tmp) >= docid) {
      return next();
    }
    // END CHECK

    // SKIPPING SEARCH
    // init of the necessary parameters
    int newposition = this.lastSkipPosition;
    int oldskiplength = this.lastSkipLength;
    int oldskipoffset = this.lastSkipOffset;
    int oldskipposition = newposition;
    int reachedDocID = this.DocID;
    do {
      // resets the step if a skip there was another iteration before (newposition != lastskipposition)
      if (newposition != this.lastSkipPosition) {
        this.resettableStep = 0;
        this.postingList.position(newposition);
      }

      // places the newposition to the next skip
      newposition = oldskiplength + oldskipoffset + oldskipposition;
      if (newposition >= this.postingList.capacity()) {
        // LINEAR SEARCH OVER REMAINING DOCIDS
        while (next()) {
          if (this.DocID >= docid)
            return true;
        }
        return false;
        // END LINEAR SEARCH
      }

      // performs checks on a tmp slice of the posting list to not alter its iterator
      tmp = this.postingList.slice(newposition, Math.min(32, this.postingList.capacity() - newposition));
      oldskiplength = tmp.position();
      oldskipoffset = VariableByteEncoder.decodeInt(tmp);
      oldskiplength = tmp.position() - oldskiplength;
      reachedDocID = VariableByteEncoder.decodeInt(tmp);
      oldskipposition = newposition;
      // checks if the docid has been reached on the skip position
      if (reachedDocID == docid) {
        this.postingList.position(newposition);
        this.resettableStep = 0;
        return next();
      }
    } while (reachedDocID < docid);

    // linear searches between the < and the >= skips to find the nextGEQ
    while (next()) {
      if (this.DocID >= docid)
        return true;
    }
    // END SKIPPING SEARCH

    // not found
    return false;
  }

  /**
   * Creates a new instance of a compressed posting list by taking it from the inverted index file
   * 
   * @param term       the term to which the posting list refers to
   * @param startByte  the byte where the posting list of the term starts
   * @param endByte    the byte offset from the start byte where the posting list of the term ends
   * @param plLength   the number of postings in the posting list
   * @param stopnostem whether or not to take the posting list from the filtered index
   * @return the instance of the compressed posting list
   * @throws IOException
   */
  public static CompressedPostingList openList(String term, long startByte, int endByte, int plLength,
      boolean stopnostem) throws IOException {
    CompressedPostingList cpl = new CompressedPostingList();

    // INITS THE BASIC DATA FOR THE POSTING LIST
    cpl.term = term;
    cpl.totalLength = plLength;
    cpl.skipstep = (int) Math.ceil(Math.sqrt(plLength));
    cpl.noccurrences = 1;

    // FILE SELECTION BASED ON FILTER SET
    final File INV_IND_FILE = Paths
        .get(stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString(), "compressed_index",
            "inverted_index.dat")
        .toFile();

    try (FileInputStream fileInvInd = new FileInputStream(INV_IND_FILE)) {
      // skips to the startbyte
      fileInvInd.skip(startByte);
      // reads a double
      cpl.upperBound = ByteBuffer.wrap(fileInvInd.readNBytes(Double.BYTES)).asDoubleBuffer().get();
      // reads endbyte bytes
      byte[] pl = new byte[endByte];
      fileInvInd.read(pl);

      // instantiates the buffer information for the posting list
      cpl.postingList = ByteBuffer.wrap(pl);
      cpl.lastSkipPosition = 0;
      cpl.lastSkipOffset = VariableByteEncoder.decodeInt(cpl.postingList);
      cpl.lastSkipLength = cpl.postingList.position() - cpl.lastSkipPosition;
      cpl.DocID = VariableByteEncoder.decodeInt(cpl.postingList);
      cpl.tf = VariableByteEncoder.decodeInt(cpl.postingList);
      cpl.resettableStep = 1;
      return cpl;
    } catch (IOException ioe) {
      ConsoleUX.ErrorLog(
          "OpenList function error, cannot open file " + INV_IND_FILE.toString() + ":\n" + ioe.getMessage().toString());
      return null;
    }
  }

  /**
   * Creates a CompressedPostingList instance from a PostingList instance by compressing its intbuffer
   * 
   * @param pl the posting list instance to be compressed
   * @return the compressed posting list
   */
  public static CompressedPostingList from(PostingList pl) {

    // INITIALIZES COMPRESSION PARAMETERS
    int skipstep = (int) Math.ceil(Math.sqrt(pl.totalLength));
    ByteBuffer compressedList = VariableByteEncoder.encodeList(pl.getBuffer());
    ByteBuffer tmp = ByteBuffer.wrap(compressedList.array());
    ArrayList<ByteBuffer> chunks = new ArrayList<>();
    CompressedPostingList result = new CompressedPostingList();
    int totalBytes = 0;
    result.skipstep = skipstep;

    // INITIALIZES THE CHUNKS BETWEEN CONSEQUENT SKIPS AND PUTS THEM INTO THE ARRAY
    while (tmp.position() < tmp.capacity()) {
      int lastSkipOffset = VariableByteEncoder.advance(tmp, 2 * skipstep);

      // the skip is generated as an offset from the current position where to jump compressed using VBE
      ByteBuffer encodedOffset = VariableByteEncoder.encode(lastSkipOffset);
      ByteBuffer chunk = ByteBuffer.allocate(lastSkipOffset + encodedOffset.capacity());
      chunk.put(encodedOffset).put(compressedList.slice(compressedList.position(), lastSkipOffset));
      // resets the bytebuffer iterator before pushing it
      chunk.position(0);
      chunks.add(chunk);
      totalBytes += chunk.capacity();
      compressedList.position(compressedList.position() + lastSkipOffset);
    }

    // allocates the resulting array into the instance's bytebuffer
    result.postingList = ByteBuffer.allocate(totalBytes);
    for (ByteBuffer i : chunks) {
      result.postingList.put(i);
    }
    result.postingList.position(0);

    return result;
  }

  /**
   * BM25 implementation
   * 
   * @param ndocs  collection size
   * @param doclen document's size
   * @param avdl   average document length in the collection
   * @return the double representing the calculated score
   */
  public double score(int ndocs, int doclen, double avdl) {
    return noccurrences * ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / avdl)) + tf)
        * Math.log10((double) ndocs / (double) this.totalLength));
  }

  /**
   * TFIDF implementation
   * 
   * @param ndocs collection size
   * @return double representing the calculated score
   */
  public double tfidf(int ndocs) {
    return noccurrences * (1 + (Math.log10(tf))) * (Math.log10((double) ndocs / (double) this.totalLength));
  }

  @Override
  public int compareTo(CompressedPostingList p) {
    return Double.compare(this.upperBound, p.upperBound);
  }
}
