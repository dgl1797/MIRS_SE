package unipi.mirs.graphics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Menu {
  ArrayList<String> options;
  int numOptions;
  int exitOption;
  Scanner stdin;

  public Menu(Scanner s, String... options) {
    this.options = new ArrayList<>(Arrays.asList(options));
    this.numOptions = this.options.size();
    this.exitOption = this.options.size() - 1;
    this.stdin = s;
  }

  /**
   * prints the options of the current instance of Menu class
   * 
   * @return the selected choice when enter is pressed
   * @throws IOException if the input stream of stdin is invalid
   */
  public int printMenu() throws IOException {
    int choice = 0;
    boolean error = false;
    do {
      System.out.print(ConsoleUX.CLS);
      for (int i = 0; i < numOptions; i++) {
        ConsoleUX.SuccessLog(" " + (i + 1) + " > " + options.get(i));
      }
      if (error) {
        ConsoleUX.ErrorLog("Invalid Choice");
      }
      String tmp = stdin.nextLine();
      try {
        choice = Integer.parseInt(tmp);
      } catch (Exception e) {
      }
      error = true;
    } while (!(choice >= 1 && choice <= numOptions));
    return choice - 1;
  }

  /**
   * Overload printing an extra string before the menu
   * 
   * @param extraPrint extra string to be printed
   * @return the selected choice
   * @throws IOException
   */
  public int printMenu(String title) throws IOException {
    int choice = 0;
    boolean error = false;
    do {
      System.out.print(ConsoleUX.CLS);
      System.out.println(title);
      for (int i = 0; i < numOptions; i++) {
        ConsoleUX.SuccessLog(" " + (i + 1) + " > " + options.get(i));
      }
      if (error) {
        ConsoleUX.ErrorLog("Invalid Choice");
      }
      String tmp = stdin.nextLine();
      try {
        choice = Integer.parseInt(tmp);
      } catch (Exception e) {
      }
      error = true;
    } while (!(choice >= 1 && choice <= numOptions));
    return choice - 1;
  }

  public int exitMenuOption() {
    return this.exitOption;
  }
}
