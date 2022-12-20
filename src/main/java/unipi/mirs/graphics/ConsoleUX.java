package unipi.mirs.graphics;

import java.util.Scanner;

public class ConsoleUX {
  private ConsoleUX() {};

  // UTILITY
  public static final String RESET = "\u001B[0m";
  public static final String BOLD = "\u001B[1m";
  public static final String CLS = "\033[H\033[2J";

  // FOREGROUND
  public static final String FG_BLACK = "\u001B[30m";
  public static final String FG_RED = "\u001B[31m";
  public static final String FG_GREEN = "\u001B[32m";
  public static final String FG_YELLOW = "\u001B[33m";
  public static final String FG_BLUE = "\u001B[34m";
  public static final String FG_PURPLE = "\u001B[35m";
  public static final String FG_CYAN = "\u001B[36m";
  public static final String FG_WHITE = "\u001B[37m";

  // BACKGROUND
  public static final String BG_BLACK = "\u001B[40m";
  public static final String BG_RED = "\u001B[41m";
  public static final String BG_GREEN = "\u001B[42m";
  public static final String BG_YELLOW = "\u001B[43m";
  public static final String BG_BLUE = "\u001B[44m";
  public static final String BG_PURPLE = "\u001B[45m";
  public static final String BG_CYAN = "\u001B[46m";
  public static final String BG_WHITE = "\u001B[47m";

  // MACROS
  public static final void DebugLog(String log) {
    System.out.println(ConsoleUX.RESET + ConsoleUX.FG_BLUE + ConsoleUX.BOLD + log + ConsoleUX.RESET);
  }

  public static final void DebugLog(String log, String termination) {
    System.out.print(ConsoleUX.RESET + ConsoleUX.FG_BLUE + ConsoleUX.BOLD + log + ConsoleUX.RESET + termination);
  }

  public static final String FormatDebug(String s) {
    return ConsoleUX.RESET + ConsoleUX.FG_BLUE + ConsoleUX.BOLD + s + ConsoleUX.RESET;
  }

  public static final void SuccessLog(String log) {
    System.out.println(ConsoleUX.RESET + ConsoleUX.FG_GREEN + ConsoleUX.BOLD + log + ConsoleUX.RESET);
  }

  public static final void SuccessLog(String log, String termination) {
    System.out.print(ConsoleUX.RESET + ConsoleUX.FG_GREEN + ConsoleUX.BOLD + log + ConsoleUX.RESET + termination);
  }

  public static final String FormatSuccess(String s) {
    return ConsoleUX.RESET + ConsoleUX.FG_GREEN + ConsoleUX.BOLD + s + ConsoleUX.RESET;
  }

  public static final void ErrorLog(String log) {
    System.out.println(ConsoleUX.RESET + ConsoleUX.FG_RED + ConsoleUX.BOLD + log + ConsoleUX.RESET);
  }

  public static final void ErrorLog(String log, String termination) {
    System.out.print(ConsoleUX.RESET + ConsoleUX.FG_RED + ConsoleUX.BOLD + log + ConsoleUX.RESET + termination);
  }

  public static final String FormatError(String s) {
    return ConsoleUX.RESET + ConsoleUX.FG_RED + ConsoleUX.BOLD + s + ConsoleUX.RESET;
  }

  /**
   * Pauses the execution of the program until Enter key is pressed.
   * 
   * @param clear_console wheather or not to clear console after the restart of execution
   * @param stdin         to avoid creating multiple instances of a Scanner on the same file
   */
  public static final void pause(boolean clear_console, Scanner stdin) {
    System.out.println(FormatDebug("Execution Paused, press Enter to continue..."));
    try {
      stdin.nextLine();
    } catch (Exception e) {
      System.out.println("Error handling input:\n" + e.getStackTrace().toString());
    }
    if (clear_console) {
      System.out.print(CLS);
    }
  }
}
