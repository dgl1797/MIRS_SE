package unipi.mirs.models;

/**
 * Class to just help in the conversion of read data into the correct type instead of using Object class and cast
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
