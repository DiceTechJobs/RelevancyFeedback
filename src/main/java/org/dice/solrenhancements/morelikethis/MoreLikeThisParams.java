package org.dice.solrenhancements.morelikethis;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.search.QueryParsing;

import java.util.Locale;

/**
 * Created by simon.hughes on 9/4/14.
 */
public interface MoreLikeThisParams {
    java.lang.String MLT = "mlt";
    java.lang.String PREFIX = "mlt.";
    java.lang.String SIMILARITY_FIELDS = PREFIX + "fl";
    java.lang.String MIN_TERM_FREQ =PREFIX + "mintf";
    java.lang.String MAX_DOC_FREQ = PREFIX + "maxdf";
    java.lang.String MIN_DOC_FREQ = PREFIX + "mindf";
    java.lang.String MIN_WORD_LEN = PREFIX + "minwl";
    java.lang.String MAX_WORD_LEN = PREFIX + "maxwl";
    // don't clash with regular mm
    java.lang.String MM = PREFIX + "mm";
    //Changed from maxqt
    java.lang.String MAX_QUERY_TERMS_PER_FIELD = PREFIX + "maxflqt";
    //Changed from maxntp
    java.lang.String MAX_NUM_TOKENS_PARSED_PER_FIELD = PREFIX + "maxflntp";
    java.lang.String BOOST = PREFIX + "boost";
    java.lang.String FQ = PREFIX + "fq";

    java.lang.String QF = PREFIX + "qf";

    // allows user to specify a query, and we use the MLT terms to boost that query
    java.lang.String MLT_QUERY = PREFIX + "q";
    java.lang.String MLT_DEFTYPE = PREFIX + QueryParsing.DEFTYPE;

    // new to this plugin
    java.lang.String FL_MUST_MATCH      = PREFIX + "fl.match";   // list of fields that must match the target document
    java.lang.String FL_MUST_NOT_MATCH  = PREFIX + "fl.different";   // list of fields that must NOT match the target document

    java.lang.String BOOST_FN = PREFIX + "boostfn";
    java.lang.String PAYLOAD_FIELDS = PREFIX + "payloadfl";

    // normalize field boosts
    java.lang.String NORMALIZE_FIELD_BOOSTS = PREFIX + "normflboosts";
    java.lang.String IS_LOG_TF = PREFIX + "logtf";

    java.lang.String STREAM_HEAD    = "stream.head";
    java.lang.String STREAM_HEAD_FL = "stream.head.fl";
    java.lang.String STREAM_BODY_FL = "stream.body.fl";

    java.lang.String STREAM_QF = "stream.qf";
    // end new to this plugin

    // the /mlt request handler uses 'rows'
    public final static String DOC_COUNT = PREFIX + "count";

    // Do you want to include the original document in the results or not
    public final static String MATCH_INCLUDE = PREFIX + "match.include";

    // If multiple docs are matched in the query, what offset do you want?
    public final static String MATCH_OFFSET  = PREFIX + "match.offset";

    // Do you want to include the original document in the results or not
    public final static String INTERESTING_TERMS = PREFIX + "interestingTerms";  // false,details,(list or true)

    public enum TermStyle {
        NONE,
        LIST,
        DETAILS;

        public static TermStyle get( String p )
        {
            if( p != null ) {
                p = p.toUpperCase(Locale.ROOT);
                if( p.equals( "DETAILS" ) ) {
                    return DETAILS;
                }
                else if( p.equals( "LIST" ) ) {
                    return LIST;
                }
            }
            return NONE;
        }
    }
}