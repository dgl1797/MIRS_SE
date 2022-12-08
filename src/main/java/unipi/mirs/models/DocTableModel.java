package unipi.mirs.models;

public class DocTableModel {
  private String docno;
  private int docid;
  private int doclen;

  public DocTableModel(String doctableLine) {
    String[] parts = doctableLine.split("\t");
    this.docid = Integer.parseUnsignedInt(parts[0]);
    parts = parts[1].split("-");
    this.docno = parts[0];
    this.doclen = Integer.parseUnsignedInt(parts[1]);
  }

  /**
   * @return String return the docno
   */
  public String docno() {
    return docno;
  }

  /**
   * @param docno the docno to set
   */
  public void docno(String docno) {
    this.docno = docno;
  }

  /**
   * @return int return the docid
   */
  public int docid() {
    return docid;
  }

  /**
   * @param docid the docid to set
   */
  public void docid(int docid) {
    this.docid = docid;
  }

  /**
   * @return long return the doclen
   */
  public int doclen() {
    return doclen;
  }

  /**
   * @param doclen the doclen to set
   */
  public void doclen(int doclen) {
    this.doclen = doclen;
  }

}
