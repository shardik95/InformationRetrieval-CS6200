import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import utilities.Constants;

/**
 * @author Gaurav Gandhi
 *
 */
public class PseudoRelevanceFeedback {
	
	private static HashMap<String, List<Posting>> invertedIndex;
	private static HashMap<String, Integer> documentLength;
	private static HashMap<String, Integer> queryVector;
	private static HashMap<String, Integer> relevantIndex;
	private static HashMap<String, Integer> nonRelevantIndex;
	private static HashMap<String, Double> scoreOfTerms;

	public static List<Query> performPseudoRelevanceFeedback(List<Query> system, HashMap<String, List<Posting>> invertedIndex1, HashMap<String, Integer> documentLength1) {
		
		invertedIndex = invertedIndex1;
		documentLength = documentLength1;
		return system.stream().map(query -> queryPRF(query)).collect(Collectors.toList());
	}
	
	private static Query queryPRF(Query query) {
		
		addTermsToQueryVector(query.query());
		addTermsToRelevantNonRelevantIndex(query);
		double magRel = calculateMagnitude(relevantIndex);
		double magNonRel = calculateMagnitude(nonRelevantIndex);
		generateScoreOfTermsForQuery(magRel, magNonRel);
		String queryString = getNewQuery(query);
		return new Query1(query.queryID(), queryString, query.listOfRelevantDocuments());
	}
	
	private static String getNewQuery(Query query) {
		
		StringBuilder newQuery = new StringBuilder(query.query());
		newQuery.append(" ");
		scoreOfTerms.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(20).forEach(object -> {
			
			if(!query.query().contains(object.getKey()))
				newQuery.append(object.getKey() + " ");
		});
		
		return newQuery.toString();
	}

	private static void generateScoreOfTermsForQuery(double magRel, double magNonRel) {
		
		scoreOfTerms = new HashMap<String, Double>();
		invertedIndex.keySet().stream().forEach(term -> {
			scoreOfTerms.put(term, calculateScore(term, magRel, magNonRel));
		});
	}

	private static Double calculateScore(String term, double magRel, double magNonRel) {
		
		return queryVector.get(term) + (0.5 / magRel) * relevantIndex.get(term) - (0.15 / magNonRel) * nonRelevantIndex.get(term);
	}

	private static void addTermToNonRelevantIndex(Query query) {
		
		
		List<String> docIDs = query.resultList().stream().limit(10).map(Result::docID).collect(Collectors.toList());
	}

	private static double calculateMagnitude(HashMap<String, Integer> relNonRelIndex) {
		
		return Math.sqrt(relNonRelIndex.entrySet().stream().map(x -> Math.pow(x.getValue(), 2)).reduce((a, b) -> a + b).get());
	}

	private static void addTermsToQueryVector(String query) {
		
		queryVector = new HashMap<String, Integer>();
		// Adding query terms
		String[] queryTerms = query.split(" ");
		for(String term: queryTerms) {
			
			if(queryVector.containsKey(term))
				queryVector.put(term, queryVector.get(term) + 1);
			else
				queryVector.put(term, 1);
		}
		// Adding terms in inverted index
		invertedIndex.keySet().stream().filter(term -> !queryVector.containsKey(term))
		.forEach(term -> queryVector.put(term, 0));
	}
	
	private static void addTermsToRelevantNonRelevantIndex(Query query) {
		
		relevantIndex = new HashMap<String, Integer>();
		nonRelevantIndex = new HashMap<String, Integer>();
		List<String> docIDs = query.resultList().stream().limit(10).map(Result::docID).collect(Collectors.toList());
		invertedIndex.entrySet().stream().forEach(term -> {
			term.getValue().stream().forEach(posting -> {
				addTermToRelevantIndex(term.getKey(), relevantIndex, posting, docIDs);
			});
		});
	}
	
	private static void addTermToRelevantIndex(String term, HashMap<String, Integer> relevantIndex, Posting posting, List<String> docIDs) {
		
		if(docIDs.contains(posting.docID())) {
			if(relevantIndex.containsKey(term))
				relevantIndex.put(term, relevantIndex.get(term) + posting.termFrequency());
			else
				relevantIndex.put(term, posting.termFrequency());
			if(!nonRelevantIndex.containsKey(term))
				nonRelevantIndex.put(term, 0);
		}
		else {
			if(!relevantIndex.containsKey(term))
				relevantIndex.put(term, 0);
			if(nonRelevantIndex.containsKey(term))
				nonRelevantIndex.put(term, nonRelevantIndex.get(term) + posting.termFrequency());
			else
				nonRelevantIndex.put(term, posting.termFrequency());
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws IOException {
		
		HashMap<String, List<Posting>> invertedIndexBase; // basic inverted index 
		HashMap<String, Integer> documentLengthBase; // basic document lengths
		List<HashMap> indexAndDocumentLength; // store inverted index and document length
		final String directoryRawCorpus = Constants.RAW_CORPUS_DIR; // directory of the raw corpus
		final String directoryParsedCorpus = Constants.PARSED_CORPUS_DIR; // directory of the parsed corpus
		final String fileQuery = Constants.QUERY_FILE; // path of query file
		List<Query> queryList = Queries.readQueriesFromFile(fileQuery); // list of queries to be executed
		
		// Parse the raw corpus
		Parser.parseAllFiles(3, directoryRawCorpus, directoryParsedCorpus);
		//Basic Inverted Index and document length
		final String directoryPathBase = Constants.PARSED_CORPUS_DIR; // parsed  documents directory (for use in invertedIndexBase and invertedIndexStop
		indexAndDocumentLength = Indexers.getInvertedIndexAndDocumentLength(1, directoryPathBase, false);
		invertedIndexBase = indexAndDocumentLength.get(0);
		documentLengthBase = indexAndDocumentLength.get(1);
		
		
		queryList = BM25Models.executeBM25ModelOnSystem(queryList, invertedIndexBase, documentLengthBase);
		List<Query> newQueryList = performPseudoRelevanceFeedback(queryList, invertedIndexBase, documentLengthBase);
		newQueryList.stream().forEach(query ->{
			System.out.println(query.query());
		});
	}
}
