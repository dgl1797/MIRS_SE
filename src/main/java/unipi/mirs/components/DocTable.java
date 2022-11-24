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
  public HashMap<Integer, Object[]> doctable = new HashMap<>();
  public int ndocs = 0;

  public DocTable() {}

  private Object[] getComponents(String line) {
    String[] parts = line.split("-");
    return new Object[] { parts[0], Integer.parseInt(parts[1]) };
  }

  public void loadDocTable() throws IOException {
    Path doctablePath = Paths.get(Constants.OUTPUT_DIR.toString(), "doctable.dat");
    File doctableFile = new File(doctablePath.toString());
    if (!doctableFile.exists()) {
      throw new IOException("Unable to retrieve the vocabulary from the index's file system");
    }
    BufferedReader dbr = Files.newBufferedReader(doctablePath, StandardCharsets.UTF_8);
    String line;
    while ((line = dbr.readLine()) != null) {
      this.ndocs += 1;
      String[] parts = line.split("\t");
      int docid = Integer.parseInt(parts[0]);
      if (doctable.containsKey(docid)) {
        throw new IOException("Malformed Document table");
      }
      doctable.put(docid, getComponents(parts[1]));
    }
  }

}
