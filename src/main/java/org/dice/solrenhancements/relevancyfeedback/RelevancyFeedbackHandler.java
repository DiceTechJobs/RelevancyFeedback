/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dice.solrenhancements.relevancyfeedback;

import com.google.common.base.Strings;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.*;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.FacetComponent;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.*;
import org.apache.solr.util.SolrPluginUtils;
import org.dice.solrenhancements.JarVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Solr RelevancyFeedback --
 *
 * Return similar documents either based on a single document or based on posted text.
 *
 * @since solr 1.3
 */
public class RelevancyFeedbackHandler extends RequestHandlerBase
{
    private final static String EDISMAX = ExtendedDismaxQParserPlugin.NAME;
    private String version = null;

    private static final Logger log = LoggerFactory.getLogger( RelevancyFeedbackHandler.class );


    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception
    {
        // set and override parameters
        SolrIndexSearcher searcher = req.getSearcher();
        SchemaField uniqueKeyField = searcher.getSchema().getUniqueKeyField();
        ModifiableSolrParams params = new ModifiableSolrParams(req.getParams());
        configureSolrParameters(req, params, uniqueKeyField.getName());

        // Set field flags
        ReturnFields returnFields = new SolrReturnFields( req );
        rsp.setReturnFields( returnFields );
        int flags = 0;
        if (returnFields.wantsScore()) {
            flags |= SolrIndexSearcher.GET_SCORES;
        }
        // note: set in configureSolrParameters
        String userQdefType = params.get(QueryParsing.DEFTYPE, EDISMAX);
        String rfDefType = params.get(RFParams.RF_DEFTYPE, EDISMAX);

        String userQ = params.get( CommonParams.Q );
        String rfQ = params.get(RFParams.RF_QUERY);

        Query rfQuery = null;
        Query userQuery = null;

        SortSpec sortSpec = null;
        QParser rfQueryParser = null;
        QParser userQueryParser = null;

        List<Query> targetFqFilters = null;
        List<Query> rfFqFilters    = null;

        try {
            if (rfQ != null) {
                rfQueryParser = QParser.getParser(rfQ, rfDefType, req);
                rfQuery = rfQueryParser.getQuery();
                sortSpec = rfQueryParser.getSort(true);
            }
            else{
                rfQueryParser = QParser.getParser(null, rfDefType, req);
                sortSpec = rfQueryParser.getSort(true);
            }

            targetFqFilters = getFilters(req, CommonParams.FQ);
            rfFqFilters    = getFilters(req, RFParams.FQ);
        } catch (SyntaxError e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        }

        try {
            if (userQ != null) {
                userQueryParser = QParser.getParser(userQ, userQdefType, req);
                userQuery = userQueryParser.getQuery();
            }

        } catch (SyntaxError e) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
        }

        RFHelper rfhelper = new RFHelper( params, searcher, uniqueKeyField, rfQueryParser );

        // Hold on to the interesting terms if relevant
        RFParams.TermStyle termStyle = RFParams.TermStyle.get(params.get(RFParams.INTERESTING_TERMS));

        RFResult RFResult = null;
        DocListAndSet rfDocs = null;

        // Parse Required Params
        // This will either have a single Reader or valid query
        Reader reader = null;
        try {
            int start = params.getInt(CommonParams.START, 0);
            int rows  = params.getInt(CommonParams.ROWS, 10);

            // for use when passed a content stream
            if (rfQ == null || rfQ.trim().length() < 1) {
                reader = getContentStreamReader(req, reader);
            }
            // Find documents RelevancyFeedback - either with a reader or a query
            // --------------------------------------------------------------------------------
            if (reader != null) {
                // this will only be initialized if used with a content stream (see above)
                rfQ = "NULL - from content stream";
                RFResult = rfhelper.getMatchesFromContentSteam(reader, start, rows, rfFqFilters, flags, sortSpec.getSort(), userQuery);
            } else if (rfQ != null) {
                // Matching options
                RFResult = getMatchesFromQuery(rsp, params, flags, rfQ, rfQuery, userQuery, sortSpec,
                        targetFqFilters, rfFqFilters, searcher, rfhelper,  start, rows);
            } else {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                        "RelevancyFeedback requires either a query (?rf.q=) or text (using stream.head and stream.body fields in a POST) to find similar documents.");
            }
            if(RFResult != null)
            {
                rfDocs = RFResult.getResults();
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        if( rfDocs == null ) {
            rfDocs = new DocListAndSet(); // avoid NPE
        }
        rsp.add( "response", rfDocs.docList );
        if(RFResult != null && RFResult.getQuery() != null) {
            rsp.add(RFParams.PREFIX + "query:", RFResult.getQuery().toString());
        }

        if( RFResult != null && termStyle != RFParams.TermStyle.NONE) {
            addInterestingTerms(rsp, termStyle, RFResult);
        }

        // maybe facet the results
        if (params.getBool(FacetParams.FACET,false)) {
            addFacet(req, rsp, params, rfDocs);
        }

        addDebugInfo(req, rsp, rfQ, rfFqFilters, rfhelper, RFResult, rfDocs);
    }

    private void configureSolrParameters(SolrQueryRequest req, ModifiableSolrParams params, String uniqueKeyField){

        // default to the the edismax parser
        String defType = params.get(QueryParsing.DEFTYPE, EDISMAX);
        // allow useage of custom edismax implementations, such as our own
        if(defType.toLowerCase().contains(EDISMAX.toLowerCase())){
            params.set(DisMaxParams.MM, 0);
            // edismax blows up without df field, even if you specify the field to match on in the query
            params.set(CommonParams.DF, uniqueKeyField);
        }
        params.set(QueryParsing.DEFTYPE, defType);
        req.setParams(params);
    }

    private Reader getContentStreamReader(SolrQueryRequest req, Reader reader) throws IOException {
        Iterable<ContentStream> streams = req.getContentStreams();
        if (streams != null) {
            Iterator<ContentStream> iter = streams.iterator();
            if (iter.hasNext()) {
                reader = iter.next().getReader();
            }
            if (iter.hasNext()) {
                throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                        "RelevancyFeedback does not support multiple ContentStreams");
            }
        }
        return reader;
    }

    private RFResult getMatchesFromQuery(SolrQueryResponse rsp, SolrParams params, int flags, String q, Query query, Query userQuery, SortSpec sortSpec, List<Query> targetFqFilters, List<Query> rfFqFilters, SolrIndexSearcher searcher, RFHelper rfHelper, int start, int rows) throws IOException, SyntaxError {

        boolean includeMatch = params.getBool(RFParams.MATCH_INCLUDE, true);
        int matchOffset = params.getInt(RFParams.MATCH_OFFSET, 0);
        // Find the base match
        DocList match = searcher.getDocList(query, targetFqFilters, null, matchOffset, 10000, flags); // only get the first one...
        if(match.matches() == 0 && userQuery == null){
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    String.format("RelevancyFeedback was unable to find any documents matching the query: '%s'.", q));
        }

        if (includeMatch) {
            rsp.add("match", match);
        }

        // This is an iterator, but we only handle the first match
        DocIterator iterator = match.iterator();
        if (iterator.hasNext() || userQuery != null) {
            // do a RelevancyFeedback query for each document in results
            return rfHelper.getMatchesFromDocs(iterator, start, rows, rfFqFilters, flags, sortSpec.getSort(), userQuery);
        }
        return null;
    }

    private List<InterestingTerm> extractInterestingTerms(List<RFTerm> RFTerms){
        List<InterestingTerm> terms = new ArrayList<InterestingTerm>();
        for( RFTerm term : RFTerms) {
            InterestingTerm it = new InterestingTerm();
            it.term = term.getTerm();
            it.boost = term.getFinalScore();
            terms.add(it);
        }
        Collections.sort(terms, InterestingTerm.BOOST_ORDER);
        return terms;
    }

    private void addInterestingTerms(SolrQueryResponse rsp, RFParams.TermStyle termStyle, RFResult RFResult) {

        List<RFTerm> RFTerms = RFResult.getRFTerms();
        Collections.sort(RFTerms, RFTerm.FLD_BOOST_X_SCORE_ORDER);

        if( termStyle == RFParams.TermStyle.DETAILS ) {
            List<InterestingTerm> interesting = extractInterestingTerms(RFResult.getRFTerms());

            int longest = 0;
            for( InterestingTerm t : interesting ) {
                longest = Math.max(t.term.toString().length(), longest);
            }

            NamedList<Float> it = new NamedList<Float>();
            for( InterestingTerm t : interesting ) {
                it.add( Strings.padEnd(t.term.toString(), longest, ' '), t.boost );
            }
            rsp.add( "interestingTerms", it );
        }
        else {
            List<String> it = new ArrayList<String>( RFTerms.size() );
            for( RFTerm RFTerm : RFTerms) {
                it.add(RFTerm.getWord());
            }
            rsp.add( "interestingTerms", it );
        }
    }

    private void addFacet(SolrQueryRequest req, SolrQueryResponse rsp, SolrParams params, DocListAndSet mltDocs) {
        if( mltDocs.docSet == null ) {
            rsp.add( "facet_counts", null );
        }
        else {
            FacetComponent fct = new FacetComponent();
            rsp.add( "facet_counts", fct.getFacetCounts(new SimpleFacets(req, mltDocs.docSet, params )) );
        }
    }

    private void addDebugInfo(SolrQueryRequest req, SolrQueryResponse rsp, String q, List<Query> rfFqFilters, RFHelper rfHelper, RFResult RFResult, DocListAndSet rfDocs) {

        boolean dbg = req.getParams().getBool(CommonParams.DEBUG_QUERY, false);
        boolean dbgQuery = false, dbgResults = false;
        if (dbg == false){//if it's true, we are doing everything anyway.
            String[] dbgParams = req.getParams().getParams(CommonParams.DEBUG);
            if (dbgParams != null) {
                for (int i = 0; i < dbgParams.length; i++) {
                    if (dbgParams[i].equals(CommonParams.QUERY)){
                        dbgQuery = true;
                    } else if (dbgParams[i].equals(CommonParams.RESULTS)){
                        dbgResults = true;
                    }
                }
            }
        } else {
            dbgQuery = true;
            dbgResults = true;
        }
        // Copied from StandardRequestHandler... perhaps it should be added to doStandardDebug?
        if (dbg == true && RFResult != null) {
            try {

                NamedList<String> it = getRFTermsForDebug(RFResult);

                NamedList<Object> dbgInfo = new NamedList<Object>();
                NamedList<Object> stdDbg = SolrPluginUtils.doStandardDebug(req, q, RFResult.getQuery(), rfDocs.docList, dbgQuery, dbgResults);
                if (null != dbgInfo) {
                    rsp.add("debug", dbgInfo);
                    dbgInfo.add( "RFTerms", it );
                    dbgInfo.addAll(stdDbg);

                    if (null != rfFqFilters) {
                        dbgInfo.add("filter_queries",req.getParams().getParams(CommonParams.FQ));
                        List<String> fqs = new ArrayList<String>(rfFqFilters.size());
                        for (Query fq : rfFqFilters) {
                            fqs.add(QueryParsing.toString(fq, req.getSchema()));
                        }
                        dbgInfo.add("rf_filter_queries",fqs);
                    }
                }
            } catch (Exception e) {
                SolrException.log(log, "Exception during debug", e);
                rsp.add("exception_during_debug", SolrException.toStr(e));
            }
        }
    }

    private NamedList<String> getRFTermsForDebug(RFResult rfResult) {
        NamedList<String> it = new NamedList<String>();
        if(rfResult == null){
            return it;
        }

        List<RFTerm> RFTerms = rfResult.getRFTerms();
        Collections.sort(RFTerms);
        int longestWd = 0;
        int longestFieldName = 0;
        for( RFTerm RFTerm : RFTerms) {
            longestWd = Math.max(RFTerm.getWord().length(), longestWd);
            longestFieldName = Math.max(RFTerm.getFieldName().length(), longestFieldName);
        }
        for( RFTerm RFTerm : RFTerms) {
            String paddedfieldName = Strings.padEnd(RFTerm.getFieldName(), longestFieldName, ' ');
            String paddedWd = Strings.padEnd(RFTerm.getWord(), longestWd, ' ');
            it.add(paddedfieldName, paddedWd + " - " + RFTerm.valuesToString() );
        }
        return it;
    }

    private List<Query> getFilters(SolrQueryRequest req, String param) throws SyntaxError {
        String[] fqs = req.getParams().getParams(param);
        if (fqs!=null && fqs.length!=0) {
            List<Query> filters = new ArrayList<Query>();
            for (String fq : fqs) {
                if (fq != null && fq.trim().length()!=0) {
                    QParser fqp = QParser.getParser(fq, null, req);
                    filters.add(fqp.getQuery());
                }
            }
            return filters;
        }
        return new ArrayList<Query>();
    }

    //////////////////////// SolrInfoMBeans methods //////////////////////

    @Override
    public String getDescription() {
        return "Dice custom RelevancyFeedback handler";
    }

    @Override
    public String getSource() {
        return "$URL$";
    }

    @Override
    public String getVersion(){

        if (version != null && version.length() > 0){
            return version;
        }
        version = JarVersion.getVersion(log);
        return version;
    };


    @Override
    public URL[] getDocs() {
        try {
            return new URL[] { new URL("http://wiki.apache.org/solr/RelevancyFeedback") };
        }
        catch( MalformedURLException ex ) { return null; }
    }


}