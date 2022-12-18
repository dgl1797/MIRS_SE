package unipi.mirs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import unipi.mirs.components.DocTable;
import unipi.mirs.components.IndexBuilder;
import unipi.mirs.components.PostingList;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.graphics.Menu;
import unipi.mirs.models.VocabularyModel;
import unipi.mirs.utilities.Constants;
import unipi.mirs.utilities.VariableByteEncoder;

public class IndexManager {
  private static final Scanner stdin = new Scanner(System.in);
  private static String inputFile = Paths.get(Constants.INPUT_DIR.toString(), "collection.tsv").toString();
  private static boolean stopnostem_mode = false;

  /**
   * Function creating all the directories for the project's correct functioning
   * 
   * @throws IOException
   */
  private static void createFileSystem() throws IOException {
    // CREATE DATA DIRECTORY IN "user.dir/data"
    File fileSystemCreator = Constants.DATA_DIR.toFile();
    safeCreateDir(fileSystemCreator);

    // CREATE INPUT AND OUTPUT FOLDERS INSIDE OF DATA DIRECTORY
    fileSystemCreator = Constants.INPUT_DIR.toFile();
    safeCreateDir(fileSystemCreator);
    fileSystemCreator = Constants.OUTPUT_DIR.toFile();
    safeCreateDir(fileSystemCreator);

    // CREATE QUERY_FILES FOLDER INSIDE INPUT FOLDER
    fileSystemCreator = Constants.QUERY_FILES.toFile();
    safeCreateDir(fileSystemCreator);

    // CREATE FILTERED_INDEX FOLDER INSIDE OUTPUT FOLDER
    fileSystemCreator = Constants.UNFILTERED_INDEX.toFile();
    safeCreateDir(fileSystemCreator);
  }

  /**
   * Allows the user to select a new file as collection of the search engine, the collection has to be in .tsv format
   * with 'docno\tdocbody\n' format, the tsv file can also be passed with gzip commpression and the function will
   * automatically select if the file needs to be parsed with decompressor or not It will modify inputFile and
   * readCompressed class' static parameters in-place.
   * 
   * @throws IOException
   */
  private static void changeInputFile() throws IOException {
    // TAKE LIST OF FILES INSIDE OF INPUT_DIR FILTERING OUT ALL UNSUPPORTED FILES
    File inputDir = new File(Constants.INPUT_DIR.toString());
    String[] files = Arrays
        .asList(inputDir.listFiles()).stream().filter((f) -> f.isFile()).filter((f) -> f.toString().matches(".*\\.gz$")
            || f.toString().matches(".*\\.tsv$") || f.toString().matches(".*\\.tar$"))
        .map(f -> f.toString()).toArray(String[]::new);

    // LOG AN ERROR IN CASE NO COMPATIBLE FILES ARE PRESENT
    if (Arrays.asList(files).size() == 0) {
      ConsoleUX.ErrorLog("No files found, make sure to import a [.tsv; .gz; .tar.gz; .tar] file inside "
          + Constants.INPUT_DIR + " folder");
      ConsoleUX.pause(true, stdin);
      inputFile = "";
      return;
    }

    // PRINT THE MENU
    Menu filesMenu = new Menu(stdin, files);

    // RETURN THE USER'S CHOICE
    inputFile = files[filesMenu.printMenu()];
  }

  /**
   * Computes the single term query over the collection to evaluate the terms upper bound then updates the inverted
   * index and the lexicon to have the new information correctly updated
   * 
   * @throws IOException
   */
  private static void saveUpperBounds() throws IOException {
    // LOAD DOCTABLE
    DocTable dTable = DocTable.loadDocTable(stopnostem_mode);
    Path workingDirectory = (!stopnostem_mode) ? Constants.OUTPUT_DIR : Constants.UNFILTERED_INDEX;
    File lexicon = Paths.get(workingDirectory.toString(), "lexicon.dat").toFile();
    File invindex = Paths.get(workingDirectory.toString(), "inverted_index.dat").toFile();
    if (!lexicon.exists())
      throw new IOException("Impossible to proceed, lexicon file doesn't exist");
    if (!invindex.exists())
      throw new IOException("Impossible to proceed, inverted index file doesn't exist");

    // OPENING LEXICON AND INVERTED INDEX
    BufferedReader lr = new BufferedReader(new FileReader(lexicon));
    FileInputStream iir = new FileInputStream(invindex);

    // CREATE TMPS FILES WHERE TO SAVE NEW INFOS
    File tmp_lexicon = Paths.get(workingDirectory.toString(), "lexicon_tmp.dat").toFile();
    File tmp_invindex = Paths.get(workingDirectory.toString(), "inverted_index_tmp.dat").toFile();
    if (tmp_lexicon.exists())
      while (!tmp_lexicon.delete());
    if (tmp_invindex.exists())
      while (!tmp_invindex.delete());
    while (!tmp_lexicon.createNewFile());
    while (!tmp_invindex.createNewFile());
    BufferedWriter lw = new BufferedWriter(new FileWriter(tmp_lexicon));
    FileOutputStream iiw = new FileOutputStream(tmp_invindex);

    // WRITE NEW INFO INTO TMPS
    String terminfos;
    long currentByte = 0;
    while ((terminfos = lr.readLine()) != null) {
      // READ POSTING LIST INTO INT_BUFFER
      VocabularyModel model = new VocabularyModel(terminfos);
      ByteBuffer plBuffer = ByteBuffer.wrap(iir.readNBytes(model.plLength * 2 * Integer.BYTES));
      IntBuffer pl = ByteBuffer.wrap(plBuffer.array()).asIntBuffer();

      // EVAL UPPER BOUND
      double upperbound = -1;
      while (pl.position() < pl.capacity()) {
        long doclen = dTable.doctable.get(pl.get()).doclen;
        int tf = pl.get();
        double score = ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / dTable.avgDocLen)) + tf)
            * Math.log10((double) dTable.ndocs / (double) model.plLength));
        upperbound = score > upperbound ? score : upperbound;
      }

      // WRITE POSTING LIST INTO TMP FILE WITH UPPER BOUND AS INITIAL VALUE
      ByteBuffer ubBuffer = ByteBuffer.allocate(Double.BYTES).putDouble(upperbound);
      iiw.write(ubBuffer.array());
      iiw.write(plBuffer.array());

      // UPDATE TMP LEXICON
      lw.write(String.format("%s\t%d-%d\n", model.term, currentByte, model.plLength));
      currentByte += (Double.BYTES + (2 * Integer.BYTES * model.plLength));
    }

    // CLOSE STREAMS
    lr.close();
    iir.close();
    lw.close();
    iiw.close();

    // DELETE OLD FILES
    while (!lexicon.delete());
    while (!invindex.delete());

    // RENAME TMPS
    File dst = Paths.get(workingDirectory.toString(), "lexicon.dat").toFile();
    while (!tmp_lexicon.renameTo(dst));
    dst = Paths.get(workingDirectory.toString(), "inverted_index.dat").toFile();
    while (!tmp_invindex.renameTo(dst));
  }

  /**
   * Builds the inverted index by first creating the sorted chunks of the collection, then merging them in a
   * merge-sort-like fashion; it will allow the creation in debug mode which will create debug files containing the core
   * informations of each chunk of files
   */
  private static void buildIndex() {
    InputStreamReader isr = null;
    BufferedReader inreader = null;
    TarArchiveInputStream tais = null;
    GZIPInputStream gis = null;
    long before = System.currentTimeMillis() / 1000;
    try {
      // SELECT THE CORRECT FILESTREAM TO USE BASED ON USER'S SELECTION
      if (inputFile.matches(".*\\.tar\\.gz")) {
        //.tar.gz archieve
        tais = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(new File(inputFile))));
        tais.getNextEntry();
        isr = new InputStreamReader(tais);
      } else if (inputFile.matches(".*\\.gz")) {
        //.gz
        gis = new GZIPInputStream(new FileInputStream(new File(inputFile)));
        isr = new InputStreamReader(gis);
      } else if (inputFile.matches(".*\\.tar")) {
        // .tar
        tais = new TarArchiveInputStream(new FileInputStream(new File(inputFile)));
        tais.getNextEntry();
        isr = new InputStreamReader(tais);
      } else {
        isr = new InputStreamReader(new FileInputStream(new File(inputFile)));
      }
      inreader = new BufferedReader(isr);
      while (!inreader.ready());

      // BUILD CHUNKS
      ConsoleUX.DebugLog(ConsoleUX.CLS + "Processing File...");
      String document;
      IndexBuilder vb = new IndexBuilder(stdin, stopnostem_mode);
      while ((document = inreader.readLine()) != null) {
        vb.addDocument(document);
      }
      vb.write_chunk();
      vb.closeDocTable();

      // MERGE CHUNKS
      int nchunks = vb.getNChunks();
      ConsoleUX.DebugLog("Merging " + nchunks + " Chunks...");
      boolean remainingChunk = false;
      for (int windowsize = nchunks; windowsize > 0; windowsize = ~~(windowsize / 2)) {
        // reset the chunkID to 0
        int assignIndex = 0;
        // windowsize will be the previous windowsize/2 + the eventual odd chunk if windowsize was odd
        windowsize = remainingChunk ? windowsize + 1 : windowsize;
        if (windowsize == 1)
          break; // we have a single chunk which means we don't need to merge anymore
        for (int left = 0; left < windowsize; left += 2) {
          // if left == right we will just rename the chunk and bring it to the next merge iteration
          int right = Math.min(left + 1, windowsize - 1);
          // merges the next two chunks into chunkid assignindex
          vb.merge(left, right, assignIndex);
          // increase the chunkID
          assignIndex++;
        }
        // calculating if there was a remaining chunk that we need to consider in the next iteration
        remainingChunk = ((windowsize % 2) != 0);
      }

      // RENAME LAST CHUNK TO REMOVE THE _0
      String OUTPUT_LOCATION = stopnostem_mode ? Constants.UNFILTERED_INDEX.toString()
          : Constants.OUTPUT_DIR.toString();
      File lastchunk = Paths.get(OUTPUT_LOCATION, "inverted_index_0.dat").toFile();
      if (!lastchunk.exists()) {
        ConsoleUX
            .ErrorLog("Unexpected error in the merging phase: " + lastchunk.toString() + " should exist but doesn't");
      }
      File finalName = Paths.get(OUTPUT_LOCATION, "inverted_index.dat").toFile();
      if (finalName.exists())
        finalName.delete();
      while (!lastchunk.renameTo(finalName));
      lastchunk = Paths.get(OUTPUT_LOCATION, "lexicon_0.dat").toFile();
      if (!lastchunk.exists()) {
        ConsoleUX
            .ErrorLog("Unexpected error in the merging phase: " + lastchunk.toString() + " should exist but doesn't");
      }
      finalName = Paths.get(OUTPUT_LOCATION, "lexicon.dat").toFile();
      if (finalName.exists())
        finalName.delete();
      while (!lastchunk.renameTo(finalName));
      ConsoleUX.SuccessLog("Merged " + nchunks + " Chunks");
      vb.reset();
      // endof merge

      // UPDATE INVERTED INDEX WITH TERMS UPPER BOUNDS
      ConsoleUX.DebugLog("Calculating Upper Bounds...");
      saveUpperBounds();
      ConsoleUX.SuccessLog("Index Building Completed. Took: " + ((System.currentTimeMillis() / 1000) - before) + "s");
      ConsoleUX.pause(true, stdin);

    } catch (IOException e) {
      ConsoleUX.ErrorLog("Unable to create index for " + inputFile + ":\n" + e.getMessage());
      ConsoleUX.pause(false, stdin);
    } finally {
      try {
        if (isr != null) {
          isr.close();
        }
        if (inreader != null) {
          inreader.close();
        }
        if (tais != null) {
          tais.close();
        }
        if (gis != null) {
          gis.close();
        }
      } catch (IOException e) {
        ConsoleUX.ErrorLog("Unable to close file:\n" + e.getMessage());
        ConsoleUX.pause(false, stdin);
      }
    }
  }

  private static void compressIndex() throws IOException {
    FileInputStream iir = null;
    try {
      // LOAD LEXICON
      Vocabulary lexicon = Vocabulary.loadVocabulary(stopnostem_mode);
      DocTable docTable = DocTable.loadDocTable(stopnostem_mode);

      // OPEN INPUT INVERTED INDEX
      String InputLocation = stopnostem_mode ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();
      File invindex = Paths.get(InputLocation, "inverted_index.dat").toFile();
      if (!invindex.exists())
        throw new IOException(stopnostem_mode ? "Unfiltered" : "Filtered" + " Inverted Index file doesn't exist");
      iir = new FileInputStream(invindex);

      // OPEN OUTPUT FILES
      File outIndex = Paths.get(Constants.COMPRESSED_INDEX.toString(), "inverted_index.dat").toFile();
      if (outIndex.exists()) {
        ConsoleUX.ErrorLog("inverted index already exists, operate in overwrite mode? [Y/n]", "");
        String answer = stdin.nextLine();
        if (answer.toLowerCase().equals("n") || answer.toLowerCase().equals("no"))
          throw new IOException("Aborted by the user");
        while (!outIndex.delete());
      }
      while (!outIndex.createNewFile());
      File outLexicon = Paths.get(Constants.COMPRESSED_INDEX.toString(), "lexicon.dat").toFile();
      if (outLexicon.exists()) {
        while (!outLexicon.delete());
      }
      while (!outLexicon.createNewFile());

      // START COMPRESSING POSTING LISTS READING LINE BY LINE AND COPYING IT INTO COMPRESSED_INDEX LOCATION
      for (String key : lexicon.vocabulary.keySet()) {
        long startByte = lexicon.vocabulary.get(key).startByte;
        PostingList pl = PostingList.openList(key, startByte, lexicon.vocabulary.get(key).plLength, stopnostem_mode);
        ByteBuffer compressedList = VariableByteEncoder.encodeList(pl.getBuffer());
      }

    } catch (IOException ioe) {
      ConsoleUX.ErrorLog("Compression Failed:\n" + ioe.getMessage());
      ConsoleUX.pause(true, stdin);
    } finally {
      if (iir != null) {
        iir.close();
      }
    }
  }

  /**
   * Completely clean the data/output directory from files
   */
  private static void cleanOutput() {
    // SELECT CORRECT OUTPUT LOCATION
    String OUTPUT_LOCATION = stopnostem_mode ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();

    // SELECT ALL FILES FILTERING OUT FOLDERS
    File outputfolder = new File(OUTPUT_LOCATION);
    File[] files = Arrays.asList(outputfolder.listFiles()).stream().filter((f) -> f.isFile()).toArray(File[]::new);

    // DELETE FILES
    for (File f : files) {
      f.delete();
    }
    ConsoleUX.SuccessLog("Cleaning complete.");
    ConsoleUX.pause(true, stdin);
  }

  public static void main(String[] args) throws IOException {
    // CREATE FILE SYSTEM FOR THE SEARCH ENGINE
    createFileSystem();

    // CREATE MENU INSTANCE
    Menu menu = new Menu(stdin, "Change Input File", "Build Index", "Compress Inverted Index", "Clean output",
        "Enable/Disable Filtering", "Exit");

    // KEEP ASKING FOR A NEW ACTION IN THE MENU UNTIL EXIT OPTION IS SELECTED
    int opt;
    while ((opt = menu.printMenu(ConsoleUX.FormatDebug("Selected File: ") + ConsoleUX.FormatSuccess(inputFile + "\n")
        + ConsoleUX.FormatDebug("Stopwords and Stemming filtering: ")
        + ConsoleUX.FormatSuccess(stopnostem_mode ? "disabled" : "enabled"))) != menu.exitOption) {

      if (opt == 0) {
        changeInputFile();
      } else if (opt == 1) {
        buildIndex();
      } else if (opt == 2) {
        compressIndex();
      } else if (opt == 3) {
        cleanOutput();
      } else if (opt == 4) {
        stopnostem_mode = !stopnostem_mode;
      }
    }
  }

  // HELPER FUNCTIONS

  /**
   * Helper function to safely create directories
   * 
   * @param f the path of the directory to create
   * @throws IOException
   */
  private static void safeCreateDir(File f) throws IOException {
    if (!f.exists()) {
      if (!f.mkdir()) {
        throw new IOException("Impossible to create the filesystem: " + f.toString() + " creation failed");
      }
    } else {
      if (!f.isDirectory()) {
        throw new IOException("Impossible to create the filesystem: " + f.toString() + " creation failed");
      }
    }
  }
}
