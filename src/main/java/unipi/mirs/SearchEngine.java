package unipi.mirs;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.Map.Entry;

import unipi.mirs.components.DocTable;
import unipi.mirs.components.PostingList;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.utilities.TextNormalizationFunctions;

public class SearchEngine {
  private static final Scanner stdin = new Scanner(System.in);

  // PARAMETERS
  private static boolean isConjunctive = false;
  private static boolean isTFIDF = true;
  private static boolean stopnostem = false;
  private static int parsedDocs = 0;

  // DATA STRUCTURES
  private static HashSet<String> stopwords = new HashSet<String>();
  private static Vocabulary lexicon = null;
  private static DocTable doctable = null;

  // CORE FUNCTIONS

  /**
   * function to handle commands in the query string
   * 
   * @param command the given command string
   * @return boolean stating wheather the given command is exit or not
   * @throws IOException
   */
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
    } else if (command.toLowerCase().equals("filter")) {
      stopnostem = !stopnostem;
      loadDataStructures();
    } else if (command.toLowerCase().equals("help")) {
      printHelp();
    } else {
      ConsoleUX.ErrorLog(ConsoleUX.CLS + "Unknown Command:");
      printHelp();
    }
    return false;
  }

  /**
   * performs a conjunctive query on the collection, returning the top 20 documents where all the query terms appear
   * 
   * @param query the query string
   * @return the top 20 documents resulting from the query
   * @throws IOException
   */
  private static TreeSet<Entry<String, Double>> conjunctiveSearch(String query) throws IOException {
    // INITIALIZE THE CORRECT COMPARISON FOR THE DOCUMENTS
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

    // NORMALIZE QUERY
    query = TextNormalizationFunctions.cleanText(query);

    // OPEN THE POSTING LISTS AND GET THE MAX DOCID
    HashMap<String, PostingList> pls = new HashMap<>();
    int maxdocid = -1;
    int oldMax = -1;
    for (String w : query.split(" ")) {

      // filter stopwords 
      if (!stopwords.contains(w)) {
        // stem
        w = stopnostem ? w : TextNormalizationFunctions.ps.stem(w);

        // if even one term is not present in the whole collection, returns empty list
        if (!lexicon.vocabulary.containsKey(w))
          return top20;

        // update the posting lists data structure
        if (pls.containsKey(w)) {
          pls.get(w).increaseOccurrences();
        } else {
          pls.put(w, PostingList.openList(lexicon.vocabulary.get(w).startByte(), lexicon.vocabulary.get(w).plLength(),
              stopnostem));

          // check if the opened docid is greater than maxdocid
          int currentdocid = pls.get(w).getDocID();
          maxdocid = (currentdocid > maxdocid) ? currentdocid : maxdocid;
        }
      }
    }

    // pls.size() == 0 means no terms are valid
    if (pls.size() == 0)
      return top20;

    // LOOP OVER THE POSTING LISTS GOING TO THE MAXIMUM COMMON DOCID AT EACH ITERATION
    boolean isover = false;
    while (!isover) {

      // find the max common docid
      while (oldMax != maxdocid) {
        oldMax = maxdocid;
        // looping over all the postings untill we reach a common maxdocid (oldmax will not change)
        for (String w : pls.keySet()) {
          if (!pls.get(w).nextGEQ(maxdocid)) {
            isover = true;
            break;
          }
          // update the maxdocid
          int currentdocid = pls.get(w).getDocID();
          maxdocid = (currentdocid > maxdocid) ? currentdocid : maxdocid;
        }
        if (isover)
          break;
      }
      if (isover)
        break;

      // compute the score for the max common docid
      double total = 0;
      for (String w : pls.keySet()) {
        double currentscore = isTFIDF ? pls.get(w).tfidf(doctable.ndocs)
            : pls.get(w).score(doctable.ndocs, doctable.doctable.get(maxdocid).doclen(), doctable.avgDocLen);
        total += currentscore;
      }
      // we have the score of the document maxdocid, now we insert it into the top20
      if (top20.size() == 20) {
        // limit reached, we need to check if last item (smallest) is lower than total
        if (top20.last().getValue() < total) {
          // we need to replace last item:
          top20.pollLast();
          top20.add(new AbstractMap.SimpleEntry<String, Double>(doctable.doctable.get(maxdocid).docno(), total));
        }
      } else {
        // still not reached the limit so we simply add
        top20.add(new AbstractMap.SimpleEntry<String, Double>(doctable.doctable.get(maxdocid).docno(), total));
      }

      // advance the positions of each postinglist
      for (String w : pls.keySet()) {
        if (!pls.get(w).next()) {
          // returned false hence posting list is over so we need to break
          isover = true;
          break;
        }
        // update maxdocid
        int currentdocid = pls.get(w).getDocID();
        maxdocid = (currentdocid > maxdocid) ? currentdocid : maxdocid;
      }
      if (isover)
        break;
    }
    parsedDocs = maxdocid;
    return top20;
  }

  private static TreeSet<Entry<String, Double>> disjunctiveSearch(String query) throws IOException {
    // INITIALIZE TOP20 WITH CORRECT COMPARATOR METHOD
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

    // NORMALIZE QUERY STRING
    query = TextNormalizationFunctions.cleanText(query);
    HashMap<String, PostingList> pls = new HashMap<>();

    // heap with the docids actually targeted by the terms' posting lists
    TreeSet<Integer> docids = new TreeSet<>();
    int lastdocid = 0;

    // OPEN THE POSTING LISTS
    for (String w : query.split(" ")) {
      // filter loaded stopwords
      if (!stopwords.contains(w)) {
        // stem if filter is active
        w = stopnostem ? w : TextNormalizationFunctions.ps.stem(w);

        // ignore terms not present in vocabulary
        if (!lexicon.vocabulary.containsKey(w))
          continue;

        // update posting lists structure
        if (pls.containsKey(w)) {
          pls.get(w).increaseOccurrences();
        } else {
          pls.put(w, PostingList.openList(lexicon.vocabulary.get(w).startByte(), lexicon.vocabulary.get(w).plLength(),
              stopnostem));
          docids.add(pls.get(w).getDocID());
        }
      }
    }

    if (pls.size() == 0)
      return top20;

    // TRAVERSE ALL THE POSTINGS DAAT
    int completedPostingLists = 0;
    while (completedPostingLists < pls.size()) {
      // init current document's score
      double docscore = 0;

      // retrieve the parsed docid which is the lowest between all the query terms' postings taken from the heap
      int currentdocid = docids.first();
      for (String w : pls.keySet()) {
        // skip ended posting lists
        if (pls.get(w).isover())
          continue;

        // check if this posting list is in correct position
        int listdocid = pls.get(w).getDocID();
        if (currentdocid == listdocid) {
          docscore += isTFIDF ? pls.get(w).tfidf(doctable.ndocs)
              : pls.get(w).score(doctable.ndocs, doctable.doctable.get(listdocid).doclen(), doctable.avgDocLen);

          if (!pls.get(w).next()) {
            completedPostingLists += 1;
          } else {
            docids.add(pls.get(w).getDocID());
          }
        }
      }

      // CHECK IF TOP20 LIMIT HAS BEEN REACHED
      if (top20.size() == 20) {
        if (top20.last().getValue() < docscore) {
          // substitute
          top20.pollLast();
          top20.add(new AbstractMap.SimpleEntry<String, Double>(doctable.doctable.get(currentdocid).docno(), docscore));
        }
      } else {
        // simple add
        top20.add(new AbstractMap.SimpleEntry<String, Double>(doctable.doctable.get(currentdocid).docno(), docscore));
      }
      lastdocid = docids.pollFirst();
    }
    parsedDocs = lastdocid;
    return top20;
  }

  public static void main(String[] args) {
    try {
      // SETUP STRUCTURES
      loadDataStructures();
      TreeSet<Entry<String, Double>> top20 = new TreeSet<>();

      // PRINT GUIDE
      printHelp();

      // START INTERACTIVE QUERYING
      while (true) {
        // SHOW THE QUERY SETTINGS
        ConsoleUX.DebugLog("Stopwords and Stemming filtering: ", "");
        ConsoleUX.SuccessLog(stopnostem ? "disabled" : "enabled");
        ConsoleUX.SuccessLog("Search", "");
        ConsoleUX.DebugLog("[" + (isConjunctive ? "c" : "d") + "]", "");
        ConsoleUX.DebugLog("[" + (isTFIDF ? "tfidf" : "bm25") + "]", "");
        ConsoleUX.SuccessLog(": ", "");

        // GET NEXT QUERY
        String query = stdin.nextLine();

        // CHECK QUERY MATCHES A COMMAND FORMAT
        if (query.matches("^\\/(help|mode|filter|score|file|exit)\\/$")) {
          if (handleCommand(query.replaceAll("\\/", ""))) {
            // termination if user enters /exit/ in the search field
            break;
          }
          System.out.print(ConsoleUX.CLS);
          continue;
        }

        // QUERY START
        long before = System.currentTimeMillis();
        top20 = isConjunctive ? conjunctiveSearch(query) : disjunctiveSearch(query);
        long delta = System.currentTimeMillis() - before;
        // QUERY END

        // PRINT TOP 20
        int position = 0;
        ConsoleUX.DebugLog("Parsed " + parsedDocs + " documents in " + delta + "ms:");
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

  // HELPER FUNCTIONS
  private static final void printHelp() {
    ConsoleUX.SuccessLog(ConsoleUX.CLS + "/help/ - prints the guide for all possible commands");
    ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
    ConsoleUX.SuccessLog("/filter/ - performs queries including stopwords and without the stemming process");
    ConsoleUX.SuccessLog("/score/ - changes scoring function to be used for ranked retrieval(TFIDF - BM25)");
    ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
    ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
    ConsoleUX.pause(true, stdin);
  }

  /**
   * Helper Function to load the datastructures
   * 
   * @throws IOException
   */
  public static void loadDataStructures() throws IOException {
    ConsoleUX.DebugLog(ConsoleUX.CLS + "Loading lexicon and doctable...");

    // LOAD STOPWORDS
    stopwords = stopnostem ? new HashSet<>() : TextNormalizationFunctions.load_stopwords();

    // LOAD LEXICON
    lexicon = Vocabulary.loadVocabulary(stopnostem);

    // LOAD DOCTABLE
    doctable = DocTable.loadDocTable(stopnostem);
  }
}
