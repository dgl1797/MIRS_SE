package unipi.mirs.components;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Map.Entry;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.TextNormalizationFunctions;

/**
 * Class that contains all the data structures necessary to create the index: Lexicon represented by an HashMap<String,
 * int[]> DocumentIndex represented by an ArrayList<String, int> InvertedIndex as an ArrayList<int[]>
 */
public class IndexBuilder {
  private Scanner stdin;
  private HashMap<String, int[]> vocabulary;
  private RandomAccessFile raf;

  private static int currentIdCount = 0;
  private static int currentTermId = 0;
  private static final Path INVIND_PATH = Paths.get(Constants.OUTPUT_DIR.toString(), "inverted_index.dat");

  public IndexBuilder(Scanner stdin) throws IOException {
    this.stdin = stdin;
    this.vocabulary = new HashMap<>();
    File invind = new File(INVIND_PATH.toString());
    if (!invind.exists()) {
      if (!invind.createNewFile()) {
        throw new IOException("Impossible to create inverted index file");
      }
    }
    this.raf = new RandomAccessFile(invind, "rw");
  }

  private void plWrite(HashMap<String, Integer> frequencies) {
    for (Entry<String, Integer> tf : frequencies.entrySet()) {

    }
  }

  public boolean addDocument(String document) {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
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
      int docLen = 0, docid = currentIdCount;
      currentIdCount += 1;
      HashMap<String, Integer> termfrequencies = new HashMap<>();
      for (String t : docbody.split(" ")) {
        if (!stopwords.contains(t)) {
          docLen += 1;
          if (!termfrequencies.containsKey(t)) {
            int[] vocVal = vocabulary.getOrDefault(t, new int[] { 0, 0 });
            vocVal[0] = currentTermId++;
            vocVal[1] += 1;
            vocabulary.put(t, vocVal);
            termfrequencies.put(t, 1);
          } else {
            termfrequencies.put(t, termfrequencies.get(t) + 1);
          }
        }
      }
      plWrite(termfrequencies);
    }
    return true;
  }
}
