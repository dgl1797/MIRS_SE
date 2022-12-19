package unipi.mirs.components;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import unipi.mirs.utilities.VariableByteEncoder;

public class CompressedPostingList {
  private ByteBuffer postinglist = null;
  private int skipstep;

  private CompressedPostingList() {}

  public ByteBuffer getBuffer() {
    return this.postinglist;
  }

  public int getStep() {
    return skipstep;
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

  public static CompressedPostingList from(PostingList pl) {
    int skipstep = (int) Math.ceil(Math.sqrt(pl.totalLength));
    ByteBuffer compressedList = VariableByteEncoder.encodeList(pl.getBuffer());
    ByteBuffer tmp = ByteBuffer.wrap(compressedList.array());
    ArrayList<ByteBuffer> chunks = new ArrayList<>();
    CompressedPostingList result = new CompressedPostingList();
    int totalBytes = 0;
    result.skipstep = skipstep;
    while (tmp.position() < tmp.capacity()) {
      int lastSkipOffset = result.advance(tmp, skipstep);
      ByteBuffer encodedOffset = VariableByteEncoder.encode(lastSkipOffset);
      ByteBuffer chunk = ByteBuffer.allocate(lastSkipOffset + encodedOffset.capacity());
      chunk.put(encodedOffset).put(compressedList.slice(compressedList.position(), lastSkipOffset));
      chunk.position(0);
      chunks.add(chunk);
      totalBytes += chunk.capacity();
      compressedList.position(compressedList.position() + lastSkipOffset);
    }

    result.postinglist = ByteBuffer.allocate(totalBytes);
    for (ByteBuffer i : chunks) {
      result.postinglist.put(i);
    }
    result.postinglist.position(0);

    return result;
  }
}
