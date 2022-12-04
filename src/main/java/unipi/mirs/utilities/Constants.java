package unipi.mirs.utilities;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Constants {
  private Constants() {};

  static public Path WORKING_DIR = Paths.get(System.getProperty("user.dir"), "src", "main", "java", "unipi", "mirs");
  static public Path INPUT_DIR = Paths.get(WORKING_DIR.toString(), "data", "input");
  static public Path OUTPUT_DIR = Paths.get(WORKING_DIR.toString(), "data", "output");
  static public Path QUERIES_FILES = Paths.get(WORKING_DIR.toString(), "data", "input", "queries");
  static public Path STOPNOSTEM_OUTPUT_DIR = Paths.get(OUTPUT_DIR.toString(), "stopnostem");
  static public double K_ONE = 1.2;
  static public double B = 0.75;
}
