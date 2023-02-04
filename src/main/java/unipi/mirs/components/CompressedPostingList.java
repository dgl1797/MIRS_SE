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
  private ByteBuffer didlist = null;
  private ByteBuffer frqlist = null;
  private int skipstep;
  private int noccurrences = 0;
  private int DocID = 0;
  private int tf = 0;
  private int dlastSkipPosition = 0;
  private int dlastSkipOffset = 0;
  private int dlastSkipLength = 0;
  private int flastSkipPosition = 0;
  private int flastSkipOffset = 0;
  private int flastSkipLength = 0;
  private int resettableStep = 0;

  // PUBLIC DATA
  public String term = "";
  public int totalLength = 0;
  public double upperBound = 0;

  private CompressedPostingList() {}

  public boolean isover() {
    return this.didlist.position() >= this.didlist.capacity();
  }

  // GETTERS 
  public ByteBuffer getDIDBuffer() {
    return this.didlist;
  }

  public ByteBuffer getFRQBuffer() {
    return this.frqlist;
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
      if (didlist.position() >= didlist.capacity())
        return false;

      // reads the skip data if the buffer is placed on a skip (one every skipstep)
      if (resettableStep == 0) {
        // for docids
        dlastSkipPosition = this.didlist.position();
        dlastSkipOffset = VariableByteEncoder.decodeInt(this.didlist);
        dlastSkipLength = this.didlist.position() - dlastSkipPosition;

        // for frequencies
        flastSkipPosition = this.frqlist.position();
        flastSkipOffset = VariableByteEncoder.decodeInt(this.frqlist);
        flastSkipLength = this.frqlist.position() - flastSkipPosition;
      }

      // reads the posting's docid and term frequency
      DocID = VariableByteEncoder.decodeInt(didlist);
      tf = VariableByteEncoder.decodeInt(frqlist);
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
    if (this.didlist.position() >= this.didlist.capacity())
      return false;

    // returns true without replacing the pointer if the current docid already is >= docid
    if (this.DocID >= docid)
      return true;

    // LAST DOCID CHECK

    // checks the last docid of the posting list to verify the passed argument is inside the posting list 
    // backwards from last position until 1 is caught
    int dlastPosition = this.didlist.capacity() - 1;
    while ((this.didlist.get(dlastPosition - 1) & 128) == 0) {
      dlastPosition--;
    }

    // same for frequencies
    int flastPosition = this.frqlist.capacity() - 1;
    while ((this.frqlist.get(flastPosition - 1) & 128) == 0) {
      flastPosition--;
    }

    // controls the lastdocid decoding a temporary buffer to not alter the postinglist iterator
    ByteBuffer tmp = this.didlist.slice(dlastPosition, this.didlist.capacity() - dlastPosition);
    int lastDocID = VariableByteEncoder.decodeInt(tmp);
    if (lastDocID < docid)
      return false;
    // places the iterator on the last item if it is exactly equal to the argument
    if (lastDocID == docid) {
      this.didlist.position(dlastPosition);
      this.frqlist.position(flastPosition);
      this.resettableStep = 1;
      return next();
    }
    // END CHECK

    // CHECK ON NEXT ELEMENT
    // the idea is to not make more iteration than a normal next() if the nextGEQ is the immediately successive docid
    tmp = this.didlist.slice(this.didlist.position(), Math.min(32, this.didlist.capacity() - this.didlist.position()));
    if (resettableStep == 0) {
      VariableByteEncoder.decodeInt(tmp);
    }
    if (VariableByteEncoder.decodeInt(tmp) >= docid) {
      return next();
    }
    // END CHECK

    // SKIPPING SEARCH
    // init of the necessary parameters
    int dnewposition = this.dlastSkipPosition;
    int doldskiplength = this.dlastSkipLength;
    int doldskipoffset = this.dlastSkipOffset;
    int doldskipposition = dnewposition;

    int fnewposition = this.flastSkipPosition;
    int foldskiplength = this.flastSkipLength;
    int foldskipoffset = this.flastSkipOffset;
    int foldskipposition = fnewposition;

    int reachedDocID = this.DocID;
    do {
      // resets the step if a skip there was another iteration before (newposition != lastskipposition)
      if (dnewposition != this.dlastSkipPosition) {
        this.resettableStep = 0;
        this.didlist.position(dnewposition);
        this.frqlist.position(fnewposition);
      }

      // places the newposition to the next skip
      dnewposition = doldskiplength + doldskipoffset + doldskipposition;
      fnewposition = foldskiplength + foldskipoffset + foldskipposition;
      if (dnewposition >= this.didlist.capacity()) {
        // LINEAR SEARCH OVER REMAINING DOCIDS
        while (next()) {
          if (this.DocID >= docid)
            return true;
        }
        return false;
        // END LINEAR SEARCH
      }

      // performs checks on a tmp slice of the posting list to not alter its iterator
      tmp = this.didlist.slice(dnewposition, Math.min(32, this.didlist.capacity() - dnewposition));
      doldskiplength = tmp.position();
      doldskipoffset = VariableByteEncoder.decodeInt(tmp);
      doldskiplength = tmp.position() - doldskiplength;
      reachedDocID = VariableByteEncoder.decodeInt(tmp);
      doldskipposition = dnewposition;
      // checks if the docid has been reached on the skip position
      if (reachedDocID == docid) {
        this.didlist.position(dnewposition);
        this.frqlist.position(fnewposition);
        this.resettableStep = 0;
        return next();
      }
      // also updates the frequency skipping informations
      tmp = this.frqlist.slice(fnewposition, Math.min(32, this.frqlist.capacity() - fnewposition));
      foldskiplength = tmp.position();
      foldskipoffset = VariableByteEncoder.decodeInt(tmp);
      foldskiplength = tmp.position() - foldskiplength;
      foldskipposition = fnewposition;
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
  public static CompressedPostingList openList(String term, long dstartByte, int dendByte, long fstartByte,
      int fendByte, int plLength, boolean stopnostem) throws IOException {
    CompressedPostingList cpl = new CompressedPostingList();

    // INITS THE BASIC DATA FOR THE POSTING LIST
    cpl.term = term;
    cpl.totalLength = plLength;
    cpl.skipstep = (int) Math.ceil(Math.sqrt(plLength));
    cpl.noccurrences = 1;

    // FILE SELECTION BASED ON FILTER SET
    final File DID_FILE = Paths
        .get(stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString(), "compressed_index",
            "docids.dat")
        .toFile();
    final File FRQ_FILE = Paths
        .get(stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString(), "compressed_index",
            "frequencies.dat")
        .toFile();

    FileInputStream didr = null;
    FileInputStream frqr = null;

    try {
      didr = new FileInputStream(DID_FILE);
      frqr = new FileInputStream(FRQ_FILE);

      // skips to the startbyte
      didr.skip(dstartByte);
      frqr.skip(fstartByte);

      // reads a double
      cpl.upperBound = ByteBuffer.wrap(didr.readNBytes(Double.BYTES)).asDoubleBuffer().get();
      // reads endbyte bytes
      byte[] dl = new byte[dendByte];
      byte[] fl = new byte[fendByte];
      didr.read(dl);
      frqr.read(fl);

      // instantiates the buffer information for the posting list
      cpl.didlist = ByteBuffer.wrap(dl);
      cpl.dlastSkipPosition = 0;
      cpl.dlastSkipOffset = VariableByteEncoder.decodeInt(cpl.didlist);
      cpl.dlastSkipLength = cpl.didlist.position() - cpl.dlastSkipPosition;

      cpl.frqlist = ByteBuffer.wrap(fl);
      cpl.flastSkipPosition = 0;
      cpl.flastSkipOffset = VariableByteEncoder.decodeInt(cpl.frqlist);
      cpl.flastSkipLength = cpl.frqlist.position() - cpl.flastSkipPosition;

      cpl.DocID = VariableByteEncoder.decodeInt(cpl.didlist);
      cpl.tf = VariableByteEncoder.decodeInt(cpl.frqlist);
      cpl.resettableStep = 1;

    } catch (IOException ioe) {
      ConsoleUX.ErrorLog("OpenList function error, cannot open file " + DID_FILE.toString() + " or "
          + FRQ_FILE.toString() + ":\n" + ioe.getMessage().toString());
      cpl = null;
    } finally {
      if (didr != null) {
        didr.close();
      }
      if (frqr != null) {
        frqr.close();
      }
    }
    return cpl;
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
    ByteBuffer compressedDIDList = VariableByteEncoder.encodeList(pl.getDIDBuffer());
    ByteBuffer compressedFRQList = VariableByteEncoder.encodeList(pl.getFRQBuffer());
    ByteBuffer dtmp = ByteBuffer.wrap(compressedDIDList.array());
    ByteBuffer ftmp = ByteBuffer.wrap(compressedFRQList.array());
    ArrayList<ByteBuffer> dchunks = new ArrayList<>();
    ArrayList<ByteBuffer> fchunks = new ArrayList<>();

    CompressedPostingList result = new CompressedPostingList();

    int dtotalBytes = 0;
    int ftotalBytes = 0;

    result.skipstep = skipstep;

    // INITIALIZES THE CHUNKS BETWEEN CONSEQUENT SKIPS AND PUTS THEM INTO THE ARRAY
    while (dtmp.position() < dtmp.capacity()) {
      int dlastSkipOffset = VariableByteEncoder.advance(dtmp, skipstep);
      int flastSkipOffset = VariableByteEncoder.advance(ftmp, skipstep);

      // the skip is generated as an offset from the current position where to jump compressed using VBE
      ByteBuffer encodedOffset = VariableByteEncoder.encode(dlastSkipOffset);
      ByteBuffer chunk = ByteBuffer.allocate(dlastSkipOffset + encodedOffset.capacity());
      chunk.put(encodedOffset).put(compressedDIDList.slice(compressedDIDList.position(), dlastSkipOffset));
      // resets the bytebuffer iterator before pushing it
      chunk.position(0);
      dchunks.add(ByteBuffer.wrap(chunk.array()));
      dtotalBytes += chunk.capacity();

      encodedOffset = VariableByteEncoder.encode(flastSkipOffset);
      chunk = ByteBuffer.allocate(flastSkipOffset + encodedOffset.capacity());
      chunk.put(encodedOffset).put(compressedFRQList.slice(compressedFRQList.position(), flastSkipOffset));
      chunk.position(0);
      fchunks.add(ByteBuffer.wrap(chunk.array()));
      ftotalBytes += chunk.capacity();

      compressedDIDList.position(compressedDIDList.position() + dlastSkipOffset);
      compressedFRQList.position(compressedFRQList.position() + flastSkipOffset);
    }

    // allocates the resulting array into the instance's bytebuffer
    result.didlist = ByteBuffer.allocate(dtotalBytes);
    result.frqlist = ByteBuffer.allocate(ftotalBytes);

    for (ByteBuffer i : dchunks) {
      result.didlist.put(i);
    }
    result.didlist.position(0);

    for (ByteBuffer i : fchunks) {
      result.frqlist.put(i);
    }
    result.frqlist.position(0);

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
