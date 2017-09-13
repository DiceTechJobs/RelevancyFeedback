package org.dice.solrenhancements.relevancyfeedback;

/**
 * Created by simon.hughes on 9/2/14.
 */

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.*;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.*;
import org.apache.solr.util.SolrPluginUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class for RelevancyFeedback that can be called from other request handlers
 */
public class RFHelper
{
    // Pattern is thread safe -- TODO? share this with general 'fl' param
    private static final Pattern splitList = Pattern.compile(",| ");

    final SolrIndexSearcher searcher;
    final QParser qParser;
    final RelevancyFeedback rf;
    final IndexReader reader;
    final SchemaField uniqueKeyField;
    final boolean needDocSet;

    public RFHelper(SolrParams params, SolrIndexSearcher searcher, SchemaField uniqueKeyField, QParser qParser )
    {
        this.searcher = searcher;
        this.qParser = qParser;
        this.reader = searcher.getIndexReader();
        this.uniqueKeyField = uniqueKeyField;
        this.needDocSet = params.getBool(FacetParams.FACET, false);

        SolrParams required = params.required();
        String[] fields = splitList.split(required.get(RFParams.SIMILARITY_FIELDS));
        if( fields.length < 1 ) {
            throw new SolrException( SolrException.ErrorCode.BAD_REQUEST,
                    "RelevancyFeedback requires at least one similarity field: "+ RFParams.SIMILARITY_FIELDS );
        }

        this.rf = new RelevancyFeedback( reader );
        rf.setFieldNames(fields);

        final String flMustMatch = params.get(RFParams.FL_MUST_MATCH);
        if( flMustMatch != null && flMustMatch.trim().length() > 0 ) {
            String[] mustMatchFields = splitList.split(flMustMatch.trim());
            rf.setMatchFieldNames(mustMatchFields);
        }

        final String flMustNOTMatch = params.get(RFParams.FL_MUST_NOT_MATCH);
        if( flMustNOTMatch != null && flMustNOTMatch.trim().length() > 0 ) {
            String[] differntMatchFields = splitList.split(flMustNOTMatch.trim());
            rf.setDifferentFieldNames(differntMatchFields);
        }

        String[] payloadFields = getFieldList(RFParams.PAYLOAD_FIELDS, params);
        if(payloadFields != null){
            rf.setPayloadFields(payloadFields);
        }
        rf.setAnalyzer( searcher.getSchema().getIndexAnalyzer() );

        // configurable params

        rf.setMm(                params.get(RFParams.MM,                       RelevancyFeedback.DEFAULT_MM));
        rf.setMinTermFreq(       params.getInt(RFParams.MIN_TERM_FREQ,         RelevancyFeedback.DEFAULT_MIN_TERM_FREQ));
        rf.setMinDocFreq(        params.getInt(RFParams.MIN_DOC_FREQ,          RelevancyFeedback.DEFAULT_MIN_DOC_FREQ));
        rf.setMaxDocFreq(        params.getInt(RFParams.MAX_DOC_FREQ,          RelevancyFeedback.DEFAULT_MAX_DOC_FREQ));
        rf.setMinWordLen(        params.getInt(RFParams.MIN_WORD_LEN,          RelevancyFeedback.DEFAULT_MIN_WORD_LENGTH));
        rf.setMaxWordLen(        params.getInt(RFParams.MAX_WORD_LEN,          RelevancyFeedback.DEFAULT_MAX_WORD_LENGTH));

        rf.setBoost(             params.getBool(RFParams.BOOST, true ) );

        // new parameters
        rf.setBoostFn(params.get(RFParams.BOOST_FN));
        rf.setNormalizeFieldBoosts(params.getBool(RFParams.NORMALIZE_FIELD_BOOSTS, RelevancyFeedback.DEFAULT_NORMALIZE_FIELD_BOOSTS));
        // new versions of previous parameters moved to the field level
        rf.setMaxQueryTermsPerField(params.getInt(RFParams.MAX_QUERY_TERMS_PER_FIELD, RelevancyFeedback.DEFAULT_MAX_QUERY_TERMS_PER_FIELD));
        rf.setMaxNumTokensParsedPerField(params.getInt(RFParams.MAX_NUM_TOKENS_PARSED_PER_FIELD, RelevancyFeedback.DEFAULT_MAX_NUM_TOKENS_PARSED_PER_FIELD));
        rf.setLogTf(params.getBool(RFParams.IS_LOG_TF, RelevancyFeedback.DEFAULT_IS_LOG_TF));

        rf.setBoostFields(SolrPluginUtils.parseFieldBoosts(params.getParams(RFParams.QF)));
        rf.setStreamBoostFields(SolrPluginUtils.parseFieldBoosts(params.getParams(RFParams.STREAM_QF)));

        String streamHead = params.get(RFParams.STREAM_HEAD);
        if(streamHead != null) {
            rf.setStreamHead(streamHead);
        }

        // Set stream fields
        String[] streamHeadFields = getFieldList(RFParams.STREAM_HEAD_FL, params);
        if(streamHeadFields != null){
            rf.setStreamHeadfieldNames(streamHeadFields);
        }

        String[] streamBodyFields = getFieldList(RFParams.STREAM_BODY_FL, params);
        if(streamBodyFields != null){
            rf.setStreamBodyfieldNames(streamBodyFields);
        }
    }

    private String[] getFieldList(String key, SolrParams params) {
        final String fieldList = params.get(key);
        if(fieldList != null && fieldList.trim().length() > 0) {
            String[] fields = splitList.split(fieldList);
            if(fields != null){
                return fields;
            }
        }
        return null;
    }

    private Query rawRFQuery;
    private Query boostedRFQuery;
    private BooleanQuery realRFQuery;

    public Query getRawRFQuery(){
        return rawRFQuery;
    }

    public Query getBoostedRFQuery(){
        return boostedRFQuery;
    }

    public Query getRealRFQuery(){
        return realRFQuery;
    }

    private Query getBoostedFunctionQuery(Query q) throws SyntaxError{

        if (rf.getBoostFn() == null || rf.getBoostFn().trim().length() == 0) {
            return q;
        }

        Query boost = this.qParser.subQuery(rf.getBoostFn(), FunctionQParserPlugin.NAME).getQuery();
        ValueSource vs;
        if (boost instanceof FunctionQuery) {
            vs = ((FunctionQuery) boost).getValueSource();
        } else {
            vs = new QueryValueSource(boost, 1.0f);
        }
        return new BoostedQuery(q, vs);
    }

    public RFResult getMatchesFromDocs(DocIterator iterator, int start, int rows, List<Query> filters, int flags, Sort lsort, Query userQuery) throws IOException, SyntaxError
    {
        realRFQuery = new BooleanQuery();
        List<Integer> ids = new ArrayList<Integer>();

        while(iterator.hasNext()) {
            int id = iterator.nextDoc();
            Document doc = reader.document(id);
            ids.add(id);

            // add exclusion filters to prevent matching seed documents
            TermQuery tq = new TermQuery(new Term(uniqueKeyField.getName(), uniqueKeyField.getType().storedToIndexed(doc.getField(uniqueKeyField.getName()))));
            realRFQuery.add(tq, BooleanClause.Occur.MUST_NOT);
        }

        RFResult RFResult = rf.like(ids);
        rawRFQuery = RFResult.rawRFQuery;
        if(RFResult.getMustMatchQuery() != null){
            filters.add(RFResult.getMustMatchQuery());
        }
        if(RFResult.getMustNOTMatchQuery() != null){
            filters.add(RFResult.getMustNOTMatchQuery());
        }

        boostedRFQuery = getBoostedFunctionQuery(rawRFQuery);
        realRFQuery.add(boostedRFQuery, BooleanClause.Occur.MUST);

        BooleanQuery finalQuery = null;

        if(userQuery != null){
            finalQuery = new BooleanQuery();
            finalQuery.add(userQuery, BooleanClause.Occur.MUST);
            finalQuery.add(realRFQuery, BooleanClause.Occur.SHOULD);
        }
        else{
            finalQuery = realRFQuery;
        }
        RFResult.setFinalQuery(finalQuery);

        DocListAndSet results = new DocListAndSet();
        if (this.needDocSet) {
            results = searcher.getDocListAndSet(finalQuery, filters, lsort, start, rows, flags);
        } else {
            results.docList = searcher.getDocList(finalQuery, filters, lsort, start, rows, flags);
        }
        RFResult.setDoclist(results);
        return RFResult;
    }


    public RFResult getMatchesFromContentSteam(Reader reader, int start, int rows, List<Query> filters, int flags, Sort lsort, Query userQuery) throws IOException, SyntaxError
    {
        RFResult RFResult = rf.like(reader);
        rawRFQuery = RFResult.rawRFQuery;

        boostedRFQuery = getBoostedFunctionQuery(rawRFQuery);

        Query finalQuery = null;
        if(userQuery != null){
            BooleanQuery tmpQuery = new BooleanQuery();
            tmpQuery .add(userQuery, BooleanClause.Occur.MUST);
            tmpQuery .add(boostedRFQuery, BooleanClause.Occur.SHOULD);
            finalQuery = tmpQuery;
        }
        else{
            finalQuery = boostedRFQuery;
        }
        RFResult.setFinalQuery(finalQuery);

        DocListAndSet results = new DocListAndSet();
        if (this.needDocSet) {
            results =   searcher.getDocListAndSet( finalQuery, filters, lsort, start, rows, flags);
        } else {
            results.docList = searcher.getDocList( finalQuery, filters, lsort, start, rows, flags);
        }
        RFResult.setDoclist(results);
        return RFResult;
    }

    public RelevancyFeedback getRelevancyFeedback()
    {
        return rf;
    }
}


