package unipi.mirs.components;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import unipi.mirs.utilities.Constants;

public class PostingList {

    byte[] postingList;
    byte[] currentPosting;
    int pointer = 0;
    public PostingList(){};


    public int getPointer()
    {
        return this.pointer;
    }
    public int getDocID()
    {
        return postingToInt(currentPosting)[0];
    }
    public int getFreq()
    {
        return postingToInt(currentPosting)[1];
    }
    public boolean next()
    {
        try {
            
        byte[] posting = Arrays.copyOfRange(this.postingList, this.pointer, this.pointer + (Integer.BYTES*2));
        this.pointer += Integer.BYTES*2;
        this.currentPosting=posting;
        return true;
        } catch (IndexOutOfBoundsException e) {
            System.out.println("Error during next() function, array out of bound: " + e.getMessage());
        }
        return false;
    }
    public void close()
    {
        this.postingList = null;
        this.currentPosting = null;
        this.pointer = 0;
    }


    public boolean openList(int startPosition, int plLength) throws IOException {
        byte[] pl = new byte[plLength];
        String invertedIndexStr = String.format("inverted_index.dat");
        Path invertedIndexPath = Paths.get(Constants.OUTPUT_DIR.toString(), invertedIndexStr);
        try {
         
        FileInputStream fileInvInd = new FileInputStream(invertedIndexPath.toString());
        fileInvInd.read(pl, startPosition, plLength);
        this.postingList= pl;   
        } catch (IOException e) {
            System.out.println("OpenList function error, cannot open file "+invertedIndexPath.toString()+"\n"+e.getMessage());
        }

        return true;
    }

    public Integer[] postingToInt(byte[] pl) {
        
        Integer iterator = 0;
        Integer[] posting ;
        try {
            ByteBuffer wrapped = ByteBuffer.wrap(Arrays.copyOfRange(pl, iterator, iterator + Integer.BYTES));
            int docid = wrapped.getInt();
            iterator += Integer.BYTES;
    
            wrapped = ByteBuffer.wrap(Arrays.copyOfRange(pl, iterator, iterator + Integer.BYTES));
            int tf = wrapped.getInt();
    
            posting = new Integer[]{ docid, tf };
            return posting;
    
        } catch (Exception e) {
            System.out.println("Error during postingToInt function parsing " + e.getMessage());
        }
        return null;

        
    }
}
