package unipi.mirs.utilities;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import opennlp.tools.stemmer.PorterStemmer; 

public final class TextNormalizationFunctions {
  PorterStemmer ps = new PorterStemmer();
  
  private TextNormalizationFunctions(){}
  
  static public String removeSpecialCharacters(String txt) {
    return txt.toLowerCase().replaceAll("[^\\p{L}\\s]+", " ").replaceAll("\\s+", " ").trim();
  }

  static public ArrayList<String> load_stopwords() {
    ArrayList<String> stopwords = new ArrayList<String>();
    try (BufferedReader infile = Files.newBufferedReader(Paths.get(Constants.INPUT_DIR.toString(), "stopwords.txt"), StandardCharsets.UTF_8)) {
        String stopword;
        while ((stopword = infile.readLine()) != null) {
            stopwords.add(stopword);
        }
    } catch (Exception e) {
        System.out.println(e.getMessage());
    }
    return stopwords;
  }

  static public ArrayList<String> tokenize(String txt) {
    ArrayList<String> tokens = new ArrayList<>();
    Collections.addAll(tokens, txt.split(" "));
    return tokens;
  }

  static public ArrayList<String> removeStopwords(ArrayList<String> tokens, ArrayList<String> stopwords) {
    ArrayList<String> clean_tokens = tokens;
    clean_tokens.removeAll(stopwords);
    return clean_tokens;
  }
  
    public String processText(String text, ArrayList<String> stopwords) {
      String[] parts = text.split("\t");
      String docbody = removeSpecialCharacters(parts[1].trim());
      ArrayList<String> stemTokenList = new ArrayList<String>();
      for (String s : removeStopwords(tokenize(docbody), stopwords)) {
        stemTokenList.add((ps.stem(s)));
      }
      return String.join(" ", stemTokenList);
    }
  
}
