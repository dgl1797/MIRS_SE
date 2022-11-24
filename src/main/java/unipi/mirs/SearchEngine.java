package unipi.mirs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import unipi.mirs.components.IndexBuilder;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.graphics.Menu;
import unipi.mirs.utilities.Constants;

public class SearchEngine {

    private static String inputFile = Paths.get(Constants.INPUT_DIR.toString(), "collection.tsv").toString();
    private static boolean readCompressed = false;
    private static final Scanner stdin = new Scanner(System.in);

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
            System.out.println(
                    ConsoleUX.FG_RED + ConsoleUX.BOLD + "No files found, make sure to import a .tsv or .gz file inside "
                            + Constants.INPUT_DIR + " folder" + ConsoleUX.RESET);
            inputFile = "";
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
        if (readCompressed) {

        }
        System.out.println(ConsoleUX.CLS + ConsoleUX.BOLD + ConsoleUX.FG_BLUE + "Processing File..." + ConsoleUX.RESET);
        try (BufferedReader inreader = Files.newBufferedReader(Path.of(inputFile), StandardCharsets.UTF_8)) {
            String document;
            IndexBuilder vb = new IndexBuilder(stdin);
            while ((document = inreader.readLine()) != null) {
                vb.addDocument(document);
            }
            vb.write_chunk();
            vb.closeDocTable();
            System.out.println(ConsoleUX.FG_GREEN + ConsoleUX.BOLD + "Index Builded succesfully.");
            ConsoleUX.pause(true, stdin);
            int nchunks = vb.getNChunks();
            System.out.println(
                    ConsoleUX.BOLD + ConsoleUX.FG_BLUE + "Merging " + nchunks + " Chunks..." + ConsoleUX.RESET);
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
            System.out.println(ConsoleUX.BOLD + ConsoleUX.FG_GREEN + "Merged " + nchunks + " Chunks" + ConsoleUX.RESET);
            ConsoleUX.pause(true, stdin);
        } catch (IOException e) {
            System.out.println(ConsoleUX.FG_RED + ConsoleUX.BOLD + "Unable to create index for " + inputFile + ":\n"
                    + e.getMessage() + ConsoleUX.RESET);
            ConsoleUX.pause(false, stdin);
        }
    }

    /**
     * Completely clean the data/output directory from files
     */
    private static void cleanOutput(){
        File outputfolder = new File(Constants.OUTPUT_DIR.toString());
        //@formatter:on
        File[] files = Arrays.asList(outputfolder.listFiles()).stream().filter((f) -> f.isFile()).toArray(File[]::new);
        for (File f : files) {
            f.delete();
        }
        System.out.println(ConsoleUX.CLS + ConsoleUX.FG_GREEN + ConsoleUX.BOLD + "Cleaning complete.");
        ConsoleUX.pause(true, stdin);
    }

    public static void main(String[] args) throws IOException {
        // Save index in a file, compressed index in another one to avoid re-building index every time
        Menu menu = new Menu(stdin, "Change Input File", "Build Index", "Compress Inverted Index", "Clean output",
                "Exit");
        int opt = 0;
        while ((opt = menu.printMenu(
                ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Selected File: " + inputFile + ConsoleUX.RESET)) != menu
                        .exitMenuOption()) {
            if (opt == 0) {
                changeInputFile();
            } else if (opt == 1) {
                buildIndex();
            } else if (opt == 2) {
                // Compression of the inverted index
            } else if (opt == 3) {
                cleanOutput();
            }
        }
        System.out.println(ConsoleUX.CLS + ConsoleUX.FG_YELLOW + ConsoleUX.BOLD
                + "UI NID TU EGZIT, UOT MATTEMMATTICCALLI DU UI DU? UI COMMPLITLLY FORMÀT D COMPIUTTER, UAAI?");
        System.out.println(
                "DU UI LAIK TU UEIST SPEIS? DU UI LAIK TU UEIST TIME? 0,0001 milliseconds to exit" + ConsoleUX.RESET);
        System.exit(0);
    }
}
