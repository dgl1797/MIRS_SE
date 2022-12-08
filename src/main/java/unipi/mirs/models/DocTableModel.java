package unipi.mirs.models;

/**
 * Class to just help in the conversion of read data into the correct type instead of using Object class and cast
 */
public class DocTableModel {
  public String docno;
  public int docid;
  public int doclen;

  public DocTableModel(String doctableLine) {
    String[] parts = doctableLine.split("\t");
    this.docid = Integer.parseUnsignedInt(parts[0]);
    parts = parts[1].split("-");
    this.docno = parts[0];
    this.doclen = Integer.parseUnsignedInt(parts[1]);
  }
}
