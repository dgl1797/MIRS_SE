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
  private ByteBuffer postingList = null;
  private int skipstep;
  private int noccurrences = 0;
  private int DocID = 0;
  private int tf = 0;
  private int lastSkipPosition = 0;
  private int lastSkipOffset = 0;
  private int lastSkipLength = 0;
  private int resettableStep = 0;

  public String term = "";
  public int totalLength = 0;
  public double upperBound = 0;

  private CompressedPostingList() {}

  public boolean isover() {
    return this.postingList.position() >= this.postingList.capacity();
  }

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

  public void increaseOccurrences() {
    this.noccurrences += 1;
  }

  private int advance(ByteBuffer bb, int skipstep) {
    int counter = 0;
    int nbytes = 0;
    while (counter < skipstep) {
      while ((bb.get() & 128) == 0 && bb.position() < bb.capacity())
        nbytes++;
      nbytes++;
      if (bb.position() >= bb.capacity())
        return nbytes;
      counter += 1;
    }
    return nbytes;
  }

  public boolean next() {
    try {
      if (postingList.position() >= postingList.capacity())
        return false;
      if (resettableStep == 0) {
        lastSkipPosition = this.postingList.position();
        lastSkipOffset = VariableByteEncoder.decodeInt(postingList);
        lastSkipLength = this.postingList.position() - lastSkipPosition;
      }
      DocID = VariableByteEncoder.decodeInt(postingList);
      tf = VariableByteEncoder.decodeInt(postingList);
      resettableStep = (resettableStep + 1) % skipstep;
      return true;
    } catch (IndexOutOfBoundsException iobe) {
      ConsoleUX.ErrorLog("Error during next() function, array out of bound:\n" + iobe.getStackTrace().toString());
      return false;
    }
  }

  public boolean nextGEQ(int docid) {
    if (this.postingList.position() >= this.postingList.capacity())
      return false;

    if (this.DocID >= docid)
      return true;

    // CHECK ON LAST DOCID
    int lastPosition = this.postingList.capacity() - 1;
    boolean isNotDocid = true;
    while ((this.postingList.get(lastPosition - 1) & 128) == 0 || isNotDocid) {
      lastPosition -= 1;
      if ((this.postingList.get(lastPosition) & 128) != 0)
        isNotDocid = false;
    }
    ByteBuffer tmp = this.postingList.slice(lastPosition, this.postingList.capacity() - lastPosition);
    int lastDocID = VariableByteEncoder.decodeInt(tmp);
    if (lastDocID < docid)
      return false;
    if (lastDocID == docid) {
      this.postingList.position(lastPosition);
      this.resettableStep = 1;
      return next();
    }
    // END CHECK

    // CHECK ON NEXT ELEMENT
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
    int newposition = this.lastSkipPosition;
    int oldskiplength = this.lastSkipLength;
    int oldskipoffset = this.lastSkipOffset;
    int oldskipposition = newposition;
    int reachedDocID = this.DocID;
    do {
      if (newposition != this.lastSkipPosition) {
        this.resettableStep = 0;
        this.postingList.position(newposition);
      }
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
      tmp = this.postingList.slice(newposition, Math.min(32, this.postingList.capacity() - newposition));
      oldskiplength = tmp.position();
      oldskipoffset = VariableByteEncoder.decodeInt(tmp);
      oldskiplength = tmp.position() - oldskiplength;
      reachedDocID = VariableByteEncoder.decodeInt(tmp);
      oldskipposition = newposition;
      if (reachedDocID == docid) {
        this.postingList.position(newposition);
        this.resettableStep = 0;
        return next();
      }
    } while (reachedDocID < docid);
    while (next()) {
      if (this.DocID >= docid)
        return true;
    }
    // END SKIPPING SEARCH
    return false;
  }

  public static CompressedPostingList openList(String term, long startByte, int endByte, int plLength,
      boolean stopnostem) throws IOException {
    CompressedPostingList cpl = new CompressedPostingList();
    cpl.term = term;
    cpl.totalLength = plLength;
    cpl.skipstep = (int) Math.ceil(Math.sqrt(plLength));
    cpl.noccurrences = 1;

    final File INV_IND_FILE = Paths
        .get(stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString(), "compressed_index",
            "inverted_index.dat")
        .toFile();

    try (FileInputStream fileInvInd = new FileInputStream(INV_IND_FILE)) {
      fileInvInd.skip(startByte);
      cpl.upperBound = ByteBuffer.wrap(fileInvInd.readNBytes(Double.BYTES)).asDoubleBuffer().get();
      byte[] pl = new byte[endByte];
      fileInvInd.read(pl);
      cpl.postingList = ByteBuffer.wrap(pl);
      cpl.lastSkipPosition = 0;
      cpl.lastSkipOffset = VariableByteEncoder.decodeInt(cpl.postingList);
      cpl.lastSkipLength = cpl.postingList.position() - cpl.lastSkipPosition;
      cpl.DocID = VariableByteEncoder.decodeInt(cpl.postingList);
      cpl.tf = VariableByteEncoder.decodeInt(cpl.postingList);
      cpl.resettableStep = 1;
      return cpl;
    } catch (IOException ioe) {
      ConsoleUX.ErrorLog("OpenList function error, cannot open file " + INV_IND_FILE.toString() + ":\n"
          + ioe.getStackTrace().toString());
      return null;
    }
  }

  public static CompressedPostingList from(PostingList pl) {
    int skipstep = (int) Math.ceil(Math.sqrt(pl.totalLength));
    ByteBuffer compressedList = VariableByteEncoder.encodeList(pl.getBuffer());
    ByteBuffer tmp = ByteBuffer.wrap(compressedList.array());
    ArrayList<ByteBuffer> chunks = new ArrayList<>();
    CompressedPostingList result = new CompressedPostingList();
    int totalBytes = 0;
    result.skipstep = skipstep;
    while (tmp.position() < tmp.capacity()) {
      int lastSkipOffset = result.advance(tmp, 2 * skipstep);
      ByteBuffer encodedOffset = VariableByteEncoder.encode(lastSkipOffset);
      ByteBuffer chunk = ByteBuffer.allocate(lastSkipOffset + encodedOffset.capacity());
      chunk.put(encodedOffset).put(compressedList.slice(compressedList.position(), lastSkipOffset));
      chunk.position(0);
      chunks.add(chunk);
      totalBytes += chunk.capacity();
      compressedList.position(compressedList.position() + lastSkipOffset);
    }

    result.postingList = ByteBuffer.allocate(totalBytes);
    for (ByteBuffer i : chunks) {
      result.postingList.put(i);
    }
    result.postingList.position(0);

    return result;
  }

  public double score(int ndocs, int doclen, double avdl) {
    return noccurrences * ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / avdl)) + tf)
        * Math.log10((double) ndocs / (double) this.totalLength));
  }

  public double tfidf(int ndocs) {
    return noccurrences * (1 + (Math.log10(tf))) * (Math.log10((double) ndocs / (double) this.totalLength));
  }

  @Override
  public int compareTo(CompressedPostingList p) {
    return Double.compare(this.upperBound, p.upperBound);
  }
}
