package unipi.mirs.components;

import unipi.mirs.utilities.Constants;

public class scoringAlgs {


    public double TFIDF_singleWeight(int df,int tf)
    {
        double score = 0.0;

        if(tf>0)
        {
            //PRECOMPILARE O AGGIORNARE TOTDOCS, E' SETTATO AD UN VALORE DI DEBUG
            score = (1+Math.log(tf))*Math.log(Constants.TOTDOCS/df);
        }

        return score;
    }

    
    
}
