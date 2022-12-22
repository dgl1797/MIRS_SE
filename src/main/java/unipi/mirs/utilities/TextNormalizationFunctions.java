package unipi.mirs.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import opennlp.tools.stemmer.PorterStemmer;

public class TextNormalizationFunctions {
  private TextNormalizationFunctions() {}

  // initialized as static final to not re-instantiate it at every stemming
  public static final PorterStemmer ps = new PorterStemmer();

  /**
   * applies a regex to clean text and normalizes it
   * 
   * @param txt text to be normalized
   * @return normalized text string
   */
  public static String cleanText(String txt) {
    // @formatter:off
    return txt.toLowerCase()
    // removes the char+PAD+char sequences used for special utf-8 combinations
        .replaceAll(".\\u0080.", " ")
    // removes all extra-characters keeping only digits and utf-8 alphabetical characters
        .replaceAll("[^\\p{L}\\w\\s]+", " ")
    // trims all extra spaces into one
        .replaceAll("[\\s]+", " ").trim();
    // @formatter:on
  }

  /**
   * loads the stopwords from a stopwords.txt file located in the input directory
   * 
   * @return
   * @throws IOException
   */
  public static HashSet<String> load_stopwords() throws IOException {
    HashSet<String> stopwords = new HashSet<>();
    Path swPath = Paths.get(Constants.INPUT_DIR.toString(), "stopwords.txt");
    try (BufferedReader infile = Files.newBufferedReader(swPath, StandardCharsets.UTF_8)) {
      String stopword;
      while ((stopword = infile.readLine()) != null) {
        stopwords.add(stopword);
      }
    } catch (Exception e) {
      throw new IOException("Failed to load stopwords file from: " + swPath.toString() + ":\n" + e.getMessage());
    }
    return stopwords;
  }

}
