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
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.graphics.Menu;
import unipi.mirs.utilities.Constants;

public class IndexManager {

    private static String inputFile = Paths.get(Constants.INPUT_DIR.toString(), "collection.tsv").toString();
    private static boolean stopnostem_mode = false;
    private static final Scanner stdin = new Scanner(System.in);

    /**
     * Function creating the necessary folders for the application to work
     */
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
     * Computes the single term query over the collection to evaluate the terms upper bound then updates the inverted
     * index and the lexicon to have the new information correctly updated
     * 
     * @throws IOException
     */
    private static void save_upper_bounds() throws IOException {
        // LOAD DOCTABLE
        DocTable dTable = new DocTable(stopnostem_mode);
        Path workingDirectory = (!stopnostem_mode) ? Constants.OUTPUT_DIR : Constants.STOPNOSTEM_OUTPUT_DIR;
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
            String[] parts = terminfos.split("\t");
            String term = parts[0];
            int pllength = Integer.parseInt(parts[1].split("-")[1]);
            ByteBuffer plBuffer = ByteBuffer.wrap(iir.readNBytes(pllength * 2 * Integer.BYTES));
            IntBuffer pl = ByteBuffer.wrap(plBuffer.array()).asIntBuffer();

            // EVAL UPPER BOUND
            double upperbound = 0;
            while (pl.position() < pl.capacity()) {
                int doclen = ((int) dTable.doctable.get(pl.get())[1]);
                int tf = pl.get();
                upperbound += ((tf)
                        / (Constants.K_ONE * ((1 - Constants.B) + (Constants.B * doclen / dTable.avgDocLen)) + tf)
                        * Math.log10(dTable.ndocs / pllength));
            }

            // WRITE POSTING LIST INTO TMP FILE WITH UPPER BOUND AS INITIAL VALUE
            ByteBuffer ubBuffer = ByteBuffer.allocate(Double.BYTES).putDouble(upperbound);
            iiw.write(ubBuffer.array());
            iiw.write(plBuffer.array());

            // UPDATE TMP LEXICON
            lw.write(String.format("%s\t%d-%d\n", term, currentByte, pllength));
            currentByte += (Double.BYTES + (2 * Integer.BYTES * pllength));
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
                .filter((f) -> f.toString().matches(".*\\.gz$") || f.toString().matches(".*\\.tsv$")
                        || f.toString().matches(".*\\.tar$"))
                .map(f -> f.toString()).toArray(String[]::new);

        if (Arrays.asList(files).size() == 0) {
            ConsoleUX.ErrorLog("No files found, make sure to import a [.tsv; .gz; .tar.gz; .tar] file inside "
                    + Constants.INPUT_DIR + " folder");
            ConsoleUX.pause(true, stdin);
            inputFile = "";
            return;
        }
        Menu filesMenu = new Menu(stdin, files);
        inputFile = files[filesMenu.printMenu()];
    }

    /**
     * Builds the inverted index by first creating the sorted chunks of the collection, then merging them in a
     * merge-sort-like fashion; it will allow the creation in debug mode which will create debug files containing the
     * core informations of each chunk of files
     */
    private static void buildIndex() {
        InputStreamReader isr = null;
        BufferedReader inreader = null;
        long before = System.currentTimeMillis() / 1000;
        try {
            if (inputFile.matches(".*\\.tar\\.gz")) {
                //.tar.gz archieve
                TarArchiveInputStream tais = new TarArchiveInputStream(
                        new GzipCompressorInputStream(new FileInputStream(new File(inputFile))));
                tais.getNextEntry();
                isr = new InputStreamReader(tais);
            } else if (inputFile.matches(".*\\.gz")) {
                //.gz
                GZIPInputStream gis = new GZIPInputStream(new FileInputStream(new File(inputFile)));
                isr = new InputStreamReader(gis);
            } else if (inputFile.matches(".*\\.tar")) {
                // .tar
                TarArchiveInputStream tais = new TarArchiveInputStream(new FileInputStream(new File(inputFile)));
                tais.getNextEntry();
                isr = new InputStreamReader(tais);
            } else {
                isr = new InputStreamReader(new FileInputStream(new File(inputFile)));
            }
            inreader = new BufferedReader(isr);
            while (!inreader.ready());
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
            ConsoleUX.SuccessLog("Merged " + nchunks + " Chunks");
            vb.reset();
            // endof merge

            ConsoleUX.DebugLog("Calculating Upper Bounds...");
            save_upper_bounds();
            ConsoleUX.SuccessLog("Index Building Completed. Took: "+((System.currentTimeMillis()/1000)-before)+"s");
        } catch (IOException e) {
            ConsoleUX.ErrorLog("Unable to create index for " + inputFile + ":\n" + e.getMessage());
            ConsoleUX.pause(false, stdin);
        } finally {
            try {
                if(isr != null){
                    isr.close();
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
                "Enable/Disable Filtering", "Exit");
        int opt = 0;
        while ((opt = menu.printMenu(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Selected File: " + inputFile
                + "\nStopwords and Stemming filtering: " + ConsoleUX.FG_GREEN + ConsoleUX.BOLD
                + (stopnostem_mode ? "disabled" : "enabled") + ConsoleUX.RESET)) != menu.exitMenuOption()) {
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
