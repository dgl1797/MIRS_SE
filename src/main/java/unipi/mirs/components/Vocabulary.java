package unipi.mirs.components;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import unipi.mirs.models.VocabularyModel;
import unipi.mirs.utilities.Constants;

public class Vocabulary {

  public HashMap<String, VocabularyModel> vocabulary = new HashMap<>();
  public boolean stopnostem;

  private Vocabulary() {}

  /**
   * Initializes a Vocabulary instance from the filtered/unfiltered index basing on the selected mode
   * 
   * @param stopnostem boolean stating wheather or not the filtering is enabled or not
   * @return the instance of the vocabulary
   * @throws IOException
   */
  public static Vocabulary loadVocabulary(boolean stopnostem, boolean compressed) throws IOException {
    Vocabulary lexicon = new Vocabulary();

    // SELECT THE CORRECT LOCATION FROM WHERE TO RETRIEVE THE LEXICON
    String OUTPUT_LOCATION = stopnostem ? Constants.UNFILTERED_INDEX.toString() : Constants.OUTPUT_DIR.toString();
    if (compressed) {
      OUTPUT_LOCATION = Paths.get(OUTPUT_LOCATION, "compressed_index").toString();
    }
    File vocabularyFile = Paths.get(OUTPUT_LOCATION, "lexicon.dat").toFile();
    if (!vocabularyFile.exists()) {
      throw new IOException("Unable to retrieve the vocabulary from the index's file system");
    }
    BufferedReader vbr = Files.newBufferedReader(vocabularyFile.toPath(), StandardCharsets.UTF_8);

    // FILL THE VOCABULARY LINE BY LINE
    String line;
    while ((line = vbr.readLine()) != null) {
      // parse line
      VocabularyModel model = new VocabularyModel(line, compressed);

      if (lexicon.vocabulary.containsKey(model.term)) {
        throw new IOException("Malformed Vocabulary");
      }
      lexicon.vocabulary.put(model.term, model);
    }

    // RETURN THE FILLED INSTANCE OF VOCABULARY
    return lexicon;
  }
}
