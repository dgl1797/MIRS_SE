package unipi.mirs.components;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Map.Entry;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.TextNormalizationFunctions;

public class IndexBuilder {
  private Scanner stdin;
  private HashSet<String> stopwords = new HashSet<>();
  /**
   * the value of vocabulary is an arraylist of 2-sized arrays where [0] is docid, [1] is term frequency
   */
  private HashMap<String, ArrayList<Integer[]>> vocabulary;
  private int currentDocID = 0;
  private BufferedWriter diWriter;

  public IndexBuilder(Scanner stdin) throws IOException {
    this.stdin = stdin;
    this.vocabulary = new HashMap<>();
    this.stopwords = TextNormalizationFunctions.load_stopwords();
    File documentIndexFile = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "document_index.dat").toString());
    if (documentIndexFile.exists()) {
      System.out.print(ConsoleUX.FG_RED + ConsoleUX.BOLD + "Document Index File already exists, overwrite? [Y/n]: "
          + ConsoleUX.RESET);
      String choice = stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        throw new IOException();
      documentIndexFile.delete();
    }
    documentIndexFile.createNewFile();
    this.diWriter = new BufferedWriter(new FileWriter(documentIndexFile));
  }

  public void closeFile() throws IOException {
    this.diWriter.close();
  }

  public void diWrite(int docid, String docno, int docLen) throws IOException {
    String docx = String.format("%d\t%s %d\n", docid, docno, docLen);
    this.diWriter.write(docx);
  }

  public void plWrite() throws IOException {
    File outfile = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "inverted_index.dat").toString());
    File vocfile = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "vocabulary.dat").toString());
    File plDebug = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "invind_debug.txt").toString());
    boolean isDebug = false;
    {
      System.out
          .print(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Do you want to debug the execution? [Y/n]: " + ConsoleUX.RESET);
      String choice = stdin.nextLine().toLowerCase();
      if (choice.equals("y")) {
        isDebug = true;
      }
    }
    if (isDebug) {
      if (plDebug.exists())
        plDebug.delete();
      plDebug.createNewFile();
    }
    if (vocfile.exists()) {
      System.out.print(
          ConsoleUX.FG_RED + ConsoleUX.BOLD + "Vocabulary file already exists, overwrite? [Y/n]: " + ConsoleUX.RESET);
      String choice = stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        return;
      vocfile.delete();
    }
    if (outfile.exists()) {
      System.out
          .print(ConsoleUX.FG_RED + ConsoleUX.BOLD + "File already exists, overwirite? [Y/n]: " + ConsoleUX.RESET);
      String choice = stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        return;
      outfile.delete();
    }
    vocfile.createNewFile();
    outfile.createNewFile();
    int currentByte = 0;
    FileOutputStream ofstream = new FileOutputStream(outfile);
    BufferedWriter vocstream = new BufferedWriter(new FileWriter(vocfile));
    BufferedWriter debugstream = null;
    if (isDebug) {
      debugstream = new BufferedWriter(new FileWriter(plDebug));
    }
    for (Entry<String, ArrayList<Integer[]>> en : vocabulary.entrySet()) {
      int startByte = currentByte;
      int plLength = en.getValue().size();
      if (isDebug) {
        debugstream.write(en.getKey() + "\t");
      }
      for (Integer[] plnode : en.getValue()) {
        byte[] bytes = ByteBuffer.allocate(2 * Integer.BYTES).putInt(plnode[0]).putInt(plnode[1]).array();
        ofstream.write(bytes);
        if (isDebug) {
          debugstream.write(String.format("%d:%d - ", plnode[0], plnode[1]));
        }
        ConsoleUX.pause(false, stdin);
        currentByte += 2 * Integer.BYTES;
      }
      if (isDebug) {
        debugstream.write("\n");
      }
      String vocLine = String.format("%s\t%d %d\n", en.getKey(), startByte, plLength);
      vocstream.write(vocLine);
    }
    ofstream.close();
    vocstream.close();
    if (isDebug) {
      debugstream.close();
    }
  }

  public void addDocument(String document) throws IOException {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int docLen = 0;
    int docid = currentDocID++;
    for (String t : docbody.split(" ")) {
      if (!stopwords.contains(t)) {
        t = TextNormalizationFunctions.ps.stem(t);
        docLen++;
        if (!vocabulary.containsKey(t)) {
          vocabulary.put(t, new ArrayList<>());
          vocabulary.get(t).add(new Integer[] { docid, 0 });
        }
        // if current docid is present it will be surely the last one in the posting list
        if (vocabulary.get(t).get(vocabulary.get(t).size() - 1)[0] == docid) {
          // document already present in the posting list of t so update the counter:
          vocabulary.get(t).get(vocabulary.get(t).size() - 1)[1] += 1;
        } else {
          // document not present so push into the arraylist of the term
          vocabulary.get(t).add(new Integer[] { docid, 1 });
        }
      }
    }
    diWrite(docid, docno, docLen);
  }
}
