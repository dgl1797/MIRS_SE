package unipi.mirs.models;

public class CVocabularyModel {
  public String term;
  public long startByte;
  public int byteLength;
  public int plLength;

  public CVocabularyModel(String cvocabularyLine) {
    String[] parts = cvocabularyLine.split("\t");
    this.term = parts[0];
    parts = parts[1].split("-");
    this.startByte = Long.parseUnsignedLong(parts[0]);
    this.byteLength = Integer.parseUnsignedInt(parts[1]);
    this.plLength = Integer.parseUnsignedInt(parts[2]);
  }
}
