package org.dice.solrenhancements.relevancyfeedback;

import org.apache.lucene.search.Query;
import org.apache.solr.search.DocListAndSet;

import java.util.List;

/**
 * Created by simon.hughes on 1/6/17.
 */
public class RFResult {
    private final List<RFTerm> RFTerms;
    private final Query finalRfQuery;
    private DocListAndSet results;

    public RFResult(List<RFTerm> RFTerms, Query finalRfQuery, DocListAndSet results){
        this.RFTerms = RFTerms;
        this.finalRfQuery = finalRfQuery;
        this.results = results;
    }

    public DocListAndSet getResults() {
        return results;
    }

    public List<RFTerm> getRFTerms(){
        return RFTerms;
    }

    public Query getQuery() {
        return finalRfQuery;
    }
}
