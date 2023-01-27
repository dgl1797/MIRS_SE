package unipi.mirs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Paths;

import unipi.mirs.utilities.Constants;

public class Tester {
  public static void main(String[] args) throws IOException {
    FileInputStream frqr = new FileInputStream(
        new File(Paths.get(Constants.UNFILTERED_INDEX.toString(), "frequencies.dat").toString()));

    IntBuffer fib = ByteBuffer.wrap(frqr.readAllBytes()).asIntBuffer();
    int maxfreq = -1;
    while (fib.position() < fib.capacity()) {
      int x = fib.get();
      if (x > maxfreq) {
        maxfreq = x;
      }
    }
    System.out.println(String.format("max frequency: %d", maxfreq));
  }
}
