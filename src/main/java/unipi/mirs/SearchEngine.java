package unipi.mirs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SearchEngine 
{
    static private Path workingFolder = Paths.get(System.getProperty("user.dir"), "src", "main", "java", "unipi",
            "mirs");
    static private Path inputPath = Paths.get(workingFolder.toString(), "data", "input", "file.txt");
    static private Path outputPath = Paths.get(workingFolder.toString(), "data", "output", "outTest.dat");
    public static void main( String[] args ) throws IOException
    {
        try (BufferedReader infile = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            try (BufferedWriter outfile = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                String doc;
                while ((doc = infile.readLine()) != null) {
                    // String[] parts = doc.split("\t");
                    // String docno = parts[0].trim();
                    // String line = parts[1].trim();
                    String line = doc.toLowerCase().replaceAll("[^\\p{L}\\s]+", " ").replaceAll("\\s+", " ")
                            .trim();
                    // outfile.append(docno + "\t");
                    outfile.append(line);
                    outfile.newLine();
                }
            }
        }
    }
}
