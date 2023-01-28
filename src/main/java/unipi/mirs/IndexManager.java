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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import unipi.mirs.components.CompressedPostingList;
import unipi.mirs.components.DocTable;
import unipi.mirs.components.IndexBuilder;
import unipi.mirs.components.PostingList;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.graphics.Menu;
import unipi.mirs.models.VocabularyModel;
import unipi.mirs.utilities.Constants;

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

    // CREATE UNFILTERED_INDEX FOLDER INSIDE OUTPUT FOLDER
    fileSystemCreator = Constants.UNFILTERED_INDEX.toFile();
    safeCreateDir(fileSystemCreator);

    // CREATE COMPRESSED_INDEX FOLDER INSIDE OUTPUT FOLDER
    fileSystemCreator = Paths.get(Constants.OUTPUT_DIR.toString(), "compressed_index").toFile();
    safeCreateDir(fileSystemCreator);

    // CREATE COMPRESSED_INDEX FOLDER INSIDE UNFILTERED_INDEX FOLDER
    fileSystemCreator = Paths.get(Constants.UNFILTERED_INDEX.toString(), "compressed_index").toFile();
    safeCreateDir(fileSystemCreator);

    // CREATE OUTPUT FOLDER FOR FILE BASED QUERIES
    fileSystemCreator = Paths.get(Constants.OUTPUT_DIR.toString(), "queries").toFile();
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
    File docids = Paths.get(workingDirectory.toString(), "docids.dat").toFile();
    File frequencies = Paths.get(workingDirectory.toString(), "frequencies.dat").toFile();
    if (!lexicon.exists())
      throw new IOException("Impossible to proceed, lexicon file doesn't exist");
    if (!docids.exists())
      throw new IOException("Impossible to proceed, inverted documents file doesn't exist");
    if (!frequencies.exists())
      throw new IOException("Impossible to proceed, frequencies index file doesn't exist");

    // OPENING LEXICON AND INVERTED INDEX
    BufferedReader lr = new BufferedReader(new FileReader(lexicon));
    FileInputStream didr = new FileInputStream(docids);
    FileInputStream frqr = new FileInputStream(frequencies);

    // CREATE TMP FILES WHERE TO SAVE NEW INFOS
    File tmp_lexicon = Paths.get(workingDirectory.toString(), "lexicon_tmp.dat").toFile();
    File tmp_docids = Paths.get(workingDirectory.toString(), "docids_tmp.dat").toFile();
    File tmp_frequencies = Paths.get(workingDirectory.toString(), "frequencies_tmp.dat").toFile();

    if (tmp_lexicon.exists())
      while (!tmp_lexicon.delete());
    if (tmp_docids.exists())
      while (!tmp_docids.delete());
    if (tmp_frequencies.exists())
      while (!tmp_frequencies.delete());
    while (!tmp_lexicon.createNewFile());
    while (!tmp_docids.createNewFile());
    while (!tmp_frequencies.createNewFile());

    BufferedWriter lw = new BufferedWriter(new FileWriter(tmp_lexicon));
    FileOutputStream didw = new FileOutputStream(tmp_docids);
    FileOutputStream frqw = new FileOutputStream(tmp_frequencies);

    // WRITE NEW INFO INTO TMPS
    String terminfos;
    long dcurrentByte = 0;
    long fcurrentByte = 0;
    while ((terminfos = lr.readLine()) != null) {
      VocabularyModel model = new VocabularyModel(terminfos, false);

      // READ POSTING LIST INTO INT_BUFFER
      ByteBuffer didBuffer = ByteBuffer.wrap(didr.readNBytes(model.plLength * Integer.BYTES));
      IntBuffer did = ByteBuffer.wrap(didBuffer.array()).asIntBuffer();

      ByteBuffer frqBuffer = ByteBuffer.wrap(frqr.readNBytes(model.plLength * Integer.BYTES));
      IntBuffer frq = ByteBuffer.wrap(frqBuffer.array()).asIntBuffer();

      // EVAL UPPER BOUND
      double upperbound = -1;
      while (did.position() < did.capacity()) {
        long doclen = dTable.doctable.get(did.get()).doclen;
        int tf = frq.get();
        double score = ((tf) / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / dTable.avgDocLen)) + tf)
            * Math.log10((double) dTable.ndocs / (double) model.plLength));
        upperbound = score > upperbound ? score : upperbound;
      }

      // WRITE POSTING LIST INTO TMP FILE WITH UPPER BOUND AS INITIAL VALUE
      ByteBuffer ubBuffer = ByteBuffer.allocate(Double.BYTES).putDouble(upperbound);
      didw.write(ubBuffer.array());
      didw.write(didBuffer.array());
      // lexicon got reshuffled by hashmap so it is necessary to also rewrite the correct frequency buffer
      frqw.write(frqBuffer.array());

      // UPDATE TMP LEXICON
      lw.write(String.format("%s\t%d-%d-%d\n", model.term, dcurrentByte, model.plLength, fcurrentByte));
      dcurrentByte += (Double.BYTES + (Integer.BYTES * model.plLength));
      fcurrentByte += (Integer.BYTES * model.plLength);
    }

    // CLOSE STREAMS
    didr.close();
    lr.close();
    frqr.close();
    lw.close();
    didw.close();
    frqw.close();

    // DELETE OLD FILES
    while (!lexicon.delete());
    while (!docids.delete());
    while (!frequencies.delete());

    // RENAME TMPS
    File dst = Paths.get(workingDirectory.toString(), "lexicon.dat").toFile();
    while (!tmp_lexicon.renameTo(dst));
    dst = Paths.get(workingDirectory.toString(), "docids.dat").toFile();
    while (!tmp_docids.renameTo(dst));
    dst = Paths.get(workingDirectory.toString(), "frequencies.dat").toFile();
    while (!tmp_frequencies.renameTo(dst));
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
        //.tar
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
      File lastchunk_doc = Paths.get(OUTPUT_LOCATION, "docids_0.dat").toFile();
      File lastchunk_freq = Paths.get(OUTPUT_LOCATION, "frequencies_0.dat").toFile();
      if (!lastchunk_doc.exists()) {
        throw new IOException(
            "Unexpected error in the merging phase: " + lastchunk_doc.toString() + " should exist but doesn't");
      }
      if (!lastchunk_freq.exists()) {
        throw new IOException(
            "Unexpected error in the merging phase: " + lastchunk_freq.toString() + " should exist but doesn't");
      }
      File finalName_doc = Paths.get(OUTPUT_LOCATION, "docids.dat").toFile();
      File finalName_freq = Paths.get(OUTPUT_LOCATION, "frequencies.dat").toFile();
      if (finalName_doc.exists())
        finalName_doc.delete();
      while (!lastchunk_doc.renameTo(finalName_doc));
      if (finalName_freq.exists())
        finalName_freq.delete();
      while (!lastchunk_freq.renameTo(finalName_freq));
      File lastchunk = Paths.get(OUTPUT_LOCATION, "lexicon_0.dat").toFile();
      if (!lastchunk.exists()) {
        throw new IOException(
            "Unexpected error in the merging phase: " + lastchunk.toString() + " should exist but doesn't");
      }
      File finalName = Paths.get(OUTPUT_LOCATION, "lexicon.dat").toFile();
      if (finalName.exists())
        finalName.delete();
      while (!lastchunk.renameTo(finalName));
      ConsoleUX.SuccessLog("Merged " + nchunks + " Chunks");
      vb.reset();
      // END OF MERGE

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

  /**
   * Reads the index from the correct folder according to the set parameters and compresses it into a sub-folder called
   * "compressed_index"
   * 
   * @throws IOException
   */
  private static void compressIndex() throws IOException {
    FileInputStream didr = null;
    FileOutputStream didw = null;
    FileInputStream frqr = null;
    FileOutputStream frqw = null;
    BufferedWriter lw = null;
    try {
      ConsoleUX.DebugLog(ConsoleUX.CLS + "Loading index...");
      // LOAD LEXICON
      Vocabulary lexicon = Vocabulary.loadVocabulary(stopnostem_mode, false);

      // OPEN INPUT INVERTED INDEX
      String InputLocation = stopnostem_mode ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();
      // DOCS AND FREQUENCIES
      File docids = Paths.get(InputLocation, "docids.dat").toFile();
      File frequencies = Paths.get(InputLocation, "frequencies.dat").toFile();
      if (!docids.exists())
        throw new IOException(
            stopnostem_mode ? "Unfiltered" : "Filtered" + " Inverted Index file for docids doesn't exist");
      if (!frequencies.exists())
        throw new IOException(
            stopnostem_mode ? "Unfiltered" : "Filtered" + " Inverted Index file for frequencies doesn't exist");
      didr = new FileInputStream(docids);
      frqr = new FileInputStream(frequencies);

      // DOCTABLE
      File dtFile = Paths.get(InputLocation, "doctable.dat").toFile();
      if (!dtFile.exists())
        throw new IOException(stopnostem_mode ? "Unfiltered" : "Filtered" + " Inverted Index file doesn't exist");

      // OPEN OUTPUT FILES
      String OutputLocation = stopnostem_mode
          ? Paths.get(Constants.UNFILTERED_INDEX.toString(), "compressed_index").toString()
          : Paths.get(Constants.OUTPUT_DIR.toString(), "compressed_index").toString();
      File outdocids = Paths.get(OutputLocation, "docids.dat").toFile();
      File outfrequencies = Paths.get(OutputLocation, "frequencies.dat").toFile();

      // DOCIDS AND FREQUENCIES
      if (outdocids.exists()) {
        ConsoleUX.ErrorLog("inverted index already exists, operate in overwrite mode? [Y/n]", "");
        String answer = stdin.nextLine();
        if (answer.toLowerCase().equals("n") || answer.toLowerCase().equals("no"))
          throw new IOException("Aborted by the user");
        while (!outdocids.delete());
      }
      while (!outdocids.createNewFile());
      if (outfrequencies.exists()) {
        while (!outfrequencies.delete());
      }
      while (!outfrequencies.createNewFile());

      // LEXICON
      File outLexicon = Paths.get(OutputLocation, "lexicon.dat").toFile();
      if (outLexicon.exists()) {
        while (!outLexicon.delete());
      }
      while (!outLexicon.createNewFile());

      // DOCTABLE
      File outDT = Paths.get(OutputLocation, "doctable.dat").toFile();
      Files.copy(dtFile.toPath(), outDT.toPath(), StandardCopyOption.REPLACE_EXISTING);

      // Buffers initializations
      didw = new FileOutputStream(outdocids);
      frqw = new FileOutputStream(outfrequencies);
      lw = new BufferedWriter(new FileWriter(outLexicon, StandardCharsets.UTF_8));

      // START COMPRESSING POSTING LISTS READING LINE BY LINE AND COPYING IT INTO COMPRESSED_INDEX LOCATION
      ConsoleUX.DebugLog("Compressing Index into: " + OutputLocation);
      long dcurrentbyte = 0;
      long fcurrentbyte = 0;
      for (String key : lexicon.vocabulary.keySet()) {
        // load from files
        long dstartByte = lexicon.vocabulary.get(key).dstartByte;
        long fstartByte = lexicon.vocabulary.get(key).fstartByte;
        PostingList pl = PostingList.openList(key, dstartByte, fstartByte, lexicon.vocabulary.get(key).plLength,
            stopnostem_mode);

        // compressing the read posting list
        CompressedPostingList cpl = CompressedPostingList.from(pl);

        // writing the compressed data
        lw.write(String.format("%s\t%d-%d-%d-%d-%d\n", key, dcurrentbyte, pl.totalLength, fcurrentbyte,
            cpl.getDIDBuffer().capacity(), cpl.getFRQBuffer().capacity()));

        ByteBuffer ubb = ByteBuffer.allocate(Double.BYTES).putDouble(pl.upperBound);
        didw.write(ubb.array());
        didw.write(cpl.getDIDBuffer().array());
        frqw.write(cpl.getFRQBuffer().array());
        dcurrentbyte += cpl.getDIDBuffer().capacity() + Double.BYTES;
        fcurrentbyte += cpl.getFRQBuffer().capacity();
      }
      ConsoleUX.SuccessLog("Compression Successful");
      ConsoleUX.pause(true, stdin);
    } catch (IOException ioe) {
      ConsoleUX.ErrorLog("Compression Failed:\n" + ioe.getMessage());
      ConsoleUX.pause(true, stdin);
    } finally {
      if (didr != null) {
        didr.close();
      }
      if (didw != null) {
        didw.close();
      }
      if (frqr != null) {
        frqr.close();
      }
      if (frqw != null) {
        frqw.close();
      }
      if (lw != null) {
        lw.close();
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
