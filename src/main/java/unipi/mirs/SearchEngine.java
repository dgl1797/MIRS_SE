package unipi.mirs;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import unipi.mirs.components.DocTable;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.components.PostingList;
import unipi.mirs.utilities.TextNormalizationFunctions;

public class SearchEngine {
  private static final Scanner stdin = new Scanner(System.in);
  private static boolean isConjunctive = false;
  private static boolean isTFIDF = true;
  private static boolean stopnostem = false;

  private static HashSet<String> stopwords = new HashSet<String>();
  private static Vocabulary lexicon = null;
  private static DocTable doctable = null;

  private static boolean handleCommand(String command) throws IOException {
    if (command.toLowerCase().equals("exit"))
      return true;

    if (command.toLowerCase().equals("mode")) {
      isConjunctive = !isConjunctive;
    } else if (command.toLowerCase().equals("score")) {
      isTFIDF = !isTFIDF;
    } else if (command.toLowerCase().equals("file")) {
      ConsoleUX.DebugLog("Work in progress");
      ConsoleUX.pause(true, stdin);
    }else if (command.toLowerCase().equals("stopnostem")) {
      stopnostem=!stopnostem;
      loadDataStructures();
    } else if (command.toLowerCase().equals("help")) {
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/stopnostem/ - performs queries including stopwords and without the stemming process");
      ConsoleUX.SuccessLog("/score/ - changes scoring function to be used for ranked retrieval(TFIDF - BM25)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    } else {
      ConsoleUX.ErrorLog(ConsoleUX.CLS + "Unknown Command:");
      ConsoleUX.SuccessLog("/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/stopnostem/ - performs queries including stopwords and without the stemming process");
      ConsoleUX.SuccessLog("/score/ - changes scoring function to be used for ranked retrieval(TFIDF - BM25)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    }
    return false;
  }
  private static TreeSet<Entry<String, Double>> search(String query) throws IOException {
    TreeSet<Entry<String, Double>> top20 = new TreeSet<>(new Comparator<Entry<String, Double>>() {
      @Override
      public int compare(Entry<String, Double> e1, Entry<String, Double> e2) {
        if (e1.getKey().compareTo(e2.getKey()) == 0) {
          return 0;
        } else {
          if (e2.getValue().compareTo(e1.getValue()) < 0) {
            return -1;
          } else {
            return 1;
          }
        }
      }
    });
    query = TextNormalizationFunctions.cleanText(query);
    HashMap<String, Object[]> pls = new HashMap<>();
    int maxdocid = -1;
    int oldMax = -1;
    for (String w : query.split(" ")) {
      if (!stopwords.contains(w)) {
        if(!stopnostem)
        {
          w = TextNormalizationFunctions.ps.stem(w);

        }
        // if even one term is not present in the whole collection, returns empty list
        if (!lexicon.vocabulary.containsKey(w))
          return top20;

        if (pls.containsKey(w)) {
          pls.get(w)[1] = ((int) pls.get(w)[1]) + 1;
        } else {
          pls.put(w,
              new Object[] { PostingList.openList(lexicon.vocabulary.get(w)[0], lexicon.vocabulary.get(w)[1],stopnostem), 1 });
          // check if the opened docid is greater than maxdocid
          int currentdocid = ((PostingList) pls.get(w)[0]).getDocID();
          maxdocid = (currentdocid > maxdocid) ? currentdocid : maxdocid;
        }
      }
    }

    // pls.size() == 0 means no terms are valid
    if (pls.size() == 0)
      return top20;

    boolean isover = false;
    while (!isover) {
      while (oldMax != maxdocid) {
        oldMax = maxdocid;
        // looping over all the postings untill we reach a common maxdocid (oldmax will not change)
        for (String w : pls.keySet()) {
          if (!((PostingList) pls.get(w)[0]).nextGEQ(maxdocid)) {
            isover = true;
            break;
          }
          // update the maxdocid
          int currentdocid = ((PostingList) pls.get(w)[0]).getDocID();
          maxdocid = (currentdocid > maxdocid) ? currentdocid : maxdocid;
        }
        if (isover)
          break;
      }
      if (isover)
        break;
      // we are sure that all docids are the same and equal to maxdocid and we compute the score
      double total = 0;
      for (String w : pls.keySet()) {
        double currentscore = isTFIDF ? ((PostingList) pls.get(w)[0]).tfidf(doctable.ndocs, ((int) pls.get(w)[1]))
            : ((PostingList) pls.get(w)[0]).score(doctable.ndocs, ((int) pls.get(w)[1]),
                ((int) doctable.doctable.get(maxdocid)[1]), doctable.avgDocLen);
        total += currentscore;
      }
      // we have the score of the document maxdocid, now we insert it into the top20
      if (top20.size() == 20) {
        // limit reached, we need to check if last item (smallest) is lower than total
        if (top20.last().getValue() < total) {
          // we need to replace last item:
          top20.pollLast();
          top20.add(new AbstractMap.SimpleEntry<String, Double>((String) doctable.doctable.get(maxdocid)[0], total));
        }
      } else {
        // still not reached the limit so we simply add
        top20.add(new AbstractMap.SimpleEntry<String, Double>((String) doctable.doctable.get(maxdocid)[0], total));
      }

      // advance the positions of each postinglist
      for (String w : pls.keySet()) {
        if (!((PostingList) pls.get(w)[0]).next()) {
          // returned false hence posting list is over so we need to break
          isover = true;
          break;
        }
        // update maxdocid
        int currentdocid = ((PostingList) pls.get(w)[0]).getDocID();
        maxdocid = (currentdocid > maxdocid) ? currentdocid : maxdocid;
      }
      if (isover)
        break;
    }

    return top20;
  }

  //overload
  private static TreeSet<Entry<String, Double>> search(String query, boolean isDisjunctive) throws IOException {
    TreeSet<Entry<String, Double>> top20 = new TreeSet<>(new Comparator<Entry<String, Double>>() {
      @Override
      public int compare(Entry<String, Double> e1, Entry<String, Double> e2) {
        if (e1.getKey().compareTo(e2.getKey()) == 0) {
          return 0;
        } else {
          if (e2.getValue().compareTo(e1.getValue()) < 0) {
            return -1;
          } else {
            return 1;
          }
        }
      }
    });
    query = TextNormalizationFunctions.cleanText(query);
    HashMap<String, Object[]> pls = new HashMap<>();
    TreeMap<Integer, Double> partialScores = new TreeMap<>();

    for (String w : query.split(" ")) {
      if (!stopwords.contains(w)) {
        if(!stopnostem)
        {
          w = TextNormalizationFunctions.ps.stem(w);
        }
        // ignoring the terms that are not present in the lexicon
        if (!lexicon.vocabulary.containsKey(w))
          continue;

        if (!pls.containsKey(w)) {
          pls.put(w,
              new Object[] { PostingList.openList(lexicon.vocabulary.get(w)[0], lexicon.vocabulary.get(w)[1],stopnostem), 1 });
        } else {
          pls.get(w)[1] = ((int) pls.get(w)[1]) + 1;
        }
      }
    }

    if (pls.size() == 0)
      return top20;

    int completedPostingLists = 0;
    while (completedPostingLists < pls.size()) {
      // evaluate the current docid's score updating the partialscore of the docids
      for (String w : pls.keySet()) {
        // if postinglist is over skip it
        if (((PostingList) pls.get(w)[0]).isover())
          continue;

        int currentdocid = ((PostingList) pls.get(w)[0]).getDocID();
        double currentscore = isTFIDF ? ((PostingList) pls.get(w)[0]).tfidf(doctable.ndocs, ((int) pls.get(w)[1]))
            : ((PostingList) pls.get(w)[0]).score(doctable.ndocs, ((int) pls.get(w)[1]),
                ((int) doctable.doctable.get(currentdocid)[1]), doctable.avgDocLen);
        // update partial score
        if (!partialScores.containsKey(currentdocid)) {
          partialScores.put(currentdocid, currentscore);
        } else {
          partialScores.put(currentdocid, partialScores.get(currentdocid) + currentscore);
        }
        // move the postinglist to the next iteration
        if (!((PostingList) pls.get(w)[0]).next()) {
          // posting list is over
          completedPostingLists += 1;
        }
      }
      // remove the mindocid which is the first element of the partialscore heap sorted by docid and add it to top20
      Entry<Integer, Double> polledEntry = partialScores.pollFirstEntry();
      if (top20.size() == 20) {
        if (top20.last().getValue() < polledEntry.getValue()) {
          // if the lowest in top20 is lower than the polled docid score we remove the last and add the polled one
          top20.pollLast();
          top20.add(new AbstractMap.SimpleEntry<String, Double>((String) doctable.doctable.get(polledEntry.getKey())[0],
              polledEntry.getValue()));
        }
      } else {
        // simply adds the polled element
        top20.add(new AbstractMap.SimpleEntry<String, Double>((String) doctable.doctable.get(polledEntry.getKey())[0],
            polledEntry.getValue()));
      }
    }
    return top20;
  }

  public static boolean loadDataStructures() throws IOException
  {
    ConsoleUX.DebugLog(ConsoleUX.CLS + "Loading lexicon and doctable..");
    lexicon = null;
    doctable = null;
    lexicon = new Vocabulary(stopnostem);
    lexicon.loadVocabulary();
    doctable = new DocTable(stopnostem);
    doctable.loadDocTable();
    return true;
  }
  public static void main(String[] args) throws IOException {
    try {
      // setup
      ConsoleUX.DebugLog(ConsoleUX.CLS + "Loading...");
      loadDataStructures();
      if(stopnostem)
      {
        stopwords = TextNormalizationFunctions.load_stopwords();
      }
      // guide
      TreeSet<Entry<String, Double>> top20 = new TreeSet<>();
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "Commands:\n/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/stopnostem/ - performs queries including stopwords and without the stemming process");
      ConsoleUX.SuccessLog("/score/ - changes scoring function to be used for ranked retrieval(TFIDF - BM25)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - quits the search engine");
      // interactive querying
      while (true) {
        ConsoleUX.SuccessLog("Search", "");
        ConsoleUX.DebugLog("[" + (isConjunctive ? "c" : "d") + "]", "");
        ConsoleUX.DebugLog("[" + (isTFIDF ? "tfidf" : "bm25") + "]", "");
        
        ConsoleUX.DebugLog("[sw" + (stopnostem ? "+]" : "-]") + "[stem"+ (stopnostem ? "-]" : "+]"));
        ConsoleUX.SuccessLog(": ", "");
        String query = stdin.nextLine();
        // query system commands
        if (query.matches("^\\/(help|mode|stopnostem|score|file|exit)\\/$")) {
          if (handleCommand(query.replaceAll("\\/", ""))) {
            // termination if user enters /exit/ in the search field
            break;
          }
          System.out.print(ConsoleUX.CLS);
          continue;
        }

        // query start
        long before = System.currentTimeMillis();
        top20 = isConjunctive ? search(query) : search(query, true);
        long delta = System.currentTimeMillis() - before;
        // query end

        // documents printing
        int position = 0;
        ConsoleUX.DebugLog("Parsed " + doctable.ndocs + " documents in " + delta + "ms:");
        for (Entry<String, Double> doc : top20) {
          ConsoleUX.SuccessLog((++position) + ".\t" + doc.getKey() + " - " + doc.getValue());
        }
        ConsoleUX.pause(true, stdin);
      }
    } catch (IOException e) {
      ConsoleUX.ErrorLog("Search failed:");
      ConsoleUX.ErrorLog(e.getMessage());
    }
  }
}
