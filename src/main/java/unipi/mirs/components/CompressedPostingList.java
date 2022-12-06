/*package unipi.mirs.components;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import unipi.mirs.utilities.Constants;

public class CompressedPostingList {
    
    String term;
    byte postingList = null;
    byte[] currentPosting;
    int pointer = 0;
    public CompressedPostingList(String term){
        this.term=term;
    }

    public String getTerm()
    {
        return this.term;
    }
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
            
        this.postingList.position(this.postingList.position()+1);//Arrays.copyOfRange(this.postingList, this.pointer, this.pointer + (Integer.BYTES*2));
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

        if(this.compressed)
        {
            this.postingList= ByteBuffer.wrap(pl);   
        
        }
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

    public boolean nextGEQ(int docid){
        
        int postListByteSize = 2*Integer.BYTES;
        int currdocid = getDocID();
        if( currdocid>=docid)
        {
            return true;
        }
        byte[] checkPost = Arrays.copyOfRange(this.postingList, this.pointer, this.pointer + (postListByteSize));
        if(postingToInt(checkPost)[0]>=docid)
        {
            this.currentPosting = checkPost;
            this.pointer+=1;
            return true;
        }
        

        int leftPostings = this.pointer+1;
        int rightPostings = (this.postingList.length /(postListByteSize));

        checkPost = Arrays.copyOfRange(this.postingList, rightPostings*(postListByteSize)-(postListByteSize), rightPostings*(postListByteSize));

        if( postingToInt(checkPost)[0]<docid)
        {
            return false;
        }
        if( postingToInt(checkPost)[0]==docid)
        {
            this.currentPosting = checkPost;
            this.pointer=rightPostings*(postListByteSize)-(postListByteSize);
            return true;
        }

        int middlePosting = (int) Math.floor((rightPostings+leftPostings)/2);

        checkPost= Arrays.copyOfRange(this.postingList, middlePosting*2, middlePosting*2 + (postListByteSize));



        while(postingToInt(checkPost)[0] == docid)
        {
            int middleDocid = postingToInt(Arrays.copyOfRange(this.postingList, middlePosting*2, middlePosting*2 + (postListByteSize)))[0];
            //int rightDocid =
            //int leftDocid =  
            if((rightPostings-leftPostings)==1)
            {
                this.currentPosting = Arrays.copyOfRange(this.postingList, rightPostings*(postListByteSize)-(postListByteSize), rightPostings*(postListByteSize));
                this.pointer=rightPostings*(postListByteSize)-(postListByteSize);
                return true;
            }
            if(docid<middleDocid)
            {
                rightPostings = middlePosting;

            }


            if(docid>middleDocid)
            {
                leftPostings = middlePosting;

            }
            middlePosting = (int) Math.floor((rightPostings+leftPostings)/2);

        }





        return true;
    }

    public double score()
    {

        //return score += (1+Math.log(this.getFreq()))*Math.log(Constants.TOTDOCS/plLength);
        return 0;
    }
}
*/