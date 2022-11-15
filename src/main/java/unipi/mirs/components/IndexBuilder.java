package unipi.mirs.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Scanner;

import unipi.mirs.utilities.TextNormalizationFunctions;

public class IndexBuilder {
  private Scanner stdin;
  private HashSet<String> stopwords = new HashSet<>();
  private HashMap<String, ArrayList<Integer[]>> vocabulary;
  private LinkedList<Object[]> docIndex;

  public IndexBuilder(Scanner stdin) throws IOException {
    this.stdin = stdin;
    this.vocabulary = new HashMap<>();
    this.docIndex = new LinkedList<>();
    this.stopwords = TextNormalizationFunctions.load_stopwords();
  }

  public void addDocument(String document) {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int docLen = 0;
    // not .size() - 1 because this document still hasn't been added to the docIndex array, .size should be O[1]
    int docid = docIndex.size();
    for (String t : docbody.split(" ")) {
      if (!stopwords.contains(t)) {
        t = TextNormalizationFunctions.ps.stem(t);
        docLen++;
        if (vocabulary.get(t).get(vocabulary.get(t).size() - 1)[0] == docid) {
          // document already present in the posting list of t so update the counter:
          vocabulary.get(t).get(vocabulary.get(t).size() - 1)[1] += 1;
        } else {
          // document not present so push into the arraylist of the term
          vocabulary.get(t).add(new Integer[] { docid, 1 });
        }
      }
    }
    docIndex.add(new Object[] { new String(docno), Integer.valueOf(docLen) });
  }
}
