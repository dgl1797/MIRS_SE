package unipi.mirs.models;

/**
 * Data class holding all the necessary information to format the doctable lines
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
