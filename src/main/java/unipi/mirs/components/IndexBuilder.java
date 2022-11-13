package unipi.mirs.components;

import java.util.HashMap;

public class IndexBuilder {
  private int docCount;
  private int docLimit;
  private HashMap<String, Integer[]> vocabulary;
  private HashMap<String, Integer[]> documentIndex;

  public IndexBuilder(int docLimit) {
    this.docLimit = docLimit;
    this.docCount = 0;
    this.vocabulary = new HashMap<>();
  };

  public IndexBuilder() {
    this.docCount = 0;
    this.docLimit = 5000;
    this.vocabulary = new HashMap<>();
  }

  public void addDocument(String document) {
    if (docCount == docLimit - 1) {
      // write();
      // reset();
    }
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
  }
}
