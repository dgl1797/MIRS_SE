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
  /*loaded once for the entire building process*/ private HashSet<String> stopwords;
  /*opened once for the entire building process*/ private BufferedWriter doctable;
  /*resettable*/ private int currentDocID = 0;
  /*unresettable*/ private int currentChunkID = 0;
  private static final int CHUNKSIZE = 524_288; // 2^19
  private static boolean stopnostem = false;

  // int[0] is the docid, int[1] the term frequency in the docid
  /*resettable*/ private TreeMap<String, ArrayList<int[]>> chunk;

  /**
   * Helper function to write the next document in the doctable file
   * 
   * @param docno  docno of the document to be added
   * @param docid  mapped docid for that docno
   * @param doclen total length of the document's body
   * @throws IOException
   */
  private void write_doctable(String docno, int docid, int doclen) throws IOException {
    String doctcontent = String.format("%d\t%s-%d\n", docid, docno, doclen);
    this.doctable.write(doctcontent);
  }

  public IndexBuilder(Scanner stdin, boolean stopnostem_mode) throws IOException {
    this.stdin = stdin;
    this.stopnostem = stopnostem_mode;
    // detection of another instance of index, request for overwrite mode
    File dtf = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "doctable.dat").toString());
    if (dtf.exists()) {
      ConsoleUX.ErrorLog("Index already present, opearte in overwite mode? [Y/n]: ", "");
      String choice = this.stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        throw new IOException("operation cancelled by the user");
      else {
        dtf.delete();
      }
    }
    // request for debugmode activation which creates debug files for each inverted index's chunk
    ConsoleUX.DebugLog("Do you want to create debug files? [Y/n]: ", "");
    String choice = this.stdin.nextLine().toLowerCase();
    if (!choice.equals("n")) {
      this.debugmode = true;
    }
    this.stopwords = TextNormalizationFunctions.load_stopwords();
    dtf.createNewFile();
    this.doctable = new BufferedWriter(new FileWriter(dtf));
    this.chunk = new TreeMap<>();
  }

  /**
   * adds a single document to the current chunk's data structures, increasing the currentDocID at each call and calling
   * the write_chunk function if the chunk's limit is reached
   * 
   * @param document the document to be added as a string in the format docno\tdocbody
   * @throws IOException
   */
  public void addDocument(String document) throws IOException {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int doclen = 0;
    int realDocID = currentDocID + (CHUNKSIZE * currentChunkID);
    for (String t : docbody.split(" ")) {

      if (!stopwords.contains(t) || stopnostem) {
        t = stopnostem ? t: TextNormalizationFunctions.ps.stem(t);
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
      ConsoleUX.DebugLog("Writing chunk " + currentChunkID + " to file...");
      int wroteFiles = currentDocID;
      int cid = currentChunkID;
      write_chunk();
      ConsoleUX.DebugLog("Wrote " + (wroteFiles + cid * CHUNKSIZE) + " documents to file.");
    }
  }

  /**
   * writes a chunk into a file with currentChunkID, then clears all the unnecessary data structures for the next chunk
   * to be processed
   * 
   * @throws IOException
   */
  public void write_chunk() throws IOException {
    if (currentDocID == 0)
      return;

      String chunkinvertedindexname;
      String chunkdebugname ;
      String chunkvocabularyname ;
      Path chunkinvertedindexPath ;
      Path chunkdebugPath;
      Path chunkvocabularyPath;

    if(!stopnostem)
    {
      // No stopwords, Stemming active
      chunkinvertedindexname = String.format("inverted_index_%d.dat", currentChunkID);
      chunkdebugname = String.format("debug_%d.dbg", currentChunkID);
      chunkvocabularyname = String.format("lexicon_%d.dat", currentChunkID);
      chunkinvertedindexPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkinvertedindexname);
      chunkdebugPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkdebugname);
      chunkvocabularyPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkvocabularyname);

    }
    else
    {
      //With stopwords, Stemming disabled
      chunkinvertedindexname = String.format("inverted_index_%d.dat", currentChunkID);
      chunkdebugname = String.format("debug_%d.dbg", currentChunkID);
      chunkvocabularyname = String.format("lexicon_%d.dat", currentChunkID);
      chunkinvertedindexPath = Paths.get(Constants.STOPNOSTEM_OUTPUT_DIR.toString(), chunkinvertedindexname);
      chunkdebugPath = Paths.get(Constants.STOPNOSTEM_OUTPUT_DIR.toString(), chunkdebugname);
      chunkvocabularyPath = Paths.get(Constants.STOPNOSTEM_OUTPUT_DIR.toString(), chunkvocabularyname);

    }

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

  /**
   * closes the doctable file
   * 
   * @throws IOException
   */
  public void closeDocTable() throws IOException {
    this.doctable.close();
  }

  /**
   * @return currentChunkID of the builder
   */
  public int getNChunks() {
    return this.currentChunkID;
  }

  /**
   * Helper function that gets the filepath of left, right and tmp chunks, checking also if it is debug file and if
   * files are correctly organized in data/output folder
   * 
   * @param base  the base file name from which debug file is recognized from base.equals("debug")
   * @param left  the chunkID of the left chunk
   * @param right the chunkID of the right chunk
   * @return a 3-sized array of Files where File[0] is the left chunk, File[1] is the right chunk and File[2] is the tmp
   *         file
   * @throws IOException
   */
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

  /**
   * Helper function to rename a file in p1 to a file in p2
   * 
   * @param p1 the origin path
   * @param p2 the destination path
   * @throws IOException
   */
  private void rename(Path p1, Path p2) throws IOException {
    if (p1.equals(p2))
      return;
    File f1 = new File(p1.toString());
    if (!f1.exists())
      throw new IOException("Impossible rename operation: " + f1.toString() + " doesn't exist");
    File f2 = new File(p2.toString());
    if (f2.exists()) {
      f2.delete();
    }
    f1.renameTo(f2);
  }

  /**
   * Helper function to split the %d-%d of a posting into docid-plLength
   * 
   * @param componentsPart the string in the format "%d-%d"
   * @return the int[] where [0] is the docid and [1] is the plLength
   */
  private int[] getComponents(String componentsPart) {
    String[] scomponents = componentsPart.split("-");
    return new int[] { Integer.parseInt(scomponents[0]), Integer.parseInt(scomponents[1]) };
  }

  /**
   * Helper function to get the new file name, check if it already exists and if it is the debug file
   * 
   * @param base     file name from which debug file is recognized if equals to "debug"
   * @param newindex the new chunkID to be assigned to the final file name
   * @return a File object having the correct path and the newindex attached as chunkID
   * @throws IOException
   */
  private File getNewFileName(String base, int newindex) throws IOException {
    String extension = base.equals("debug") ? "dbg" : "dat";
    Path newPath = Paths.get(Constants.OUTPUT_DIR.toString(), String.format("%s_%d.%s", base, newindex, extension));
    File newFilename = new File(newPath.toString());
    if (newFilename.exists())
      throw new IOException(
          "Something went wrong in the merging phase: " + newFilename.toString() + " already existed at rename step");
    return newFilename;
  }

  /**
   * converts a ByteBuffer to a string
   * 
   * @param ib the ByteBuffer
   * @return the string composed by all the integers in the ByteBuffer
   */
  private String byteBufferToString(ByteBuffer ib) {
    int nextint;
    String rString = "";
    while (ib.hasRemaining()) {
      nextint = ib.getInt();
      rString += (nextint + (ib.hasRemaining() ? " - " : ""));
    }
    return rString;
  }

  /**
   * Loads the entire remaining content of a buffered chunk into the merged chunk
   * 
   * @param lexicon     the lexicon of the chunk to be loaded into the merged chunk
   * @param invindex    the inverted index of the chunk to be loaded into the merged chunk
   * @param newLexicon  the merged lexicon stream
   * @param newInvindex the merged inverted index stream
   * @param newDebug    the debug file of the merging
   * @param currentByte the byte from which the load needs to be started
   * @param currentTerm the last term read from lexicon
   * @throws IOException
   */
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

  /**
   * Merges two chunks into one, opening lexicon and inverted index for both left and right terms, then writes a new
   * debug file from the resulting file
   * 
   * @param li       the chunkID of the left chunk
   * @param ri       the chunkID of the right chunk
   * @param newindex the index to be assigned at the resulting file
   * @throws IOException
   */
  public void merge(int li, int ri, int newindex) throws IOException {
    if (li == ri) {
      // just rename the three needed files into *.newindex
      ConsoleUX.DebugLog("Renaming chunks _" + li + " to _" + newindex);
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
      ConsoleUX.DebugLog("Merging chunks _" + li + " and _" + ri + " into _" + newindex);
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
        if (leftTerm == null) {
          // only rightTerms remain
          loadFileinto(lexicons[1], invindexes[1], newLexicon, newInvindex, debugWriter, currentByte, rightTerm);
          break;
        }
        if (rightTerm == null) {
          // only leftTerms remain
          loadFileinto(lexicons[0], invindexes[0], newLexicon, newInvindex, debugWriter, currentByte, leftTerm);
          break;
        }
        // in the middle of the parsing
        String[] parts = leftTerm.split("\t");
        String lterm = parts[0];
        int[] lcomponents = getComponents(parts[1]);
        parts = rightTerm.split("\t");
        String rterm = parts[0];
        int[] rcomponents = getComponents(parts[1]);
        if (lterm.equals(rterm)) {
          // concatenate right's posting to left's posting
          byte[] bl = invindexes[0].readNBytes(lcomponents[1] * 2 * Integer.BYTES);
          byte[] br = invindexes[1].readNBytes(rcomponents[1] * 2 * Integer.BYTES);
          byte[] result = ByteBuffer.allocate(bl.length + br.length).put(bl).put(br).array();
          newLexicon.write(String.format("%s\t%d-%d\n", lterm, currentByte, lcomponents[1] + rcomponents[1]));
          newInvindex.write(result);
          if (debugmode) {
            debugWriter.write(String.format("%s\t%d -> %s\n", lterm, lcomponents[1] + rcomponents[1],
                byteBufferToString(ByteBuffer.wrap(result))));
          }
          leftTerm = lexicons[0].readLine();
          rightTerm = lexicons[1].readLine();
          currentByte += result.length;
        } else if (lterm.compareTo(rterm) < 0) {
          // lterm comes before rterm
          byte[] bl = invindexes[0].readNBytes(lcomponents[1] * 2 * Integer.BYTES);
          newLexicon.write(String.format("%s\t%d-%d\n", lterm, currentByte, lcomponents[1]));
          newInvindex.write(bl);
          if (debugmode) {
            debugWriter
                .write(String.format("%s\t%d -> %s\n", lterm, lcomponents[1], byteBufferToString(ByteBuffer.wrap(bl))));
          }
          leftTerm = lexicons[0].readLine();
          currentByte += bl.length;
        } else {
          // rterm comes before rterm
          byte[] br = invindexes[1].readNBytes(rcomponents[1] * 2 * Integer.BYTES);
          newLexicon.write(String.format("%s\t%d-%d\n", rterm, currentByte, rcomponents[1]));
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
      //  CLEANING LEXICON FILES
      lsFiles[0].delete();
      lsFiles[1].delete();
      //  CLEANING DEBUG FILES
      if (debugmode) {
        dbgFiles[0].delete();
        dbgFiles[1].delete();
      }

      // RENAIMING OF THE FILES TO AVOID ISSUES WITH DELETE IT HAS BEEN SEPARATED FROM THE CLEANING STEPS
      while (!iiFiles[2].renameTo(getNewFileName("inverted_index", newindex)));
      while (!lsFiles[2].renameTo(getNewFileName("lexicon", newindex)));
      if (debugmode) {
        while (!dbgFiles[2].renameTo(getNewFileName("debug", newindex)));
      }
    }
  }
}
