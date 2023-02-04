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
import unipi.mirs.models.VocabularyModel;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.TextNormalizationFunctions;

public class IndexBuilder {
  private Scanner stdin;

  private boolean debugmode = false;
  /*loaded once for the entire building process*/ private HashSet<String> stopwords;
  /*opened once for the entire building process*/ private BufferedWriter doctable;
  /*resettable*/ private int currentDocID = 0;
  /*unresettable*/ private int currentChunkID = 0;
  private static final int CHUNKSIZE = 524_288; // 2^19 number of documents in any chunk
  private String SELECTED_PATH = null;
  private boolean stopnostem = false;

  // int[0] is the docid, int[1] the term frequency in the docid
  /*resettable*/ private TreeMap<String, ArrayList<int[]>> chunk;

  public IndexBuilder(Scanner stdin, boolean stopnostem_mode) throws IOException {
    // ASSIGN LOCAL PARAMETERS
    this.stdin = stdin;
    this.stopnostem = stopnostem_mode;

    // SELECT THE CORRECT DESTINATION FOR THE INDEX
    this.SELECTED_PATH = stopnostem_mode ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();

    // DETECT ANOTHER INSTANCE OF THE INDEX INSIDE THE SELECTED PATH
    File dtf = Paths.get(SELECTED_PATH, "doctable.dat").toFile();
    if (dtf.exists()) {
      ConsoleUX.ErrorLog("Index already present, opearte in overwite mode? [Y/n]: ", "");
      String choice = this.stdin.nextLine().toLowerCase();
      if (choice.equals("n"))
        throw new IOException("operation cancelled by the user");
      else {
        dtf.delete();
      }
    }
    dtf.createNewFile();

    // ENABLE/DISABLE DEBUG MODE BASING ON USER INPUT
    ConsoleUX.DebugLog("Do you want to create debug files? [Y/n]: ", "");
    String choice = this.stdin.nextLine().toLowerCase();
    if (!choice.equals("n")) {
      this.debugmode = true;
    }

    // LOAD STOPWORDS IF FILTERING IS ENABLED
    this.stopwords = this.stopnostem ? new HashSet<>() : TextNormalizationFunctions.load_stopwords();

    // INITIALIZE RESOURCES
    this.doctable = new BufferedWriter(new FileWriter(dtf));
    this.chunk = new TreeMap<>();
  }

  // CORE FUNCTIONS

  public void reset() {
    this.chunk.clear();
    this.stopwords.clear();
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

    ConsoleUX.DebugLog("Writing chunk " + currentChunkID + " to file...");
    int wroteFiles = currentDocID;
    int cid = currentChunkID;

    // FORMAT FILENAMES
    String chunkdocidindexname = String.format("docids_%d.dat", currentChunkID);
    String chunkfrequenciesname = String.format("frequencies_%d.dat", currentChunkID);
    String chunkdebugname = String.format("debug_%d.dbg", currentChunkID);
    String chunkvocabularyname = String.format("lexicon_%d.dat", currentChunkID);

    // OPEN FILES
    File chunkdocidindex = Paths.get(SELECTED_PATH, chunkdocidindexname).toFile();
    File chunkfrequencyindex = Paths.get(SELECTED_PATH, chunkfrequenciesname).toFile();
    File chunkdebug = Paths.get(SELECTED_PATH, chunkdebugname).toFile();
    File chunkvocabulary = Paths.get(SELECTED_PATH, chunkvocabularyname).toFile();
    if (chunkdocidindex.exists()) {
      chunkdocidindex.delete();
    }
    if (chunkfrequencyindex.exists()) {
      chunkfrequencyindex.delete();
    }
    if (chunkdebug.exists() && debugmode) {
      chunkdebug.delete();
    }
    if (chunkvocabulary.exists()) {
      chunkvocabulary.delete();
    }
    chunkdocidindex.createNewFile();
    chunkvocabulary.createNewFile();
    chunkfrequencyindex.createNewFile();
    if (debugmode)
      chunkdebug.createNewFile();

    // PREPARE DATA STREAMS
    FileOutputStream didw = new FileOutputStream(chunkdocidindex);
    FileOutputStream frqw = new FileOutputStream(chunkfrequencyindex);
    BufferedWriter vcw = new BufferedWriter(new FileWriter(chunkvocabulary));
    BufferedWriter dbgw = null;
    if (debugmode) {
      dbgw = new BufferedWriter(new FileWriter(chunkdebug));
    }

    // LOOP OVER THE ENTRIES OF THE CHUNK TO WRITE DOWN FILES
    long currentByte = 0;
    for (Entry<String, ArrayList<int[]>> en : chunk.entrySet()) {
      // int[0] is the docid and int[1] is the frequency
      int plLength = en.getValue().size();
      vcw.write(String.format("%s\t%d-%d\n", en.getKey(), currentByte, plLength));
      if (debugmode) {
        dbgw.write(en.getKey() + "\t");
        dbgw.write("size: " + plLength + " -> ");
      }
      ByteBuffer didbuffer = ByteBuffer.allocate(plLength * Integer.BYTES);
      ByteBuffer frqbuffer = ByteBuffer.allocate(plLength * Integer.BYTES);
      for (int[] x : en.getValue()) {
        didbuffer.putInt(x[0]);
        frqbuffer.putInt(x[1]);
        currentByte += Integer.BYTES;
      }
      didw.write(didbuffer.array());
      frqw.write(frqbuffer.array());
    }
    if (debugmode) {
      dbgw.write("\n");
    }
    // CLOSE RESOURCES
    didw.close();
    frqw.close();
    vcw.close();
    if (debugmode || dbgw != null) {
      dbgw.close();
    }

    // RESET INDEX_BUILDER INSTANCE
    chunk.clear();
    currentChunkID++;
    currentDocID = 0;
    ConsoleUX.DebugLog("Wrote " + (wroteFiles + cid * CHUNKSIZE) + " documents to file.");
  }

  /**
   * adds a single document to the current chunk's data structures, increasing the currentDocID at each call and calling
   * the write_chunk function if the chunk's limit is reached
   * 
   * @param document the document to be added as a string in the format docno\tdocbody
   * @throws IOException
   */
  public void addDocument(String document) throws IOException {
    // SPLIT DOCNO AND DOCBODY DISCARDING MALFORMATTED DOCUMENT
    String[] parts = document.split("\t");
    if (parts.length < 2)
      return;
    String docno = parts[0];
    String docbody = parts[1];

    // NORMALIZE TEXT
    docbody = TextNormalizationFunctions.cleanText(docbody);

    // LOOP OVER TOKENS FOR CHUNK FILLING
    int doclen = 0;
    int realDocID = currentDocID + (CHUNKSIZE * currentChunkID);
    for (String t : docbody.split(" ")) {
      // DISCARD STOPWORDS AND STEM IF FILTERING IS ENABLED
      if (!stopwords.contains(t)) {
        t = stopnostem ? t : TextNormalizationFunctions.ps.stem(t);
        doclen++;
        // FILL CHUNK
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

    // UPDATE DOCTABLE WITH DOCUMENT'S STATISTICS
    write_doctable(docno, realDocID, doclen);

    // PREPARE FOR NEXT DOCUMENT
    currentDocID++;
    if (currentDocID == CHUNKSIZE) {
      // reset and write of the chunk
      write_chunk();
    }
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
      // RENAME REMAINING FILE TO MATCH THE INCREMENTAL COUNT
      ConsoleUX.DebugLog("Renaming chunks _" + li + " to _" + newindex);
      Path oldPath = Paths.get(SELECTED_PATH, String.format("docids_%d.dat", li));
      Path newPath = Paths.get(SELECTED_PATH, String.format("docids_%d.dat", newindex));
      rename(oldPath, newPath);
      oldPath = Paths.get(SELECTED_PATH, String.format("frequencies_%d.dat", li));
      newPath = Paths.get(SELECTED_PATH, String.format("frequencies_%d.dat", newindex));
      rename(oldPath, newPath);

      // RENAME DEBUG FILES TOO
      if (debugmode) {
        oldPath = Paths.get(SELECTED_PATH, String.format("debug_%d.dbg", li));
        newPath = Paths.get(SELECTED_PATH, String.format("debug_%d.dbg", newindex));
        rename(oldPath, newPath);
      }

      // RENAME LEXICON FILES TOO
      oldPath = Paths.get(SELECTED_PATH, String.format("lexicon_%d.dat", li));
      newPath = Paths.get(SELECTED_PATH, String.format("lexicon_%d.dat", newindex));
      rename(oldPath, newPath);

    } else {
      // OPEN LEFT; RIGHT AND TMP FILES FOR THE CHUNK MERGING
      ConsoleUX.DebugLog("Merging chunks _" + li + " and _" + ri + " into _" + newindex);
      // [0] is left, [1] is right, [2] is tmp
      File[] didFiles = getFiles("docids", li, ri);
      File[] frqFiles = getFiles("frequencies", li, ri);
      File[] dbgFiles = debugmode ? getFiles("debug", li, ri) : null;
      File[] lsFiles = getFiles("lexicon", li, ri);

      // OPEN THE STREAM READERS
      BufferedReader[] lexicons = new BufferedReader[] { new BufferedReader(new FileReader(lsFiles[0])),
          new BufferedReader(new FileReader(lsFiles[1])) };
      FileInputStream[] didindexes = new FileInputStream[] { new FileInputStream(didFiles[0]),
          new FileInputStream(didFiles[1]) };
      FileInputStream[] frqindexes = new FileInputStream[] { new FileInputStream(frqFiles[0]),
          new FileInputStream(frqFiles[1]) };

      // OPEN THE STREAM WRITERS
      BufferedWriter newLexicon = new BufferedWriter(new FileWriter(lsFiles[2]));
      BufferedWriter debugWriter = debugmode ? new BufferedWriter(new FileWriter(dbgFiles[2])) : null;
      FileOutputStream newdidIndex = new FileOutputStream(didFiles[2]);
      FileOutputStream newfrqIndex = new FileOutputStream(frqFiles[2]);

      // READ LEFT AND RIGHT TERMS FOR MERGE COMPARISON
      String leftTerm = lexicons[0].readLine();
      String rightTerm = lexicons[1].readLine();
      long currentByte = 0;
      while (leftTerm != null || rightTerm != null) {
        if (leftTerm == null) {
          // only rightTerms remain
          loadFileinto(lexicons[1], didindexes[1], frqindexes[1], newLexicon, newdidIndex, newfrqIndex, debugWriter,
              currentByte, rightTerm);
          break;
        }
        if (rightTerm == null) {
          // only leftTerms remain
          loadFileinto(lexicons[0], didindexes[0], frqindexes[0], newLexicon, newdidIndex, newfrqIndex, debugWriter,
              currentByte, leftTerm);
          break;
        }
        // TAKE LEFT AND RIGHT TERMS AND THEIR RESPECTIVE INFOS
        VocabularyModel leftmodel = new VocabularyModel(leftTerm, false);
        VocabularyModel rightmodel = new VocabularyModel(rightTerm, false);

        if (leftmodel.term.equals(rightmodel.term)) {
          // concatenate right's posting to left's posting
          byte[] dl = didindexes[0].readNBytes((leftmodel.plLength) * Integer.BYTES);
          byte[] dr = didindexes[1].readNBytes((rightmodel.plLength) * Integer.BYTES);

          byte[] fl = frqindexes[0].readNBytes((leftmodel.plLength) * Integer.BYTES);
          byte[] fr = frqindexes[1].readNBytes((rightmodel.plLength) * Integer.BYTES);

          byte[] dresult = ByteBuffer.allocate(dl.length + dr.length).put(dl).put(dr).array();
          byte[] fresult = ByteBuffer.allocate(fl.length + fr.length).put(fl).put(fr).array();

          // write to tmp files
          newLexicon.write(
              String.format("%s\t%d-%d\n", leftmodel.term, currentByte, (leftmodel.plLength) + (rightmodel.plLength)));
          newdidIndex.write(dresult);
          newfrqIndex.write(fresult);
          if (debugmode) {
            debugWriter
                .write(String.format("%s\t%d -> %s%s\n", leftmodel.term, (leftmodel.plLength) + (rightmodel.plLength),
                    byteBufferToString(ByteBuffer.wrap(dresult)), byteBufferToString(ByteBuffer.wrap(fresult))));
          }

          // advance both the files
          leftTerm = lexicons[0].readLine();
          rightTerm = lexicons[1].readLine();
          currentByte += dresult.length;

        } else if (leftmodel.term.compareTo(rightmodel.term) < 0) {
          // lterm comes before rterm
          byte[] dl = didindexes[0].readNBytes((leftmodel.plLength) * Integer.BYTES);
          byte[] fl = frqindexes[0].readNBytes((leftmodel.plLength) * Integer.BYTES);

          newLexicon.write(String.format("%s\t%d-%d\n", leftmodel.term, currentByte, (leftmodel.plLength)));
          newdidIndex.write(dl);
          newfrqIndex.write(fl);
          if (debugmode) {
            debugWriter.write(String.format("%s\t%d -> %s%s\n", leftmodel.term, (leftmodel.plLength),
                byteBufferToString(ByteBuffer.wrap(dl)), byteBufferToString(ByteBuffer.wrap(fl))));
          }
          leftTerm = lexicons[0].readLine();
          currentByte += dl.length;
        } else {
          // rterm comes before rterm
          byte[] dr = didindexes[1].readNBytes((rightmodel.plLength) * Integer.BYTES);
          byte[] fr = frqindexes[1].readNBytes((rightmodel.plLength) * Integer.BYTES);
          newLexicon.write(String.format("%s\t%d-%d\n", rightmodel.term, currentByte, (rightmodel.plLength)));
          newdidIndex.write(dr);
          newfrqIndex.write(fr);
          if (debugmode) {
            debugWriter.write(String.format("%s\t%d -> %s\n", rightmodel.term, (rightmodel.plLength),
                byteBufferToString(ByteBuffer.wrap(dr)), byteBufferToString(ByteBuffer.wrap(fr))));
          }
          rightTerm = lexicons[1].readLine();
          currentByte += dr.length;
        }
      }

      // closing all the buffers
      lexicons[0].close();
      lexicons[1].close();
      didindexes[0].close();
      didindexes[1].close();
      frqindexes[0].close();
      frqindexes[1].close();
      newLexicon.close();
      newdidIndex.close();
      newfrqIndex.close();
      if (debugmode || debugWriter != null)
        debugWriter.close();

      // delete old files, rename tmp file into _newindex
      //  CLEANING INDEX FILES
      while (!didFiles[0].delete());
      while (!didFiles[1].delete());
      while (!frqFiles[0].delete());
      while (!frqFiles[1].delete());
      //  CLEANING LEXICON FILES
      while (!lsFiles[0].delete());
      while (!lsFiles[1].delete());
      //  CLEANING DEBUG FILES
      if (debugmode) {
        while (!dbgFiles[0].delete());
        while (!dbgFiles[1].delete());
      }

      // RENAIMING OF THE FILES TO AVOID ISSUES WITH DELETE IT HAS BEEN SEPARATED FROM THE CLEANING STEPS
      while (!didFiles[2].renameTo(getNewFileName("docids", newindex)));
      while (!frqFiles[2].renameTo(getNewFileName("frequencies", newindex)));
      while (!lsFiles[2].renameTo(getNewFileName("lexicon", newindex)));
      if (debugmode) {
        while (!dbgFiles[2].renameTo(getNewFileName("debug", newindex)));
      }
    }
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

  // HELPER FUNCTIONS

  /**
   * Helper function to get the new file name, check if it already exists and if it is the debug file
   * 
   * @param base     file name from which debug file is recognized if equals to "debug"
   * @param newindex the new chunkID to be assigned to the final file name
   * @return a File object having the correct path and the newindex attached as chunkID
   * @throws IOException
   */
  private File getNewFileName(String base, int newindex) throws IOException {
    // EVALUATE CORRECT EXTENSION
    String extension = base.equals("debug") ? "dbg" : "dat";

    // CHECK FILE SAFETY
    File newFilename = Paths.get(SELECTED_PATH, String.format("%s_%d.%s", base, newindex, extension)).toFile();
    if (newFilename.exists())
      throw new IOException(
          "Something went wrong in the merging phase: " + newFilename.toString() + " already existed at rename step");

    // RETURN THE NEW FILE OBJECT
    return newFilename;
  }

  /**
   * Helper function to write the next document in the doctable file
   * 
   * @param docno  docno of the document to be added
   * @param docid  mapped docid for that docno
   * @param doclen total length of the document's body
   * @throws IOException
   */
  private void write_doctable(String docno, int docid, int doclen) throws IOException {
    // APPEND LINE RELATIVE TO THE ADDED DOCUMENT INTO DOCTABLE
    String doctcontent = String.format("%d\t%s-%d\n", docid, docno, doclen);
    this.doctable.write(doctcontent);
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
    // EVALUATE CORRECT EXTENSION
    String extension = (base.equals("debug")) ? "dbg" : "dat";

    // GENERATE THE FILES
    File[] resultFiles = new File[] {
        Paths.get(SELECTED_PATH, String.format("%s_%d.%s", base, left, extension)).toFile(),
        Paths.get(SELECTED_PATH, String.format("%s_%d.%s", base, right, extension)).toFile(),
        Paths.get(SELECTED_PATH, String.format("%s_tmp.%s", base, extension)).toFile() };

    // LOOP OVER THE FILES TO CHECK THE CORRECTNESS OF EACH
    for (int i = 0; i < 3; i++) {
      // if left and right files aren't both available the merging is impossible
      if ((i == 0 || i == 1) && !resultFiles[i].exists())
        throw new IOException("Impossible merge operation: " + resultFiles[i].toString() + " doesn't exist");
      // if tmp file is already present rewrite it
      if (i == 2 && resultFiles[i].exists())
        resultFiles[i].delete();
      resultFiles[i].createNewFile();
    }

    // RETURN THE ARRAY OF FILES
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
    // DO NOT RENAME FILE INTO ITSELF
    if (p1.equals(p2))
      return;

    // CHECK RESOURCES VALIDITY
    File f1 = p1.toFile();
    if (!f1.exists())
      throw new IOException("Impossible rename operation: " + f1.toString() + " doesn't exist");
    File f2 = p2.toFile();
    if (f2.exists()) {
      f2.delete();
    }

    // RENAME
    while (!f1.renameTo(f2));
  }

  /**
   * Helper Function that loads the entire remaining content of a buffered chunk into the merged chunk
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
  private void loadFileinto(BufferedReader lexicon, FileInputStream didindex, FileInputStream frqindex,
      BufferedWriter newLexicon, FileOutputStream newdidIndex, FileOutputStream newfrqIndex, BufferedWriter newDebug,
      long currentByte, String currentTerm) throws IOException {

    // LOOP OVER THE REMAINING TERMS IN THE LEXICON TO UPDATE THE TMP FILES
    do {
      // prepare data
      VocabularyModel model = new VocabularyModel(currentTerm, false);

      // save posting list into byte[]
      int size = Integer.BYTES * (model.plLength);
      byte[] dpl = didindex.readNBytes(size);
      byte[] gpl = frqindex.readNBytes(size);

      // update tmp files
      newLexicon.write(String.format("%s\t%d-%d\n", model.term, currentByte, (model.plLength)));
      if (debugmode) {
        newDebug.write(String.format("%s\t%d -> %s\n", model.term, (model.plLength),
            byteBufferToString(ByteBuffer.wrap(dpl)), byteBufferToString(ByteBuffer.wrap(gpl))));
      }
      newdidIndex.write(dpl);
      newfrqIndex.write(gpl);

      // prepare next iteration
      currentByte += size;
    } while ((currentTerm = lexicon.readLine()) != null);
  }

  /**
   * Helper Function that converts a ByteBuffer to a string [for debugging purposes]
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
}
