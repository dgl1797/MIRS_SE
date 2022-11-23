package unipi.mirs.components;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
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
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Wrote " + currentDocID + " documents to file.");
      write_chunk();
    }
  }

  public void write_chunk() throws IOException {
    if (currentDocID == 0)
      return;
    String chunkinvertedindexname = String.format("inverted_index_%d.dat", currentChunkID);
    String chunkdebugname = String.format("debug_%d.dbg", currentChunkID);
    String chunkvocabularyname = String.format("lexicon_%d.dat", currentChunkID);
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
    if (debugmode || dbgw != null) {
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
    String extension = (base.equals("debug")) ? "dbg" : "dat";
    Path[] resultPaths = new Path[] {
        Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_%d.%s", base, left, extension)),
        Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_%d.%s", base, right, extension)),
        Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_tmp.%s", base, extension)) };
    File[] resultFiles = new File[3];
    for (int i = 0; i < 3; i++) {
      // if left and right files aren't both available the merging is impossible
      resultFiles[i] = new File(resultPaths[i].toString());
      if ((i == 0 || i == 1) && !resultFiles[i].exists())
        throw new IOException("Impossible merge operation: " + resultFiles[i].toString() + " doesn't exist");
      // if tmp file is already present rewrite it
      if (i == 2 && resultFiles[i].exists())
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
    f1.renameTo(f2);
  }

  private int[] getComponents(String componentsPart) {
    String[] scomponents = componentsPart.split("-");
    return new int[] { Integer.parseInt(scomponents[0]), Integer.parseInt(scomponents[1]) };
  }

  private File getNewFileName(String base, int newindex) throws IOException {
    String extension = base.equals("debug") ? "dbg" : "dat";
    Path newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_%d.%s", base, newindex, extension));
    File newFilename = new File(newPath.toString());
    if (newFilename.exists())
      throw new IOException(
          "Something went wrong in the merging phase: " + newFilename.toString() + " already existed at rename step");
    return newFilename;
  }

  private String byteBufferToString(ByteBuffer ib) {
    int nextint;
    String rString = "";
    while (ib.hasRemaining()) {
      nextint = ib.getInt();
      rString += (nextint + (ib.hasRemaining() ? " - " : ""));
    }
    return rString;
  }

  private void loadFileinto(BufferedReader lexicon, FileInputStream invindex, BufferedWriter newLexicon,
      FileOutputStream newInvindex, BufferedWriter newDebug, int currentByte, String currentTerm) throws IOException {
    do {
      String[] parts = currentTerm.split("\t");
      String term = parts[0];
      int[] components = getComponents(parts[1]);
      byte[] pl = invindex.readNBytes(2 * Integer.BYTES * components[1]);
      newLexicon.write(String.format("%s\t%d-%d\n", term, components[0], components[1]));
      if (debugmode) {
        newDebug.write(String.format("%s\t%d -> %s\n", term, components[1], byteBufferToString(ByteBuffer.wrap(pl))));
      }
      newInvindex.write(pl);
      currentByte += pl.length;
    } while ((currentTerm = lexicon.readLine()) != null);
  }

  public void merge(int li, int ri, int newindex) throws IOException {
    if (li == ri) {
      // just rename the three needed files into *.newindex
      System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Renaming chunks _" + li + " to _" + newindex);
      Path oldPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("inverted_index_%d.dat", li));
      Path newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("inverted_index_%d.dat", newindex));
      rename(oldPath, newPath);
      if (debugmode) {
        oldPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("debug_%d.dbg", li));
        newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("debug_%d.dbg", newindex));
        rename(oldPath, newPath);
      }
      oldPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("lexicon_%d.dat", li));
      newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("lexicon_%d.dat", newindex));
      rename(oldPath, newPath);
    } else {
      System.out
          .println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Merging chunks _" + li + " and _" + ri + " into _" + newindex);
      // [0] is left, [1] is right, [2] is tmp
      File[] iiFiles = getFiles("inverted_index", li, ri);
      File[] dbgFiles = debugmode ? getFiles("debug", li, ri) : null;
      File[] lsFiles = getFiles("lexicon", li, ri);
      BufferedReader[] lexicons = new BufferedReader[] { new BufferedReader(new FileReader(lsFiles[0])),
          new BufferedReader(new FileReader(lsFiles[1])) };
      FileInputStream[] invindexes = new FileInputStream[] { new FileInputStream(iiFiles[0]),
          new FileInputStream(iiFiles[1]) };
      BufferedWriter newLexicon = new BufferedWriter(new FileWriter(lsFiles[2]));
      BufferedWriter debugWriter = debugmode ? new BufferedWriter(new FileWriter(dbgFiles[2])) : null;
      FileOutputStream newInvindex = new FileOutputStream(iiFiles[2]);
      String leftTerm = lexicons[0].readLine();
      String rightTerm = lexicons[1].readLine();
      int currentByte = 0;
      while (leftTerm != null || rightTerm != null) {
        // System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Actual position: " + currentByte);
        // System.out.println("left: " + leftTerm + " right: " + rightTerm);
        if (leftTerm == null) {
          // System.out.println("Ended all the left terms");
          // only rightTerms remain
          loadFileinto(lexicons[1], invindexes[1], newLexicon, newInvindex, debugWriter, currentByte, rightTerm);
          break;
        }
        if (rightTerm == null) {
          // System.out.println("Ended all the right terms");
          // only leftTerms remain
          loadFileinto(lexicons[0], invindexes[0], newLexicon, newInvindex, debugWriter, currentByte, leftTerm);
          break;
        }
        // in the middle 
        String[] parts = leftTerm.split("\t");
        String lterm = parts[0];
        int[] lcomponents = getComponents(parts[1]);
        parts = rightTerm.split("\t");
        String rterm = parts[0];
        int[] rcomponents = getComponents(parts[1]);
        // System.out.println("comparing " + lterm + " with " + rterm);
        if (lterm.equals(rterm)) {
          // System.out.println("equals");
          // concatenate right's posting to left's posting
          byte[] bl = invindexes[0].readNBytes(lcomponents[1] * 2 * Integer.BYTES);
          byte[] br = invindexes[1].readNBytes(rcomponents[1] * 2 * Integer.BYTES);
          // System.err.println("got left pl of " + bl.length + " bytes and right pl of " + br.length + " bytes");
          byte[] result = ByteBuffer.allocate(bl.length + br.length).put(bl).put(br).array();
          // System.out.println("merged into a buffer of " + result.length + " bytes");
          newLexicon.write(String.format("%s\t%d-%d\n", lterm, currentByte, lcomponents[1] + rcomponents[1]));
          // System.out.println("Wrote into lexicon new term: " + lterm + " at byte: " + currentByte + " with plLength: "
          //     + lcomponents[1] + rcomponents[1]);
          newInvindex.write(result);
          if (debugmode) {
            debugWriter.write(String.format("%s\t%d -> %s\n", lterm, lcomponents[1] + rcomponents[1],
                byteBufferToString(ByteBuffer.wrap(result))));
          }
          leftTerm = lexicons[0].readLine();
          rightTerm = lexicons[1].readLine();
          currentByte += result.length;
        } else if (lterm.compareTo(rterm) < 0) {
          // System.out.println(lterm + " comes first");
          // lterm comes before rterm
          byte[] bl = invindexes[0].readNBytes(lcomponents[1] * 2 * Integer.BYTES);
          // System.out.println("got a pl of " + bl.length + " bytes");
          newLexicon.write(String.format("%s\t%d-%d\n", lterm, currentByte, lcomponents[1]));
          // System.out.println("wrote " + lterm + " in byte " + currentByte + " with plLength of " + lcomponents[1]);
          newInvindex.write(bl);
          if (debugmode) {
            debugWriter
                .write(String.format("%s\t%d -> %s\n", lterm, lcomponents[1], byteBufferToString(ByteBuffer.wrap(bl))));
          }
          leftTerm = lexicons[0].readLine();
          currentByte += bl.length;
        } else {
          // System.out.println(rterm + " comes first");
          // rterm comes before rterm
          byte[] br = invindexes[1].readNBytes(rcomponents[1] * 2 * Integer.BYTES);
          // System.out.println("got a pl of " + br.length + " bytes");
          newLexicon.write(String.format("%s\t%d-%d\n", rterm, currentByte, rcomponents[1]));
          // System.out.println("wrote " + rterm + " at byte " + currentByte + " with a plLength of " + rcomponents[1]);
          newInvindex.write(br);
          if (debugmode) {
            debugWriter
                .write(String.format("%s\t%d -> %s\n", rterm, rcomponents[1], byteBufferToString(ByteBuffer.wrap(br))));
          }
          rightTerm = lexicons[1].readLine();
          currentByte += br.length;
        }
      }

      // closing all the buffers
      lexicons[0].close();
      lexicons[1].close();
      invindexes[0].close();
      invindexes[1].close();
      newLexicon.close();
      newInvindex.close();
      if (debugmode || debugWriter != null)
        debugWriter.close();

      // delete old files, rename tmp file into _newindex
      //  CLEANING INDEX FILES
      iiFiles[0].delete();
      iiFiles[1].delete();
      iiFiles[2].renameTo(getNewFileName("inverted_index", newindex));
      //  CLEANING LEXICON FILES
      lsFiles[0].delete();
      lsFiles[1].delete();
      lsFiles[2].renameTo(getNewFileName("lexicon", newindex));
      //  CLEANING DEBUG FILES
      if (debugmode) {
        dbgFiles[0].delete();
        dbgFiles[1].delete();
        dbgFiles[2].renameTo(getNewFileName("debug", newindex));
      }
    }
  }
}
