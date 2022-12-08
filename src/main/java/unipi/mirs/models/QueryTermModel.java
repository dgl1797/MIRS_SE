package unipi.mirs.models;

import java.io.IOException;

import unipi.mirs.components.PostingList;

public class QueryTermModel {
  private String term;
  private PostingList pl;
  private int occurrences;

  public QueryTermModel(String term, long startByte, int pllength, boolean stopnostem) throws IOException {
    this.pl = PostingList.openList(startByte, pllength, stopnostem);
    this.term = term;
    this.occurrences = 1;
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
   * @return PostingList return the pl
   */
  public PostingList pl() {
    return pl;
  }

  /**
   * @param pl the pl to set
   */
  public void pl(PostingList pl) {
    this.pl = pl;
  }

  /**
   * @return int return the occurrences
   */
  public int occurrences() {
    return occurrences;
  }

  /**
   * @param occurrences the occurrences to set
   */
  public void occurrences(int occurrences) {
    this.occurrences = occurrences;
  }

  public void increase() {
    this.occurrences += 1;
  }

}
