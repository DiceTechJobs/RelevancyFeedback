package org.dice.solrenhancements.relevancyfeedback;

import org.apache.lucene.queries.payloads.AveragePayloadFunction;
import org.apache.lucene.queries.payloads.PayloadScoreQuery;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.solr.util.SolrPluginUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by simon.hughes on 11/25/14.
 */
public class RFQuery {

    private final List<RFTerm> RFTerms;
    private final String mm;
    private BooleanQuery mustMatchQuery = null;
    private BooleanQuery mustNOTMatchQuery = null;

    public RFQuery(List<RFTerm> RFTerms, String mm){
        this.RFTerms = RFTerms == null? new ArrayList<RFTerm>() : RFTerms;
        this.mm = mm;
    }
    public BooleanQuery getMustMatchQuery(){
        return this.mustMatchQuery;
    }

    public void setMustMatchQuery(BooleanQuery query){
        this.mustMatchQuery = query;
    }

    public Query getMustNOTMatchQuery(){
        return this.mustNOTMatchQuery;
    }

    public void setMustNOTMatchQuery(BooleanQuery query){
        this.mustNOTMatchQuery = query;
    }

    public List<RFTerm> getRFTerms(){
        return RFTerms;
    }

    public Query getOrQuery(){
        BooleanQuery.Builder qryBuilder = new BooleanQuery.Builder();
        for(RFTerm RFTerm : this.RFTerms){
            qryBuilder.add(toBoostedQuery(RFTerm), BooleanClause.Occur.SHOULD);
        }
        SolrPluginUtils.setMinShouldMatch(qryBuilder, mm);
        return qryBuilder.build();
    }

    private Query toBoostedQuery(RFTerm RFTerm){
        Query tq = toTermQuery(RFTerm);
        return new BoostQuery(tq, RFTerm.getFinalScore());
    }

    private Query toTermQuery(RFTerm RFTerm) {
        if(RFTerm.hasPayload()) {
            return new PayloadScoreQuery(new SpanTermQuery(RFTerm.getTerm()), new AveragePayloadFunction(), false);
        }
        else{
            return new TermQuery(RFTerm.getTerm());
        }
    }
}
