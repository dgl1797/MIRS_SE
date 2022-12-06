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
  public HashMap<String, Long[]> vocabulary = new HashMap<String, Long[]>();
  public boolean stopnostem;
  //the cell [0] is the docid , into the cell [1] there is the length
  public Vocabulary(boolean stopnostem_mode) {
     this.stopnostem = stopnostem_mode;
    }

  private Long[] getComponents(String line) {
    String[] parts = line.split("-");
    return new Long[] { Long.parseLong(parts[0]), Long.parseLong(parts[1]) };
  }

  public void loadVocabulary() throws IOException {
    String OUTPUT_LOCATION = stopnostem ? Constants.STOPNOSTEM_OUTPUT_DIR.toString() : Constants.OUTPUT_DIR.toString();
    Path vocabularyPath = Paths.get(OUTPUT_LOCATION, "lexicon.dat");
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
      
      /* 
      if(getComponents(parts[1])[1]>=1000000)
      {
        System.out.println(parts[0] + " - " + String.format("%,d", getComponents(parts[1])[1]));;
      }*/
      vocabulary.put(parts[0], getComponents(parts[1]));
    }
  }
}
