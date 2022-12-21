package unipi.mirs.utilities;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
  private Constants() {};

  // PATHS
  static public Path WORKING_DIR = Paths.get(System.getProperty("user.dir"));
  static public Path DATA_DIR = Paths.get(WORKING_DIR.toString(), "data");
  static public Path INPUT_DIR = Paths.get(DATA_DIR.toString(), "input");
  static public Path OUTPUT_DIR = Paths.get(DATA_DIR.toString(), "output");
  static public Path QUERY_FILES = Paths.get(INPUT_DIR.toString(), "queries");
  static public Path UNFILTERED_INDEX = Paths.get(OUTPUT_DIR.toString(), "unfiltered_index");

  // CONSTANTS
  static public double K_ONE = 1.2;
  static public double B = 0.75;
}
