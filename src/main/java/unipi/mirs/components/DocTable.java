package unipi.mirs.components;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import unipi.mirs.utilities.Constants;

public class DocTable {
  //[0] is the docno , [1] the doclen
  public HashMap<Integer, Object[]> doctable = new HashMap<>();
  public int ndocs = 0;
  public double avgDocLen = 0;
  public boolean stopnostem;

  public DocTable(boolean stopnostem_mode) {
    this.stopnostem = stopnostem_mode;
  }

  private static Object[] getComponents(String line) {
    String[] parts = line.split("-");
    return new Object[] { parts[0], Integer.parseInt(parts[1]) };
  }

  public static DocTable loadDocTable(boolean stopnostem) throws IOException {
    DocTable dTable = new DocTable(stopnostem);
    String OUTPUT_LOCATION = stopnostem ? Constants.STOPNOSTEM_OUTPUT_DIR.toString() : Constants.OUTPUT_DIR.toString();
    Path doctablePath = Paths.get(OUTPUT_LOCATION, "doctable.dat");
    File doctableFile = new File(doctablePath.toString());
    if (!doctableFile.exists()) {
      throw new IOException("Unable to retrieve the vocabulary from the index's file system");
    }
    BufferedReader dbr = Files.newBufferedReader(doctablePath, StandardCharsets.UTF_8);
    String line;
    while ((line = dbr.readLine()) != null) {
      dTable.ndocs += 1;
      String[] parts = line.split("\t");
      int docid = Integer.parseInt(parts[0]);
      dTable.avgDocLen += Integer.parseInt(parts[1].split("-")[1]);
      if (dTable.doctable.containsKey(docid)) {
        throw new IOException("Malformed Document table");
      }
      dTable.doctable.put(docid, getComponents(parts[1]));
    }
    dTable.avgDocLen /= dTable.ndocs;
    return dTable;
  }

}
