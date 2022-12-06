package unipi.mirs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import unipi.mirs.components.IndexBuilder;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.graphics.Menu;
import unipi.mirs.utilities.Constants;

public class IndexManager {

    private static String inputFile = Paths.get(Constants.INPUT_DIR.toString(), "collection.tsv").toString();
    private static boolean readCompressed = false;
    private static boolean stopnostem_mode = false;
    private static final Scanner stdin = new Scanner(System.in);

    private static void createFileSystem() {
        String pathString = Constants.WORKING_DIR.toString();
        File fileSystemCreator = new File(Paths.get(pathString, "data").toString());
        if (!fileSystemCreator.exists()) {
            fileSystemCreator.mkdir();
        }
        String datafolder = fileSystemCreator.toString();
        fileSystemCreator = new File(Paths.get(datafolder, "input").toString());
        if (!fileSystemCreator.exists()) {
            fileSystemCreator.mkdir();
        }
        fileSystemCreator = new File(Paths.get(fileSystemCreator.toString(), "queries").toString());
        if (!fileSystemCreator.exists()) {
            fileSystemCreator.mkdir();
        }
        fileSystemCreator = new File(Paths.get(datafolder, "output").toString());
        if (!fileSystemCreator.exists()) {
            fileSystemCreator.mkdir();
        }
        String outputfolder = fileSystemCreator.toString();
        fileSystemCreator = new File(Paths.get(outputfolder, "distributed").toString());
        if (!fileSystemCreator.exists()) {
            fileSystemCreator.mkdir();
        }
        fileSystemCreator = new File(Paths.get(outputfolder, "stopnostem").toString());
        if (!fileSystemCreator.exists()) {
            fileSystemCreator.mkdir();
        }
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
        File inputDir = new File(Constants.INPUT_DIR.toString());

        String[] files = Arrays.asList(inputDir.listFiles()).stream().filter((f) -> f.isFile())
                .filter((f) -> f.toString().matches(".*\\.gz$") || f.toString().matches(".*\\.tsv$"))
                .map(f -> f.toString()).toArray(String[]::new);

        if (Arrays.asList(files).size() == 0) {
            ConsoleUX.ErrorLog(
                    "No files found, make sure to import a .tsv or .gz file inside " + Constants.INPUT_DIR + " folder");
            ConsoleUX.pause(true, stdin);
            inputFile = "";
            return;
        }
        Menu filesMenu = new Menu(stdin, files);
        inputFile = files[filesMenu.printMenu()];
        if (inputFile.matches(".*\\.gz$"))
            readCompressed = true;
    }

    /**
     * Builds the inverted index by first creating the sorted chunks of the collection, then merging them in a
     * merge-sort-like fashion; it will allow the creation in debug mode which will create debug files containing the
     * core informations of each chunk of files
     */
    private static void buildIndex() {
        BufferedReader inreader = null;
        InputStreamReader isr = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(inputFile);
            if (readCompressed) {
                GZIPInputStream zippedInputStream = new GZIPInputStream(fis);
                isr = new InputStreamReader(zippedInputStream, StandardCharsets.UTF_8);
            } else {
                isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            }
            inreader = new BufferedReader(isr);
            ConsoleUX.DebugLog(ConsoleUX.CLS + "Processing File...");
            String document;
            IndexBuilder vb = new IndexBuilder(stdin, stopnostem_mode);
            while ((document = inreader.readLine()) != null) {
                vb.addDocument(document);
            }
            vb.write_chunk();
            vb.closeDocTable();
            int nchunks = vb.getNChunks();
            ConsoleUX.DebugLog("Merging " + nchunks + " Chunks...");
            boolean remainingChunk = false;
            //@formatter:off
            for (int windowsize = nchunks; windowsize > 0; windowsize = (int)Math.floor(windowsize/2)) {
                // reset the chunkID to 0
                int assignIndex = 0;
                // windowsize will be the previous windowsize/2 + the eventual odd chunk if windowsize was odd
                windowsize = remainingChunk ? windowsize+1 : windowsize;
                if(windowsize == 1) break; // we have a single chunk which means we don't need to merge anymore
                for (int left = 0; left < windowsize; left += 2) {
                    // if left == right we will just rename the chunk and bring it to the next merge iteration
                    int right = Math.min(left + 1, windowsize-1);
                    // merges the next two chunks into chunkid assignindex
                    vb.merge(left, right, assignIndex);
                    // increase the chunkID
                    assignIndex++;
                }
                // calculating if there was a remaining chunk that we need to consider in the next iteration
                remainingChunk = ((windowsize%2) != 0);
            }
            // removing _0 from last produced chunks
            String OUTPUT_LOCATION = stopnostem_mode ? Constants.STOPNOSTEM_OUTPUT_DIR.toString() : Constants.OUTPUT_DIR.toString();
            File lastchunk = new File(Paths.get(OUTPUT_LOCATION, "inverted_index_0.dat").toString());
            if(!lastchunk.exists()){
                ConsoleUX.ErrorLog("Unexpected error in the merging phase: "+lastchunk.toString()+" should exist but doesn't");
            }
            File finalName = new File(Paths.get(OUTPUT_LOCATION,"inverted_index.dat").toString());
            if(finalName.exists()) finalName.delete();
            while(!lastchunk.renameTo(finalName));

            lastchunk = new File(Paths.get(OUTPUT_LOCATION, "lexicon_0.dat").toString());
            if(!lastchunk.exists()){
                ConsoleUX.ErrorLog("Unexpected error in the merging phase: "+lastchunk.toString()+" should exist but doesn't");
            }
            finalName = new File(Paths.get(OUTPUT_LOCATION, "lexicon.dat").toString());
            if(finalName.exists()) finalName.delete();
            while(!lastchunk.renameTo(finalName));
            // endof merge

            ConsoleUX.SuccessLog("Merged " + nchunks + " Chunks");
            ConsoleUX.pause(true, stdin);
        } catch (IOException e) {
            ConsoleUX.ErrorLog("Unable to create index for " + inputFile + ":\n" + e.getMessage());
            ConsoleUX.pause(false, stdin);
        } finally {
            try {
                if(isr != null){
                    isr.close();
                }
                if(fis != null){
                    fis.close();
                }
                if(inreader != null){
                    inreader.close();
                }
            } catch (IOException e) {
                ConsoleUX.ErrorLog("Unable to close file:\n" + e.getMessage());
                ConsoleUX.pause(false, stdin);
            }
        }
    }

    /**
     * Completely clean the data/output directory from files
     */
    private static void cleanOutput(){
        String OUTPUT_LOCATION = stopnostem_mode ? Constants.STOPNOSTEM_OUTPUT_DIR.toString() : Constants.OUTPUT_DIR.toString();
        File outputfolder = new File(OUTPUT_LOCATION);
        //@formatter:on
        File[] files = Arrays.asList(outputfolder.listFiles()).stream().filter((f) -> f.isFile()).toArray(File[]::new);
        for (File f : files) {
            f.delete();
        }
        ConsoleUX.SuccessLog("Cleaning complete.");
        ConsoleUX.pause(true, stdin);
    }

    public static void main(String[] args) throws IOException {
        createFileSystem();
        // Save index in a file, compressed index in another one to avoid re-building index every time
        Menu menu = new Menu(stdin, "Change Input File", "Build Index", "Compress Inverted Index", "Clean output",
                "Change Mode", "Exit");
        int opt = 0;
        while ((opt = menu.printMenu(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Selected File: " + inputFile
                + "\nSelected Modes: Stopwords[" + (stopnostem_mode ? "enabled] - " : "disabled] - ") + "Stemming["
                + (stopnostem_mode ? "disabled]" : "enabled]") + ConsoleUX.RESET)) != menu.exitMenuOption()) {
            if (opt == 0) {
                changeInputFile();
            } else if (opt == 1) {
                buildIndex();
            } else if (opt == 2) {
                // Compression of the inverted index
            } else if (opt == 3) {
                cleanOutput();
            } else if (opt == 4) {
                stopnostem_mode = !stopnostem_mode;
            }
        }
    }
}
