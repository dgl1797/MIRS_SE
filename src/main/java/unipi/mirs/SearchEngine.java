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

  private static HashSet<String> stopwords = null;
  private static Vocabulary lexicon = null;
  private static DocTable doctable = null;

  private static boolean handleCommand(String command) {
    if (command.toLowerCase().equals("exit"))
      return true;

    if (command.toLowerCase().equals("mode")) {
      isConjunctive = !isConjunctive;
    } else if (command.toLowerCase().equals("score")) {
      isTFIDF = !isTFIDF;
    } else if (command.toLowerCase().equals("file")) {
      ConsoleUX.DebugLog("Work in progress");
      ConsoleUX.pause(true, stdin);
    } else if (command.toLowerCase().equals("help")) {
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/score/ - changes scoring function to be used for ranked retrieval(TFIDF - BM25)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    } else {
      ConsoleUX.ErrorLog(ConsoleUX.CLS + "Unknown Command:");
      ConsoleUX.SuccessLog("/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
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
        return e2.getValue().compareTo(e1.getValue());
      }
    });

    String queryClean = TextNormalizationFunctions.cleanText(query);
    HashMap<String, Object[]> pls = new HashMap<>();
    int maxDocId = 0;
    boolean isOver = false;

    for (String w : queryClean.split(" ")) {
      if (!stopwords.contains(w)) {
        w = TextNormalizationFunctions.ps.stem(w);
        // one of the terms is not present anywhere
        if (!lexicon.vocabulary.containsKey(w))
          return top20;
        if (!pls.containsKey(w)) {

          pls.put(w,
              new Object[] { PostingList.openList(lexicon.vocabulary.get(w)[0], lexicon.vocabulary.get(w)[1]), 1 });
          maxDocId = ((PostingList) pls.get(w)[0]).getDocID() > maxDocId ? ((PostingList) pls.get(w)[0]).getDocID()
              : maxDocId;
        } else {
          pls.get(w)[1] = ((int) pls.get(w)[1]) + 1;
        }
      }
    }

    // only stopwords or empty string
    if (pls.size() == 0)
      return top20;

    int oldMax = -1;
    while (!isOver) {
      while (oldMax != maxDocId) {
        oldMax = maxDocId;
        for (String w : pls.keySet()) {
          if (!((PostingList) pls.get(w)[0]).nextGEQ(maxDocId)) {
            isOver = true;
            break;
          }
          maxDocId = (((PostingList) pls.get(w)[0]).getDocID() > maxDocId) ? (((PostingList) pls.get(w)[0]).getDocID())
              : maxDocId;

        }
      }
      if (isOver) {
        break;
      }
      double total = 0;
      for (String w : pls.keySet()) {
        total += (!isTFIDF)
            ? ((PostingList) pls.get(w)[0]).score(doctable.ndocs, ((int) pls.get(w)[1]),
                (int) doctable.doctable.get(maxDocId)[1], doctable.avgDocLen)
            : ((PostingList) pls.get(w)[0]).tfidf(doctable.ndocs, ((int) pls.get(w)[1]));
      }
      if (top20.size() == 20) {
        if (total < top20.last().getValue()) {
          for (String w : pls.keySet()) {
            int nextdocid;
            if (((PostingList) pls.get(w)[0]).next()) {
              nextdocid = ((PostingList) pls.get(w)[0]).getDocID();
              if (maxDocId < nextdocid) {
                maxDocId = nextdocid;
              }
            } else {
              // no more terms in at least 1 posting list hence quits the for and the while by setting isover as true
              isOver = true;
              break;
            }
          }
          if (isOver)
            break;
          continue;
        }
        top20.pollLast();
      }
      top20.add(new AbstractMap.SimpleEntry<String, Double>((String) doctable.doctable.get(maxDocId)[0], total));
      for (String w : pls.keySet()) {
        int nextdocid;
        if (((PostingList) pls.get(w)[0]).next()) {
          nextdocid = ((PostingList) pls.get(w)[0]).getDocID();
          if (maxDocId < nextdocid) {
            maxDocId = nextdocid;
          }
        } else {
          // no more terms in at least 1 posting list hence quits the while
          isOver = true;
          break;
        }
      }
    }
    return top20;
  }

  //overload
  private static TreeSet<Entry<String, Double>> search(String query, boolean isDisjunctive) throws IOException {
    TreeSet<Entry<String, Double>> top20 = new TreeSet<>(new Comparator<Entry<String, Double>>() {
      @Override
      public int compare(Entry<String, Double> e1, Entry<String, Double> e2) {
        return e2.getValue().compareTo(e1.getValue());
      }
    });
    query = TextNormalizationFunctions.cleanText(query);
    HashMap<String, Object[]> pls = new HashMap<>();
    TreeMap<Integer, Double> partialScores = new TreeMap<>();

    for (String w : query.split(" ")) {
      if (!stopwords.contains(w)) {
        w = TextNormalizationFunctions.ps.stem(w);
        // ignoring the terms that are not present in the lexicon
        if (!lexicon.vocabulary.containsKey(w))
          continue;

        if (!pls.containsKey(w)) {
          pls.put(w,
              new Object[] { PostingList.openList(lexicon.vocabulary.get(w)[0], lexicon.vocabulary.get(w)[1]), 1 });
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

  public static void main(String[] args) throws IOException {
    try {
      // setup
      ConsoleUX.DebugLog(ConsoleUX.CLS + "Loading...");
      lexicon = new Vocabulary();
      lexicon.loadVocabulary();
      doctable = new DocTable();
      doctable.loadDocTable();
      stopwords = TextNormalizationFunctions.load_stopwords();
      // guide
      TreeSet<Entry<String, Double>> top20 = new TreeSet<>();
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "Commands:\n/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/score/ - changes scoring function to be used for ranked retrieval(TFIDF - BM25)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - quits the search engine");
      // interactive querying
      while (true) {
        ConsoleUX.SuccessLog("Search", "");
        ConsoleUX.DebugLog("[" + (isConjunctive ? "c" : "d") + "]", "");
        ConsoleUX.DebugLog("[" + (isTFIDF ? "tfidf" : "bm25") + "]", "");
        ConsoleUX.SuccessLog(": ", "");
        String query = stdin.nextLine();
        // query system commands
        if (query.matches("^\\/(help|mode|score|file|exit)\\/$")) {
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
