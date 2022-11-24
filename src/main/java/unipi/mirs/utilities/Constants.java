package unipi.mirs.utilities;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Constants {
  private Constants() {};

  static public Path WORKING_DIR = Paths.get(System.getProperty("user.dir"), "src", "main", "java", "unipi", "mirs");
  static public Path INPUT_DIR = Paths.get(WORKING_DIR.toString(), "data", "input");
  static public Path OUTPUT_DIR = Paths.get(WORKING_DIR.toString(), "data", "output");
}
