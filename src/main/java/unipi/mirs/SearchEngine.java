package unipi.mirs;

import java.io.IOException;
import java.util.Scanner;

import unipi.mirs.components.DocTable;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.graphics.ConsoleUX;
import unipi.mirs.graphics.Menu;

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

  public static void main(String[] args) throws IOException {
    Menu queryMenu = new Menu(stdin, "Change Mode", "Start Search", "Exit");
    ConsoleUX.DebugLog("Loading...");
    Vocabulary lexicon = new Vocabulary();
    lexicon.loadVocabulary();
    DocTable doctable = new DocTable();
    doctable.loadDocTable();
    int choice;
    System.out.println(ConsoleUX.CLS);
    while ((choice = queryMenu.printMenu(String.format(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Current query mode: %s",
        (isConjunctive ? "Conjunctive " : "Disjunctive ")))) != queryMenu.exitMenuOption()) {
      if (choice == 0) {
        isConjunctive = !isConjunctive;
      } else if (choice == 1) {
        //start_engine();
      }
    }
  }
}
