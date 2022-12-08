package unipi.mirs.components;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import unipi.mirs.utilities.Constants;

public class DocTable {
  //[0] is the docno , [1] the doclen
  public HashMap<Integer, Object[]> doctable = new HashMap<>();
  public int ndocs = 0;
  public double avgDocLen = 0;
  public boolean stopnostem;

  private DocTable() {}

  /**
   * Function loading a doctable from a given file
   * 
   * @param stopnostem weather to select or not the filtered doctable (doclen is different)
   * @return a DocTable instance with the correct instance parameters
   * @throws IOException
   */
  public static DocTable loadDocTable(boolean stopnostem) throws IOException {
    DocTable dTable = new DocTable();

    // SELECT THE CORRECT LOCATION FROM WHERE TO RETRIEVE THE DOCTABLE
    String OUTPUT_LOCATION = stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();
    File doctableFile = Paths.get(OUTPUT_LOCATION, "doctable.dat").toFile();
    if (!doctableFile.exists()) {
      throw new IOException("Unable to retrieve the vocabulary from the index's file system");
    }
    BufferedReader dbr = Files.newBufferedReader(doctableFile.toPath(), StandardCharsets.UTF_8);

    // LOOP OVER THE LINES CALCULATING AVG_DOC_LEN IN THE MEANWHILE
    String line;
    while ((line = dbr.readLine()) != null) {
      // count the number of documents in this doctable
      dTable.ndocs += 1;

      // split the components
      String[] parts = line.split("\t");
      int docid = Integer.parseInt(parts[0]);

      // update the average summation
      dTable.avgDocLen += Integer.parseInt(parts[1].split("-")[1]);
      if (dTable.doctable.containsKey(docid)) {
        throw new IOException("Malformed Document table");
      }

      // update the hashmap between docid and [docno, doclen]
      dTable.doctable.put(docid, getComponents(parts[1]));
    }
    // divide to get the avg_doc_len
    dTable.avgDocLen /= dTable.ndocs;

    // RETURN THE INSTANCE OF DOCTABLE
    return dTable;
  }

  // HELPER FUNCTIONS

  /**
   * Helper Function to get the components of the document as docno in [0] and doclen in [1]
   * 
   * @param line
   * @return
   */
  private static Object[] getComponents(String line) {
    String[] parts = line.split("-");
    return new Object[] { parts[0], Integer.parseInt(parts[1]) };
  }
}
