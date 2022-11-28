package unipi.mirs;

import java.io.IOException;
import java.time.chrono.IsoChronology;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.management.Query;

import unipi.mirs.components.DocTable;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.components.PostingList;
import unipi.mirs.utilities.TextNormalizationFunctions;

/**
 * @formatter:off
 * search has to give the chance to quit after a succesful query such as:
 *   d1
 *   d2
 *   d3
 *  keep querying? [Y/n]: y
 * 
 * search: <newquery>
 *   d1 -> old results
 *   d2 -> old results
 *   d3 -> old results
 * @formatter:on
 */

public class SearchEngine {
  private static final Scanner stdin = new Scanner(System.in);
  private static boolean isConjunctive = false;

  private static HashSet<String> stopwords = null;
  private static Vocabulary lexicon = null;
  private static DocTable doctable = null;

  private static boolean handleCommand(String command) {
    if (command.toLowerCase().equals("mode")) {
      isConjunctive = !isConjunctive;
      return false;
    } else if (command.toLowerCase().equals("exit")) {
      return true;
    } else if (command.toLowerCase().equals("file")) {
      ConsoleUX.DebugLog("Work in progress");
    } else if (command.toLowerCase().equals("help")) {
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    } else {
      ConsoleUX.ErrorLog(ConsoleUX.CLS + "Unknown Command:");
      ConsoleUX.SuccessLog("/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    }
    return false;
  }

  private static String[] search(String query) throws IOException {
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
        if (!pls.containsKey(w)) {
          pls.put(w,
              new Object[] { PostingList.openList(lexicon.vocabulary.get(w)[0], lexicon.vocabulary.get(w)[1]), 1 });
          maxDocId = ((PostingList) pls.get(w)[0]).getDocID() > maxDocId ? ((PostingList) pls.get(w)[0]).getDocID()
              : maxDocId;
        } else {
          pls.get(w)[1] = (int) (pls.get(w)[1]) + 1;
        }
      }
    }

    int oldMax = -1;
    while (!isOver) {
      while (oldMax != maxDocId) {
        oldMax = maxDocId;
        for (String w : pls.keySet()) {
          if (!((PostingList) pls.get(w)[0]).nextGEQ(maxDocId)) {
            isOver = true;
            break;
          } ;
          maxDocId = ((PostingList) pls.get(w)[0]).getDocID() > maxDocId ? ((PostingList) pls.get(w)[0]).getDocID()
              : maxDocId;
        }
      }
      if (isOver) {
        break;
      }
      double total = 0;
      for (String w : pls.keySet()) {
        total += ((PostingList) pls.get(w)[0]).score();
      }
      if (top20.size() == 20) {
        if (total < top20.last().getValue()) {
          continue;
        }
        top20.pollLast();
      }
      top20.add(new AbstractMap.SimpleEntry<String, Double>((String) doctable.doctable.get(maxDocId)[0], total));
    }
    return (String[]) top20.toArray();
  }

  //overload
  private static String[] search(String query, boolean isDisjunctive) throws IOException {
    String[] top20 = new String[20];
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
      String[] top20 = new String[20];
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "Commands:\n/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/file/ - performs queries taking them from a selected file");
      ConsoleUX.SuccessLog("/exit/ - quits the search engine");
      // interactive querying
      while (true) {
        ConsoleUX.SuccessLog("Search", "");
        ConsoleUX.DebugLog("[" + (isConjunctive ? "c" : "d") + "]", "");
        ConsoleUX.SuccessLog(": ", "");
        String query = stdin.nextLine();
        // query system commands
        if (query.matches("^\\/(help|mode|file|exit)\\/$")) {
          if (handleCommand(query.replaceAll("\\/", ""))) {
            // termination if user enters /exit/ in the search field
            break;
          }
          System.out.print(ConsoleUX.CLS);
          continue;
        }
        // query start
        long before = System.currentTimeMillis();

        // top20 = search(query);

        long delta = System.currentTimeMillis() - before;
        // query end

        // documents printing
        ConsoleUX.DebugLog("Parsed " + doctable.ndocs + " documents in " + delta + "ms:");
        for (int i = 0; i < top20.length; i++) {
          ConsoleUX.SuccessLog((i + 1) + ".\t" + top20[i]);
        }
        ConsoleUX.pause(true, stdin);
      }
    } catch (IOException e) {
      ConsoleUX.ErrorLog("Search failed:");
      ConsoleUX.ErrorLog(e.getMessage());
    }
  }
}
