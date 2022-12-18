package unipi.mirs;

import java.io.IOException;

import unipi.mirs.components.DocTable;
import unipi.mirs.components.Vocabulary;

public class Tester {
  public static void main(String[] args) throws IOException {
    Vocabulary lexicon = Vocabulary.loadVocabulary(true);
    DocTable dTable = DocTable.loadDocTable(true);
    final int MAX_BYTE_LEN = (int) Math.ceil(Math.log(dTable.ndocs) / Math.log(128));
    System.out.println(MAX_BYTE_LEN);
  }
}
