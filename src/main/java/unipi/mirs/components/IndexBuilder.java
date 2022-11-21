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
import java.util.HashSet;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.Map.Entry;

import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.TextNormalizationFunctions;

public class IndexBuilder {
  private Scanner stdin;

  private boolean debugmode = false;
  private HashSet<String> stopwords;
  private BufferedWriter doctable;
  private int currentDocID = 0;
  private int currentChunkID = 0;
  private static final int CHUNKSIZE = 1_000;

  // int[0] is the docid, int[1] the term frequency in the docid
  private TreeMap<String, ArrayList<int[]>> chunk;

  private void write_doctable(String docno, int docid, int doclen) throws IOException {
    String doctcontent = String.format("%s\t%d %d\n", docno, docid, doclen);
    this.doctable.write(doctcontent);
  }

  public IndexBuilder(Scanner stdin) throws IOException {
    this.stdin = stdin;

    // detection of another instance of index, request for overwrite mode
    File dtf = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "doctable_test.dat").toString());
    if (dtf.exists()) {
      System.out.print(ConsoleUX.FG_RED + ConsoleUX.BOLD + "Index already present, opearte in overwite mode? [Y/n]: ");
      String choice = this.stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        throw new IOException("operation cancelled by the user");
      else {
        dtf.delete();
      }
    }
    // request for debugmode activation which creates debug files for each inverted index's chunk
    System.out.print(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Do you want to create debug files? [Y/n]: ");
    String choice = this.stdin.nextLine().toLowerCase();
    if (!choice.equals("n")) {
      this.debugmode = true;
    }
    this.stopwords = TextNormalizationFunctions.load_stopwords();
    dtf.createNewFile();
    this.doctable = new BufferedWriter(new FileWriter(dtf));
    this.chunk = new TreeMap<>();
  }

  public void addDocument(String document) throws IOException {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int doclen = 0;
    int realDocID = currentDocID + (CHUNKSIZE * currentChunkID);
    for (String t : docbody.split(" ")) {
      if (!stopwords.contains(t)) {
        t = TextNormalizationFunctions.ps.stem(t);
        doclen++;
        if (!chunk.containsKey(t)) {
          // it is the first time it appears in the chunk
          chunk.put(t, new ArrayList<>(Arrays.asList(new int[] { realDocID, 1 })));
        } else {
          // we need to check if it is the first time the term appears in the document
          if (chunk.get(t).get(chunk.get(t).size() - 1)[0] == realDocID) {
            // already appeared in the document hence only increase the frequency
            chunk.get(t).get(chunk.get(t).size() - 1)[1] += 1;
          } else {
            // first time it appears in the document
            chunk.get(t).add(new int[] { realDocID, 1 });
          }
        }
      }
    }
    write_doctable(docno, realDocID, doclen);
    currentDocID++;
    if (currentDocID == CHUNKSIZE) {
      // reset and write of the chunk
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Writing chunk " + currentChunkID + " to file...");
      write_chunk();
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Wrote " + currentDocID + " documents to file.");
    }
  }

  public void write_chunk() throws IOException {
    if (currentDocID == 0)
      return;
    String chunkinvertedindexname = String.format("inverted_index_%d.dat", currentChunkID);
    String chunkdebugname = String.format("debug_%d.dbg", currentChunkID);
    String chunkvocabularyname = String.format("lexicon_%d", currentChunkID);
    Path chunkinvertedindexPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkinvertedindexname);
    Path chunkdebugPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkdebugname);
    Path chunkvocabularyPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkvocabularyname);
    File chunkinvertedindex = new File(chunkinvertedindexPath.toString());
    File chunkdebug = new File(chunkdebugPath.toString());
    File chunkvocabulary = new File(chunkvocabularyPath.toString());
    if (chunkinvertedindex.exists()) {
      chunkinvertedindex.delete();
    }
    if (chunkdebug.exists() && debugmode) {
      chunkdebug.delete();
    }
    if (chunkvocabulary.exists()) {
      chunkvocabulary.delete();
    }
    chunkinvertedindex.createNewFile();
    chunkvocabulary.createNewFile();
    if (debugmode)
      chunkdebug.createNewFile();
    FileOutputStream iiw = new FileOutputStream(chunkinvertedindex);
    BufferedWriter vcw = new BufferedWriter(new FileWriter(chunkvocabulary));
    BufferedWriter dbgw = null;
    if (debugmode) {
      dbgw = new BufferedWriter(new FileWriter(chunkdebug));
    }
    int currentByte = 0;
    for (Entry<String, ArrayList<int[]>> en : chunk.entrySet()) {
      int startByte = currentByte;
      int plLength = en.getValue().size();
      vcw.write(String.format("%s\t%d-%d\n", en.getKey(), startByte, plLength));
      if (debugmode) {
        dbgw.write(en.getKey() + "\t");
        dbgw.write("size: " + plLength + " -> ");
      }
      ByteBuffer b = ByteBuffer.allocate(2 * Integer.BYTES * plLength);
      for (int[] node : en.getValue()) {
        // [0] is docid [1] is frequency
        b = b.putInt(node[0]).putInt(node[1]);
        if (debugmode) {
          dbgw.write(String.format("%d:%d - ", node[0], node[1]));
        }
        currentByte += 2 * Integer.BYTES;
      }
      iiw.write(b.array());
      if (debugmode) {
        dbgw.write("\n");
      }
    }
    iiw.close();
    vcw.close();
    if (debugmode) {
      dbgw.close();
    }
    // reset
    chunk.clear();
    currentChunkID++;
    currentDocID = 0;
  }

  public void closeDocTable() throws IOException {
    this.doctable.close();
  }

  public int getNChunks() {
    return this.currentChunkID;
  }

  private File[] getFiles(String base, int left, int right) throws IOException {
    Path[] resultPaths = new Path[] {
        Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_%d.dat", base, left)),
        Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_%d.dat", base, right)),
        Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_tmp.dat", base)) };
    File[] resultFiles = new File[3];
    for (int i = 0; i < 3; i++) {
      resultFiles[i] = new File(resultPaths[i].toString());
      if (i == 0 && !resultFiles[i].exists())
        throw new IOException("Impossible merge operation: " + resultFiles[i].toString() + " doesn't exist");
      if (i > 0 && resultFiles[i].exists())
        resultFiles[i].delete();
      resultFiles[i].createNewFile();
    }
    return resultFiles;
  }

  private void rename(Path p1, Path p2) throws IOException {
    File f1 = new File(p1.toString());
    if (!f1.exists())
      throw new IOException("Impossible rename operation: " + f1.toString() + " doesn't exist");
    File f2 = new File(p2.toString());
    if (f2.exists()) {
      f2.delete();
    }
    f2.createNewFile();
    f1.renameTo(f2);
  }

  public void merge(int li, int ri, int newindex) throws IOException {
    if (li == ri) {
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Renaming chunks _" + li + " to _" + newindex);
      // just rename the three needed files into *.newindex
      Path oldPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("inverted_index_%d.dat", li));
      Path newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("inverted_index_%d.dat", newindex));
      rename(oldPath, newPath);
      if (debugmode) {
        oldPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("debug_%d.dat", li));
        newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("debug_%d.dat", newindex));
        rename(oldPath, newPath);
      }
      oldPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("lexicon_%d.dat", li));
      newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("lexicon_%d.dat", newindex));
      rename(oldPath, newPath);
    } else {
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Merging chunks _" + li + " into _" + newindex);
      // [0] is left, [1] is right, [2] is tmp
      File[] iiFiles = getFiles("inverted_index", li, ri);
      File[] dbgFiles = getFiles("debug", li, ri);
      File[] lsFiles = getFiles("lexicon", li, ri);
      // TODO: merge logic
    }
  }
}
