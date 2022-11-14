package unipi.mirs.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Map.Entry;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.TextNormalizationFunctions;

/**
 * Class that contains all the data structures necessary to create the index: Lexicon represented by an HashMap<String,
 * int[]> DocumentIndex represented by an ArrayList<String, int> InvertedIndex as an ArrayList<int[]>
 */
public class IndexBuilder {

  /**
   * Auxiliary class to store the different types inside the ArrayList used for DocumentIndex
   */
  private class DocIndexType {
    public String docno;
    public int doclen;

    public DocIndexType(String docno, int doclen) {
      this.doclen = doclen;
      this.docno = docno;
    }
  }

  private Scanner stdin;
  /*resettable*/private int docCount;
  /*unresettable*/private int chunknumber;
  /*unresettable*/private int termCount;
  /*unresettable*/private int docLimit;

  // int[0] is the termid and the position where the postingList of the term starts; int[1] is the postingsList length
  /*unresettable*/private HashMap<String, int[]> vocabulary;
  /*resettable*/private ArrayList<DocIndexType> documentIndex;

  public IndexBuilder(int docLimit, Scanner stdin) {
    this.stdin = stdin;
    this.docLimit = docLimit;
    this.docCount = 0;
    this.chunknumber = 0;
    this.termCount = 0;
    this.vocabulary = new HashMap<>();
    this.documentIndex = new ArrayList<>();
  };

  public IndexBuilder(Scanner stdin) {
    this.stdin = stdin;
    this.docCount = 0;
    this.chunknumber = 0;
    this.termCount = 0;
    this.docLimit = 5000;
    this.vocabulary = new HashMap<>();
    this.documentIndex = new ArrayList<>();
  }

  /**
   * performs the reset of the resettable parameters to clear some memory
   */
  private void reset() {
    this.docCount = 0;
    this.chunknumber += 1;
    this.documentIndex.clear();
  }

  /**
   * Parses the document to update the Data Structures up until this.docLimit where the method will update the files and
   * reset the instance
   * 
   * @param document the new document to be parsed and added
   * @return a boolean saying if the index building has been stopped before completion or not
   */
  public boolean addDocument(String document) {
    // updates file if we reach the docLimit for the batch
    if (docCount == docLimit - 1) {
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "parsed " + chunknumber + "th chunk:");
      for (Entry<String, int[]> en : vocabulary.entrySet()) {
        System.out.println("{" + en.getKey() + ": " + "[" + en.getValue()[0] + ", " + en.getValue()[1] + "]" + "}");
      }
      ConsoleUX.pause(true, stdin);
      // write();
      reset();
    }
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    System.out.println(ConsoleUX.CLS + ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "parsing document " + docno + " as id: "
        + (docCount + 5000 * chunknumber));
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int docLen = 0;
    HashSet<String> stopwords = new HashSet<>();
    try {
      stopwords = TextNormalizationFunctions.load_stopwords();
    } catch (IOException e) {
      System.out.println(
          ConsoleUX.FG_RED + ConsoleUX.BOLD + "Failed to load stopwords from stopwords.txt, file absent or corrupted.\n"
              + "Do you want to proceed without stopwords?[Y/n]: " + ConsoleUX.RESET);
      String answ = stdin.nextLine().toLowerCase();
      if (answ == "n")
        return false;
    } finally {
      // tokenization+stopwords_removal+stemming process
      for (String t : docbody.split(" ")) {
        if (!stopwords.contains(t)) {
          t = TextNormalizationFunctions.ps.stem(t);
          docLen += 1;
          int[] tcount = vocabulary.getOrDefault(t, new int[] { termCount++, 0 });
          tcount[1] += 1;
          vocabulary.put(t, tcount);
        }
      }
      documentIndex.add(new DocIndexType(docno, docLen));
      this.docCount += 1;
    }
    return true;
  }
}
