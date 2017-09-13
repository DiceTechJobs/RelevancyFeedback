package org.dice.solrenhancements.relevancyfeedback;

import org.apache.lucene.search.Query;
import org.apache.solr.search.DocListAndSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 11/25/14.
 */
public class RFResult {

    public final List<RFTerm> RFTerms;
    public Query rawRFQuery;

    public Query finalQuery;
    private Query mustMatchQuery = null;
    private Query mustNOTMatchQuery = null;

    private DocListAndSet doclist;

    public RFResult(List<RFTerm> RFTerms, Query rawRFQuery){

        this.RFTerms = RFTerms == null? new ArrayList<RFTerm>() : RFTerms;
        this.rawRFQuery = rawRFQuery;
    }

    public DocListAndSet getDoclist() {
        return doclist;
    }

    public void setDoclist(DocListAndSet doclist) {
        this.doclist = doclist;
    }

    public Query getMustMatchQuery(){
        return this.mustMatchQuery;
    }

    public void setMustMatchQuery(Query query){
        this.mustMatchQuery = query;
    }

    public Query getMustNOTMatchQuery(){
        return this.mustNOTMatchQuery;
    }

    public void setMustNOTMatchQuery(Query query){
        this.mustNOTMatchQuery = query;
    }

    public Query getFinalQuery() {
        return finalQuery;
    }

    public void setFinalQuery(Query finalQuery) {
        this.finalQuery = finalQuery;
    }
}
