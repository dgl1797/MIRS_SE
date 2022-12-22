package unipi.mirs.models;

/**
 * Data class holding all the necessary information of the vocabulary lines for both compressed and uncompressed formats
 */
public class VocabularyModel {
  public String term;
  public long startByte;
  public int plLength;
  public int endByte;

  public VocabularyModel(String vocabularyLine, boolean compressed) {
    String[] parts = vocabularyLine.split("\t");
    this.term = parts[0];
    parts = parts[1].split("-");
    this.startByte = Long.parseUnsignedLong(parts[0]);
    this.plLength = Integer.parseUnsignedInt(parts[1]);
    this.endByte = compressed ? Integer.parseUnsignedInt(parts[2]) : plLength * 2 * Integer.BYTES;
  }

}
