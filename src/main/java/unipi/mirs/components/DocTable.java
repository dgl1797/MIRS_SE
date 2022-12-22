package unipi.mirs.components;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import unipi.mirs.models.DocTableModel;
import unipi.mirs.utilities.Constants;

public class DocTable {

  public HashMap<Integer, DocTableModel> doctable = new HashMap<>();
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

      // parse line
      DocTableModel model = new DocTableModel(line);

      // update the average summation
      dTable.avgDocLen += model.doclen;
      if (dTable.doctable.containsKey(model.docid)) {
        throw new IOException("Malformed Document table");
      }

      // update the hashmap between docid and [docno, doclen]
      dTable.doctable.put(model.docid, model);
    }
    // divide to get the avg_doc_len
    dTable.avgDocLen /= dTable.ndocs;

    // RETURN THE INSTANCE OF DOCTABLE
    return dTable;
  }
}
