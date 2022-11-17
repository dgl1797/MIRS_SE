package unipi.mirs.components;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Map.Entry;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.TextNormalizationFunctions;

public class IndexBuilderTest {
  private Scanner stdin;
  private HashSet<String> stopwords;
  private BufferedWriter doctableFile;
  private boolean debugmode = false;
  // int[0] is the docid and int[1] is the term frequency in the chunk
  /*resettable*/private HashMap<String, ArrayList<int[]>> indexChunk;
  /*unresettable*/private HashMap<String, BitSet> chunkMap;

  private int chunkID = 0;
  private int currentDocID = 0;
  private int chunkSize = 880_000; // 10 chunks for 8.8M

  private void dtWrite(String docno, int docid, int doclen) throws IOException {
    String doctcontent = String.format("%s\t%d %d\n", docno, docid, doclen);
    this.doctableFile.write(doctcontent);
  }

  public IndexBuilderTest(Scanner stdin, int chunkSize) throws IOException {
    this.stdin = stdin;
    this.stopwords = TextNormalizationFunctions.load_stopwords();
    this.indexChunk = new HashMap<>();
    this.chunkMap = new HashMap<>();
    this.chunkSize = chunkSize;
    File dtf = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "doctable_test.dat").toString());
    if (dtf.exists()) {
      System.out.print(ConsoleUX.FG_RED + ConsoleUX.BOLD + "Index already present, opearte in overwite mode? [Y/n]: ");
      String choice = this.stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        throw new IOException();
      else {
        dtf.delete();
      }
    }
    System.out.print(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Do you want to create debug files? [Y/n]: ");
    String choice = this.stdin.nextLine().toLowerCase();
    if (!choice.equals("n")) {
      this.debugmode = true;
    }
    dtf.createNewFile();
    this.doctableFile = new BufferedWriter(new FileWriter(dtf));
  }

  public HashMap<String, BitSet> getChunkMap() {
    return this.chunkMap;
  }

  public void closeDocTableFile() throws IOException {
    this.doctableFile.close();
  }

  public void reset() {
    this.indexChunk.clear();
    for (String k : chunkMap.keySet()) {
      BitSet newset = new BitSet(chunkMap.get(k).size() + 1);
      newset.clear(newset.size() - 1);
      chunkMap.put(k, newset);
    }
  }

  public void write_chunk() throws IOException {
    System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Writing chunk " + (chunkID - 1) + " to file...");
    String chunkvocabularyname = String.format("%s%d.dat", "vocabulary_", chunkID - 1);
    String chunkinvertedindexname = String.format("%s%d.dat", "inverted_index_", chunkID - 1);
    String chunkdebugname = String.format("%s%d.dbg", "debug_", chunkID - 1);
    Path chunkvocabularyPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkvocabularyname);
    Path chunkinvertedindexPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkinvertedindexname);
    Path chunkdebugPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkdebugname);
    File chunkvocabulary = new File(chunkvocabularyPath.toString());
    File chunkinvertedindex = new File(chunkinvertedindexPath.toString());
    File chunkdebug = new File(chunkdebugPath.toString());
    if (chunkvocabulary.exists()) {
      chunkvocabulary.delete();
    }
    if (chunkinvertedindex.exists()) {
      chunkinvertedindex.delete();
    }
    if (chunkdebug.exists() && debugmode) {
      chunkdebug.delete();
    }
    chunkvocabulary.createNewFile();
    chunkinvertedindex.createNewFile();
    if (debugmode)
      chunkdebug.createNewFile();
    BufferedWriter vw = new BufferedWriter(new FileWriter(chunkvocabulary));
    FileOutputStream iiw = new FileOutputStream(chunkinvertedindex);
    BufferedWriter dbgw = null;
    if (debugmode) {
      dbgw = new BufferedWriter(new FileWriter(chunkdebug));
    }
    int currentByte = 0;
    for (Entry<String, ArrayList<int[]>> en : indexChunk.entrySet()) {
      int startByte = currentByte;
      int plLength = en.getValue().size();
      if (debugmode) {
        dbgw.write(en.getKey() + "\t");
      }
      for (int[] plnode : en.getValue()) {
        byte[] b = ByteBuffer.allocate(2 * Integer.BYTES).putInt(plnode[0]).putInt(plnode[1]).array();
        iiw.write(b);
        if (debugmode) {
          dbgw.write(String.format("%d:%d - ", plnode[0], plnode[1]));
        }
        currentByte += 2 * Integer.BYTES;
      }
      if (debugmode) {
        dbgw.write("\n");
      }
      String vocLine = String.format("%s\t%d %d\n", en.getKey(), startByte, plLength);
      vw.write(vocLine);
    }
    vw.close();
    iiw.close();
    if (debugmode) {
      dbgw.close();
    }
  }

  public void addDocument(String document) throws IOException {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int doclen = 0;
    if (currentDocID % chunkSize == 0) {
      chunkID += 1; // if we are at currentDocID 0 it will enter and set chunkID at 1
      if (chunkID != 1) {
        write_chunk();
        reset();
      }
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Processing Chunk " + (chunkID - 1) + "...");
    }
    // here docid is initialized at currentDocID then the increase happens
    int docid = currentDocID++;
    for (String t : docbody.split(" ")) {
      if (!stopwords.contains(t) && t.length() > 1) {
        doclen++;
        t = TextNormalizationFunctions.ps.stem(t);
        if (!chunkMap.containsKey(t)) {
          // globally a new term has arrived
          BitSet tmp = new BitSet(chunkID);
          tmp.clear();
          tmp.set(chunkID - 1);
          chunkMap.put(t, tmp);
          // if it is the first time it appears globally, it will be the first time in the chunk aswell
          indexChunk.put(t, new ArrayList<>(Arrays.asList(new int[] { docid, 1 })));
        } else {
          // term was already present in the chunkmap hence we need to check if it is the first time
          // we get the term in the chunk, if it is we need to set the bit to 1
          if (!chunkMap.get(t).get(chunkID - 1)) {
            // there it means it is the first time the term appears in the chunk hence it will not be present in the indexchunk
            indexChunk.put(t, new ArrayList<>(Arrays.asList(new int[] { docid, 1 })));
            chunkMap.get(t).set(chunkID - 1);
          } else {
            // if we are here we know the term already appeared in this chunk hence we update the indexChunk
            if (indexChunk.get(t).get(indexChunk.get(t).size() - 1)[0] == docid) {
              // last element of the posting list is the docid hence we just need to increment the frequency of that docid
              indexChunk.get(t).get(indexChunk.get(t).size() - 1)[1] += 1;
            } else {
              // last element of the posting list is not docid, hence it is the first time the term appears in the document
              indexChunk.get(t).add(new int[] { docid, 1 });
            }
          }
        }
      }
    }
    dtWrite(docno, docid, doclen);
  }
}
