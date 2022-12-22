package unipi.mirs.graphics;

import java.io.IOException;
import java.util.Scanner;

public class Menu {
  private String[] options;
  public int numOptions;
  public int exitOption;
  private Scanner stdin;

  public Menu(Scanner s, String... options) {
    this.options = options;
    this.numOptions = this.options.length;
    this.exitOption = this.numOptions - 1;
    this.stdin = s;
  }

  /**
   * Function to print the options and to get the selected output with all error handlings
   * 
   * @return the choice as an integer
   * @throws IOException
   */
  public int printMenu() throws IOException {
    int choice = 0;
    boolean error = false;
    do {
      System.out.print(ConsoleUX.CLS);
      for (int i = 0; i < numOptions; i++) {
        ConsoleUX.SuccessLog(" " + (i + 1) + " > " + options[i]);
      }
      if (error) {
        ConsoleUX.ErrorLog("Invalid Choice");
      }
      String tmp = stdin.nextLine();
      try {
        choice = Integer.parseInt(tmp);
      } catch (Exception e) {
        for (int i = 0; i < numOptions; i++) {
          if (tmp.toLowerCase().equals(options[i].toLowerCase()))
            choice = i + 1;
        }
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
        ConsoleUX.SuccessLog(" " + (i + 1) + " > " + options[i]);
      }
      if (error) {
        ConsoleUX.ErrorLog("Invalid Choice");
      }
      String tmp = stdin.nextLine();
      try {
        choice = Integer.parseInt(tmp);
      } catch (Exception e) {
        for (int i = 0; i < numOptions; i++) {
          if (tmp.toLowerCase().equals(options[i].toLowerCase()))
            choice = i + 1;
        }
      }
      error = true;
    } while (!(choice >= 1 && choice <= numOptions));
    return choice - 1;
  }
}
