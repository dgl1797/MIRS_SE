package unipi.mirs.components;

import java.util.ArrayList;

import unipi.mirs.utilities.Constants;

public class scoringAlgs {


    public double TFIDF(ArrayList<PostingList> plList)
    {
        double score = 0.0;

        for(PostingList pl: plList)
        {
         
            
                if(pl.getFreq()>0)
                {
                    //PRECOMPILARE O AGGIORNARE TOTDOCS, E' SETTATO AD UN VALORE DI DEBUG. 
                    //NECESSARIO PASSARE LA REFERENZA DEL VOCABULARY PER "T.DF"
                    score += (1+Math.log(pl.getFreq()))*Math.log(Constants.TOTDOCS/t.df);
                }
   


        }
        return score;
    }


    
}
