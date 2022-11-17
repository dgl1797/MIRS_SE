package unipi.mirs.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import opennlp.tools.stemmer.PorterStemmer;

public final class TextNormalizationFunctions {
  private TextNormalizationFunctions() {}

  public static final PorterStemmer ps = new PorterStemmer();

  static public String cleanText(String txt) {
    //@formatter:off
    return txt.toLowerCase().replaceAll("[^\\p{L}\\s]+", " ")
        .replaceAll("ã â â", " ")
        .replaceAll("â", "")
        .replaceAll("[\\s]+", " ").trim();
    //@formatter:on
  }

  static public HashSet<String> load_stopwords() throws IOException {
    HashSet<String> stopwords = new HashSet<>();
    Path swPath = Paths.get(Constants.INPUT_DIR.toString(), "stopwords.txt");
    try (BufferedReader infile = Files.newBufferedReader(swPath, StandardCharsets.UTF_8)) {
      String stopword;
      while ((stopword = infile.readLine()) != null) {
        stopwords.add(stopword);
      }
    } catch (Exception e) {
      throw new IOException("Failed to load stopwords file from: " + swPath.toString());
    }
    return stopwords;
  }

  // static public ArrayList<String> tokenize(String txt) {
  //   ArrayList<String> tokens = new ArrayList<>();
  //   Collections.addAll(tokens, txt.split(" "));
  //   return tokens;
  // }

  // static public ArrayList<String> removeStopwords(String text, HashSet<String> stopwords) {
  //   ArrayList<String> clean_tokens = tokens;
  //   clean_tokens.removeAll(stopwords);
  //   return clean_tokens;
  // }

  // public String processText(String text) {
  //   HashSet<String> stopwords = load_stopwords();
  //   String cleanText = cleanText(text.trim());
  //   ArrayList<String> stemTokenList = new ArrayList<String>();
  //   for (String s : removeStopwords(tokenize(cleanText), stopwords)) {
  //     stemTokenList.add((ps.stem(s)));
  //   }
  //   return String.join(" ", stemTokenList);
  // }

}
