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

public class Vocabulary {
  public HashMap<String, int[]> vocabulary = new HashMap<String, int[]>();

  public Vocabulary() {}

  private int[] getComponents(String line) {
    String[] parts = line.split("-");
    return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
  }

  public void loadVocabulary() throws IOException {
    Path vocabularyPath = Paths.get(Constants.OUTPUT_DIR.toString(), "lexicon.dat");
    File vocabularyFile = new File(vocabularyPath.toString());
    if (!vocabularyFile.exists()) {
      throw new IOException("Unable to retrieve the vocabulary from the index's file system");
    }
    BufferedReader vbr = Files.newBufferedReader(vocabularyPath, StandardCharsets.UTF_8);
    String line;
    while ((line = vbr.readLine()) != null) {
      String[] parts = line.split("\t");
      if (vocabulary.containsKey(parts[0])) {
        throw new IOException("Malformed Vocabulary");
      }
      vocabulary.put(parts[0], getComponents(parts[1]));
    }
  }
}
