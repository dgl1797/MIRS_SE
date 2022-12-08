package unipi.mirs.models;

public class VocabularyModel {
  private String term;
  private long startByte;
  private int plLength;

  public VocabularyModel(String vocabularyLine) {
    String[] parts = vocabularyLine.split("\t");
    this.term = parts[0];
    parts = parts[1].split("-");
    this.startByte = Long.parseUnsignedLong(parts[0]);
    this.plLength = Integer.parseInt(parts[1]);
  }

  /**
   * @return String return the term
   */
  public String term() {
    return term;
  }

  /**
   * @param term the term to set
   */
  public void term(String term) {
    this.term = term;
  }

  /**
   * @return long return the startByte
   */
  public long startByte() {
    return startByte;
  }

  /**
   * @param startByte the startByte to set
   */
  public void startByte(long startByte) {
    this.startByte = startByte;
  }

  /**
   * @return int return the plLength
   */
  public int plLength() {
    return plLength;
  }

  /**
   * @param plLength the plLength to set
   */
  public void plLength(int plLength) {
    this.plLength = plLength;
  }

}
