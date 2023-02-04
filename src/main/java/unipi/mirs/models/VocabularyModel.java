package unipi.mirs.models;

/**
 * Data class holding all the necessary information of the vocabulary lines for both compressed and uncompressed formats
 */
public class VocabularyModel {
  public String term;
  public long dstartByte;
  public long fstartByte;
  public int plLength;
  public int dendByte;
  public int fendByte;

  public VocabularyModel(String vocabularyLine, boolean compressed) {
    String[] parts = vocabularyLine.split("\t");
    this.term = parts[0];
    parts = parts[1].split("-");
    this.dstartByte = Long.parseUnsignedLong(parts[0]);
    this.plLength = Integer.parseUnsignedInt(parts[1]);
    this.fstartByte = parts.length >= 3 ? Long.parseUnsignedLong(parts[2]) : dstartByte;
    this.dendByte = compressed ? Integer.parseUnsignedInt(parts[3]) : plLength * Integer.BYTES;
    this.fendByte = compressed ? Integer.parseUnsignedInt(parts[4]) : plLength * Integer.BYTES;
  }

}
