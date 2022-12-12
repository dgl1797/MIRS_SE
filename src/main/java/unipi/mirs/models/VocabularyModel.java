package unipi.mirs.models;

/**
 * Class to just help in the conversion of read data into the correct type instead of using Object class and cast
 */
public class VocabularyModel {
  public String term;
  public long startByte;
  public int plLength;

  public VocabularyModel(String vocabularyLine) {
    String[] parts = vocabularyLine.split("\t");
    this.term = parts[0];
    parts = parts[1].split("-");
    this.startByte = Long.parseUnsignedLong(parts[0]);
    this.plLength = Integer.parseUnsignedInt(parts[1]);
  }

}
