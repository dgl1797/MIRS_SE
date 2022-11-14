package unipi.mirs.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
  /*unresettable*/private LinkedHashMap<String, int[]> vocabulary;
  /*resettable*/private HashMap<String, HashMap<Integer, Integer>> postingLists;
  /*resettable*/private ArrayList<DocIndexType> documentIndex;

  public IndexBuilder(int docLimit, Scanner stdin) {
    this.stdin = stdin;
    this.docLimit = docLimit;
    this.docCount = 0;
    this.chunknumber = 0;
    this.termCount = 0;
    this.vocabulary = new LinkedHashMap<>();
    this.documentIndex = new ArrayList<>();
    this.postingLists = new HashMap<>();
  };

  public IndexBuilder(Scanner stdin) {
    this.stdin = stdin;
    this.docCount = 0;
    this.chunknumber = 0;
    this.termCount = 0;
    this.docLimit = 5000;
    this.vocabulary = new LinkedHashMap<>();
    this.documentIndex = new ArrayList<>();
    this.postingLists = new HashMap<>();
  }

  /**
   * performs the reset of the resettable parameters to clear some memory
   */
  private void reset() {
    this.docCount = 0;
    this.chunknumber += 1;
    this.documentIndex.clear();
    this.postingLists.clear();
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
        String term = en.getKey();
        System.out
            .println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + term + " appearing in " + en.getValue()[1] + " documents");
        for (Entry<Integer, Integer> pl : postingLists.get(term).entrySet()) {
          System.out.print(pl.getKey() + ":" + pl.getValue() + "->");
        }
        System.out.println();
        ConsoleUX.pause(true, stdin);
      }
      ConsoleUX.pause(true, stdin);
      // write();
      reset();
    }
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    int docid = docCount + 5000 * chunknumber;
    System.out
        .println(ConsoleUX.CLS + ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "parsing document " + docno + " as id: " + docid);
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
      HashSet<String> alreadyAppeared = new HashSet<>();
      // tokenization+stopwords_removal+stemming process
      for (String t : docbody.split(" ")) {
        if (!stopwords.contains(t)) {
          t = TextNormalizationFunctions.ps.stem(t);
          docLen += 1;
          if (!vocabulary.containsKey(t)) {
            vocabulary.put(t, new int[] { termCount++, 1 });
            alreadyAppeared.add(t);
            postingLists.put(t, new HashMap<>());
            postingLists.get(t).put(docid, 1);
          } else {
            if (!alreadyAppeared.contains(t)) {
              int[] vals = vocabulary.get(t);
              vals[1] += 1;
              vocabulary.put(t, vals);
              if (!postingLists.containsKey(t)) {
                postingLists.put(t, new HashMap<>());
                postingLists.get(t).put(docid, 1);
              } else {
                if (!postingLists.get(t).containsKey(docid)) {
                  postingLists.get(t).put(docid, 1);
                } else {
                  int fq = postingLists.get(t).get(docid);
                  fq += 1;
                  postingLists.get(t).put(docid, fq);
                }
              }
              alreadyAppeared.add(t);
            } else {
              // document term's frequency
              if (!postingLists.get(t).containsKey(docid)) {
                postingLists.get(t).put(docid, 1);
              } else {
                int fq = postingLists.get(t).get(docid);
                fq += 1;
                postingLists.get(t).put(docid, fq);
              }
            }
          }
        }
      }
      documentIndex.add(new DocIndexType(docno, docLen));
      this.docCount += 1;
    }
    return true;
  }
}
