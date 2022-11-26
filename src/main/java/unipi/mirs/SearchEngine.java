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

  private static boolean handleCommand(String command) {
    if (command.toLowerCase().equals("mode")) {
      isConjunctive = !isConjunctive;
      return false;
    } else if (command.toLowerCase().equals("exit")) {
      return true;
    } else if (command.toLowerCase().equals("help")) {
      ConsoleUX.SuccessLog(ConsoleUX.CLS + "/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    } else {
      ConsoleUX.ErrorLog(ConsoleUX.CLS + "Unknown Command:");
      ConsoleUX.SuccessLog("/help/ - prints the guide for all possible commands");
      ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
      ConsoleUX.SuccessLog("/exit/ - stops the interactive search");
      ConsoleUX.pause(true, stdin);
    }
    return false;
  }

  public static void main(String[] args) throws IOException {
    try {
      // setup
      Menu queryMenu = new Menu(stdin, "Change Mode", "Start Search", "Exit");
      ConsoleUX.DebugLog(ConsoleUX.CLS + "Loading...");
      Vocabulary lexicon = new Vocabulary();
      lexicon.loadVocabulary();
      DocTable doctable = new DocTable();
      doctable.loadDocTable();
      int choice;
      // menu
      while ((choice = queryMenu.printMenu(String.format(ConsoleUX.FG_BLUE + ConsoleUX.BOLD + "Current query mode: %s",
          (isConjunctive ? "Conjunctive " : "Disjunctive ")))) != queryMenu.exitMenuOption()) {
        if (choice == 0) {
          isConjunctive = !isConjunctive;
        } else if (choice == 1) {
          String[] top20 = new String[20];
          ConsoleUX.SuccessLog(ConsoleUX.CLS + "/help/ - prints the guide for all possible commands");
          ConsoleUX.SuccessLog("/mode/ - changes query mode(conjunctive - disjunctive)");
          ConsoleUX.SuccessLog("/exit/ - goes back to main menu");
          // interactive querying
          while (true) {
            ConsoleUX.SuccessLog("Search", "");
            ConsoleUX.DebugLog("[" + (isConjunctive ? "c" : "d") + "]", "");
            ConsoleUX.SuccessLog(": ", "");
            String query = stdin.nextLine();
            // query system commands
            if (query.matches("^\\/.+\\/$")) {
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
        }
      }
    } catch (IOException e) {
      ConsoleUX.ErrorLog("Search failed:");
      ConsoleUX.ErrorLog(e.getMessage());
    }
  }
}
