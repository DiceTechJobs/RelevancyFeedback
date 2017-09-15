package org.dice.solrenhancements.relevancyfeedback;

import org.apache.lucene.index.Term;

import java.util.Comparator;

/**
 * Created by simon.hughes on 9/2/14.
 */
public class InterestingTerm
{
    public String term;
    public float boost;

    public static Comparator<InterestingTerm> BOOST_ORDER = new Comparator<InterestingTerm>() {
        @Override
        public int compare(InterestingTerm t1, InterestingTerm t2) {
            float d = t1.boost - t2.boost;
            if( d == 0 ) {
                return 0;
            }
            return (d>0)?-1:1;
        }
    };
}