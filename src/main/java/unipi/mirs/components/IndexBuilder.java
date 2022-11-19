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

  private HashSet<String> stopwords;
  // int[i] = starting position for chunkID i
  private TreeMap<String, int[]> lexicon;
  // int[0] is the docid and int[1] is the term frequency
  private TreeMap<String, ArrayList<int[]>> chunk;

  private boolean debugmode = false;
  private boolean gotoNextChunk = false;
  private int nchunks;
  private int chunksize;
  private int remainingdocs;
  private int currentDocID = 0;
  private int currentChunkID = 0;
  private BufferedWriter doctable;

  private void write_doctable(String docno, int docid, int doclen) throws IOException {
    // if i'm at chunkid 0, it means i have still nothing added
    // otherwise it means that i added currentChunkID documents + the entire chunks, but at most i can add remainingdocs documents
    int realDocid = docid + Math.min(currentChunkID, remainingdocs) + (this.chunksize * currentChunkID);
    String doctcontent = String.format("%s\t%d %d\n", docno, realDocid, doclen);
    this.doctable.write(doctcontent);
  }

  private void reset() {
    this.chunk.clear();
    // at last reset currentChunkID will be the size of the number of chunks
    currentChunkID += 1;
    // to free space in the postings list we can reset docid to 0, we still will maintain the docid by calculating the offset:
    // docid+(chunksize*chunkID)
    currentDocID = 0;
  }

  public IndexBuilder(Scanner s, int nchunks, int collectionSize) throws IOException {
    this.stdin = s;
    this.nchunks = nchunks;
    this.chunksize = Math.floorDiv(collectionSize, nchunks);
    // remainingdocs will be used to add a document to each chunk untill it will go to 0
    this.remainingdocs = collectionSize % nchunks;
    System.out
        .println(ConsoleUX.FG_GREEN + ConsoleUX.BOLD + chunksize + " with " + remainingdocs + " documents remaining");
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
    this.lexicon = new TreeMap<>();
    this.chunk = new TreeMap<>();
  }

  public void closeDocTable() throws IOException {
    this.doctable.close();
  }

  public void addDocument(String document) throws IOException {
    String[] parts = document.split("\t");
    String docno = parts[0];
    String docbody = parts[1];
    docbody = TextNormalizationFunctions.cleanText(docbody);
    int doclen = 0;
    // currentDocID = docid; currentChunkID = chunkID
    for (String t : docbody.split(" ")) {
      if (!stopwords.contains(t)) {
        t = TextNormalizationFunctions.ps.stem(t);
        doclen++;
        // first we check if the term was already globally inserted:
        if (lexicon.containsKey(t)) {
          // if it is present in the global lexicon it might still not be present in the current chunk
          if (!chunk.containsKey(t)) {
            // if it is the case, we must initialize the term in the chunk with currentDocid set to 1 occurrency of term t
            chunk.put(t, new ArrayList<>(Arrays.asList(new int[] { currentDocID, 1 })));
          } else {
            // if we are here surely the Arraylist of chunk[t] is not empty, but we need to check if in last position there is
            // currentDocID
            if (chunk.get(t).get(chunk.get(t).size() - 1)[0] == currentDocID) {
              // if it is we need to increment the number of occurrencies for t in the chunk
              chunk.get(t).get(chunk.get(t).size() - 1)[1] += 1;
            } else {
              // if it is not we need to push it into the list with 1 instance of term t
              chunk.get(t).add(new int[] { currentDocID, 1 });
            }
          }
        } else {
          // automatically initializes all chunks to have value 0, which means absent
          lexicon.put(t, new int[nchunks]);
          // if it wasn't it means it is also the first time the term appears in the current chunk
          chunk.put(t, new ArrayList<>(Arrays.asList(new int[] { currentDocID, 1 })));
        }
      }
    }
    // document parsed, we can go to the next docID
    currentDocID += 1;
    if (currentDocID >= chunksize) {
      // if we didn't add a document from the remaining ones yet, we skip:
      if ((remainingdocs - Math.min(currentChunkID, remainingdocs)) != 0 && !gotoNextChunk) {
        this.gotoNextChunk = true;
        return;
      } else {
        // there either gotoNextChunk has been set to true or remainingdocs has arrived to 0
        if ((remainingdocs - Math.min(currentChunkID, remainingdocs)) != 0)
          gotoNextChunk = false; // if we still didn't consume all remaining docs we reset gotoNextChunk

        // in any case we need to reset and write the chunk to disk now and reset the data structures
        System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Writing chunk " + currentChunkID + " to file...");
        write_chunk();
        System.out.println(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Wrote " + currentDocID + " documents to file.");
        reset();
      }
    }
    // updates the doctable
    write_doctable(docno, currentDocID - 1, doclen);
  }

  public void write_chunk() throws IOException {
    if (currentChunkID == nchunks)
      return;
    String chunkinvertedindexname = String.format("%s%d.dat", "inverted_index_", currentChunkID);
    String chunkdebugname = String.format("%s%d.dbg", "debug_", currentChunkID);
    Path chunkinvertedindexPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkinvertedindexname);
    Path chunkdebugPath = Paths.get(Constants.OUTPUT_DIR.toString(), chunkdebugname);
    File chunkinvertedindex = new File(chunkinvertedindexPath.toString());
    File chunkdebug = new File(chunkdebugPath.toString());
    if (chunkinvertedindex.exists()) {
      chunkinvertedindex.delete();
    }
    if (chunkdebug.exists() && debugmode) {
      chunkdebug.delete();
    }
    chunkinvertedindex.createNewFile();
    if (debugmode)
      chunkdebug.createNewFile();
    FileOutputStream iiw = new FileOutputStream(chunkinvertedindex);
    BufferedWriter dbgw = null;
    if (debugmode) {
      dbgw = new BufferedWriter(new FileWriter(chunkdebug));
    }
    // 0 means the chunk is absent hence we start counting by 1
    int currentByte = 1;
    for (Entry<String, ArrayList<int[]>> en : chunk.entrySet()) {
      int startByte = currentByte;
      lexicon.get(en.getKey())[currentChunkID] = startByte;
      int plLength = en.getValue().size();
      if (debugmode) {
        dbgw.write(en.getKey() + "\t");
        dbgw.write("size: " + plLength + " -> ");
      }
      // COMPRESSION:
      //   in each chunk we cannot have more than chunksize+1 documents(we might at most add 1 document in each chunk) 
      //    int binary_chunksize = (int) Math.ceil(Math.log(chunksize+1) / Math.log(2));
      //    int binary_frqsize = (int) Math.ceil(Math.log(maxfrequency) / Math.log(2));
      //   plLength will at most be chunksize+1 in case a term appears in every single document in the chunk => binary_chunksize
      //    ByteBuffer b = ByteBuffer.allocate(((binary_chunksize + binary_frqsize) * plLength) + binary_chunksize)
      //        .putInt(plLength);
      ByteBuffer b = ByteBuffer.allocate((2 * Integer.BYTES * plLength) + Integer.BYTES).putInt(plLength);
      for (int[] plnode : en.getValue()) {
        b = b.putInt(plnode[0]).putInt(plnode[1]);
        if (debugmode) {
          dbgw.write(String.format("%d:%d - ", plnode[0], plnode[1]));
        }
        // currentByte += (docsize+frqsize)
        currentByte += 2 * Integer.BYTES;
      }
      iiw.write(b.array());
      if (debugmode) {
        dbgw.write("\n");
      }
    }
    iiw.close();
    if (debugmode) {
      dbgw.close();
    }
  }

  public void save_lexicon() throws IOException {
    File lf = new File(Paths.get(Constants.OUTPUT_DIR.toString(), "vocabulary.dat").toString());
    if (lf.exists()) {
      lf.delete();
    }
    lf.createNewFile();
    BufferedWriter lfw = new BufferedWriter(new FileWriter(lf));
    for (Entry<String, int[]> en : lexicon.entrySet()) {
      String vocline = String.format("%s\t", en.getKey());
      for (int i = 0; i < nchunks; i++) {
        vocline += ((i == nchunks - 1) ? String.format("%d", en.getValue()[i])
            : String.format("%d-", en.getValue()[i]));
      }
      lfw.write(vocline + "\n");
    }
    lfw.close();
  }
}
