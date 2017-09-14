Dice Relevancy Feedback
========================

Dice.com's solr plugins for performing personalized search, and recommendations (via the relevancy feedback plugin) and conceptual / semantic search (via the unsupervised feedback plugin).

## Building the Plugin
A pre-built jar file can be found in the ```./target``` folder. The project contains a maven pom.xml file which can also be used to build it from source.

## Supported Solr versions
Current branch is compiled for Solr 5.4. I will be adding branches compiled against Solr 6 versions shortly. If there is a particular version of Solr you need this for, please create a GitHub issue and I'll see what I can do.
To manually compile it for a later version, use maven to compile the plugins using the pom.xml file, and update the versions of the solr and lucene libraries in that file, and use maven to pull in those dependencies. Then fix any compilation errors. Later versions of solr (6.x) use query builders to build the boolean queries, which is likely what needs to be fixed to support 6.x.
 
## Importing into SOLR
Please see the official SOLR guidelines for registering plugins with solr. This basically involves simply dropping the jar file into one of the folders that Solr checks for class and jar files on core reload.  

- [Solr Plugins](https://wiki.apache.org/solr/SolrPlugins)
- [Adding custom plugins in Solr cloud](https://lucene.apache.org/solr/guide/6_6/adding-custom-plugins-in-solrcloud-mode.html)
 
# Relevancy Feedback Plugin
An **example request handler configuration** for the solrconfig.xml is shown below, with comments outlining the main parameters:
```$xml
<requestHandler name="/rf" class="org.dice.solrenhancements.relevancyfeedback.RelevancyFeedbackHandler">
        <lst name="defaults">
            <str name="omitHeader">true</str>
            <str name="wt">json</str>
            <str name="indent">true</str>			
	
            <!-- Regular query configuration - query parser used when parsing the rf query-->
            <str  name="defType">lucene</str>
            
            <!-- fields returned -->
            <str  name="fl">jobTitle,skill,company</str>
            <!-- fields to match on-->
            <str  name="rf.fl">skillFromSkill,extractTitles</str>
            
            <!-- field weights. Note that term weights are normalized so that each field is weighted exactly in this ratio
            as different fields can get different numbers of matching terms-->
            <str  name="rf.qf">skillFromSkill^3 extractTitles^4.5</str>
            
            <int  name="rows">10</int>

            <int  name="rf.maxflqt">10</int>
            <bool name="rf.boost">true</bool>
            
            <!-- normalize the weights for terms in each field (custom to dice MLT) -->
            <bool name="rf.normflboosts">true</bool>
            <bool name="rf.logtf">true</bool>
            
            <!-- Minimum should match settings for the rf query - determines what proportion of the terms have to match -->
            <!-- See Solr edismax mm parameter for specifics --> 
            <bool name="rf.mm">25%</bool>            
            <str  name="rf.interestingTerms">details</str>
            
            <!-- Turns the rf query into a boost query using a multiplicative boost, allowing for boosting -->
            <str  name="rf.boostfn"></str>
            
            <!-- q parameter -  If you want to execute one query, and use the rf query to boost the results (e.g. for personalizing search), 
            	 pass the user's query into this parameter. Can take regular query syntax e.g.rf.q={!edismax df=title qf=.... v=$qq}&qq=Java
            	 The regular q parameter is reserved for the rf query (see abpve)
            -->
            <str name="q"></str>

            <!-- rf.q parameter - Note that the regular q parameter is used only for personalized search scenarios, where you have a main query
            	 and you want to use the rf query generated to boost the main queries documents. Typically the rf.q query is a query that identifies
            	 one or more documents, e.g. rf.q=(id:686867 id:98928980 id:999923). But it cam be any query. Note that it will process ALL documents
            	 matched by the q query (as it's intended mainly for looking up docs by id), so use cautiously.
            -->
            <str name="rf.q"></str>
            <!-- query parser to use for the rf.q query -->
            <str name="rf.defType"></str>
            
            <!-- Settings for personalized search - use the regular parameter names for the query parser defined by rf.defType parameter -->
            <str name="df">title</str>
            <str name="qf"> company_text^0.01 title^12 skill^4 description^0.3</str>
            <str name="pf2">company_text^0.01 title^12 skill^4 description^0.6</str> 
            
            <!-- Fields used for processing documents posted to the stream.body and stream.head parameters in a POST call -->
			<str  name="stream.head.fl">title,title_syn</str>
            <str  name="stream.body.fl">extractSkills,extractTitles</str>
            
            <!-- Specifies the separate set of field weights to apply when procesing a document posted to the request handler via the 
                 stream.body and stream.head parameters -->
            <str  name="stream.qf">extractSkills^4.5 extractTitles^2.25 title^3.0 title_syn^3.0</str>           
        </lst>
</requestHandler>
```
#### Example Request
[http://localhost:8983/solr/Jobs/rf?q=id:11f407d319d6cc707437fad874a097c0+id:a2fd2f2e34667d61fadcdcabfd359cf4&rows=10&df=title&fl=title,skills,geoCode,city,state&wt=json](http://localhost:8983/solr/Jobs/rf?q=id:11f407d319d6cc707437fad874a097c0+id:a2fd2f2e34667d61fadcdcabfd359cf4&rows=10&df=title&fl=title,skills,geoCode,city,state&wt=json)

#### Example Response
```$json
{
  "match":{
      "numFound":2,
      "start":0,
      "docs":[
          {
            "id":"a2fd2f2e34667d61fadcdcabfd359cf4",        
            "title":"Console AAA Sports Video Game Programmer.",
            "skills":["Sports Game Experience a plus.",
              "2-10 years plus Console AAA Video Game Programming Experience"],
            "geocode":"38.124447,-122.55051",
            "city":"Novato",
            "state":"CA"
          },
          {
            "id":"11f407d319d6cc707437fad874a097c0",
            "title":"Game Engineer - Creative and Flexible Work Environment!",
            "skills":["3D Math",
              "Unity3d",
              "C#",
              "3D Math - game programming",
              "game programming",
              "C++",
              "Java"],
            "geocode":"33.97331,-118.243614",
            "city":"Los Angeles",
            "state":"CA"
          }
      ]
  },
  "response":{
      "numFound":5333,
      "start":0,
      "docs":[
          {
            "title":"Software Design Engineer 3 (Game Developer)",
            "skills":["C#",
              "C++",
              "Unity"],
            "geocode":"47.683647,-122.12183",
            "city":"Redmond",
            "state":"WA"
          },
          {          
            "title":"Game Server Engineer - MMO Mobile Gaming Start-Up!",
            "skills":["AWS",
              "Node.JS",
              "pubnub",
              "Websockets",
              "pubnub - Node.JS",
              "Vagrant",
              "Linux",
              "Git",
              "MongoDB",
              "Jenkins",
              "Docker"],
            "geocode":"37.777115,-122.41733",
            "city":"San Francisco",
            "state":"CA"
          },...
      ]
   }
}
```

# Unsupervised Feedback (Blind Feedback) Plugin
An example request handler configuration for the solrconfig.xml is shown below, with comments outlining the main parameters:
```$xml
 <requestHandler name="/ufselect" class="org.dice.solrenhancements.unsupervisedfeedback.DiceUnsupervisedFeedbackHandler">
        <lst name="defaults">
            <str name="omitHeader">true</str>
            <str name="wt">json</str>
            <str name="indent">true</str>

            <!-- Regular query configuration -->
            <str  name="defType">edismax</str>
            <str  name="df">title</str>
            <str  name="qf">title^1.5   skills^1.25 description^1.1</str>
            <str  name="pf2">title^3.0  skills^2.5  description^1.5</str>
            <str  name="mm">1</str>
            <str  name="q.op">OR</str>

            <str  name="fl">jobTitle,skills,company</str>
            <int  name="rows">30</int>
                        
            <!-- Unsupervised Feedback (Blind Feedback) query configuration-->
            <str  name="uf.fl">skillsFromskills,titleFromJobTitle</str>
            <!-- How many docs to extract the top terms from -->
            <str  name="uf.maxdocs">50</str>
            <!-- How many terms to extract per field (max) -->
            <int  name="uf.maxflqt">10</int>
            <bool name="uf.boost">true</bool>
            <!-- Relative per-field boosts on the extracted terms (similar to edismax qf parameter -->
            <!-- NOTE: with  uf.normflboosts=true, all terms are normalized so that the total importance of each
            	field on the query is the same, then these relative boosts are applied per field-->
            
            <str  name="uf.qf">skillsFromskills^4.5 titleFromJobTitle^6.0</str>
            
            <!-- Returns the top k terms (see regular solr MLT handler) -->
            <str  name="uf.interestingTerms">details</str>
			
            <!-- unit-length norm all term boosts within a field (recommended) - see talk for details -->
            <bool name="uf.normflboosts">true</bool>
            <!-- use raw term clounts or log term counts? -->
            <bool name="uf.logtf">false</bool>
        </lst>
</requestHandler>
```
#### Example Request
[http://localhost:8983/solr/DiceJobsCP/ufselect?q=Machine+Learning+Engineer&start=0&rows=10&uf.logtf=false&fl=title,skills,geoCode,city,state&fq={!geofilt+sfield=jobEndecaGeoCode+d=48+pt=39.6955,-105.0841}&wt=json](http://localhost:8983/solr/DiceJobsCP/ufselect?q=Machine+Learning+Engineer&start=0&rows=10&uf.logtf=false&fl=title,skills,geoCode,city,state&fq={!geofilt+sfield=jobEndecaGeoCode+d=48+pt=39.6955,-105.0841}&wt=json)

#### Example Response
```$json
{
  "match":
  {
    "numFound":7729,
    "start":0,
    "docs":[
      {
        "title":"NLP/Machine Learning Engineer",
        "skills":["Linux",
          "NLP (Natural Language Processing)",
          "SQL",
          "Bash",
          "Python",
          "ML (Machine Learning)",
          "JavaScript",
          "Java"],
        "geocode":"42.35819,-71.050674",
        "city":"Boston",
        "state":"MA"
      },
      {
        "title":"Machine Learning Engineer",
        "skills":["machine learning",
          "java",
          "scala"],
        "geocode":"47.60473,-122.32594",
        "city":"Seattle",
        "state":"WA"
      },
      {
        "title":"Machine Learning Engineer - REMOTE!",
        "skills":["Neo4j",
          "Hadoop",
          "gensim",
          "gensim - C++",
          "Java",
          "R",
          "MongoDB",
          "elastic search",
          "sci-kit learn",
          "Python",
          "C++"],
        "geocode":"37.777115,-122.41733",
        "city":"San Francisco",
        "state":"CA"
        },...
    ]
}
```