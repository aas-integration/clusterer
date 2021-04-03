package clusterer;

import com.google.common.base.Verify;
import com.google.common.collect.Iterables;
import com.vesperin.text.Corpus;
import com.vesperin.text.Introspector;
import com.vesperin.text.Recommend;
import com.vesperin.text.Selection.Word;
import com.vesperin.text.spi.BasicExecutionMonitor;
import com.vesperin.text.tokenizers.Tokenizers;
import com.vesperin.text.tokenizers.WordsTokenizer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import soot.*;
import soot.util.ArraySet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ClusterGenerator {

	private static final String BLANK = "";

	public static void main(String[] args) {
		Options options = Options.v();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			parser.printUsage(System.err);
			return;
		} catch (Throwable t) {
			throw t;
		} finally {
			Options.resetInstance();
			soot.G.reset();
		}

		List<String> directories = options.classDirList;

		if (directories == null || directories.isEmpty()) {
			System.err.println("No input directories found.");
			parser.printUsage(System.err);
			return;
		}

		SceneLoader.loadFromClassDirs(directories, ".");

		Set<String> dict = getEnglishDict();
		Verify.verify(dict.contains("vector"));
		Verify.verify(dict.contains("matrix"));

		dict.add("box");// Verify.verify(dict.contains("box"));

		Verify.verify(dict.contains("sphere"));
		Verify.verify(dict.contains("cube"));
		Verify.verify(dict.contains("cylinder"));
		Verify.verify(dict.contains("capsule"));

		if(Options.v().verbose){ BasicExecutionMonitor.get().enable(); } else {
			BasicExecutionMonitor.get().disable();
		}

		Set<String> ignoreWords = new HashSet<String>(Arrays.asList(new String[] { "package" }));

		File outFile = new File(options.outFileName);
		switch (options.clusteringStrategy) {
		case 1: {
			writeToJson(strategy1(ignoreWords, dict), outFile);
			break;
		}
		case 2: {
			writeToJson(strategy2(ignoreWords, dict), outFile);
			break;
		}
		case 3: {
			writeToJson(strategy3(ignoreWords, dict), outFile);
			break;
		}
		case 4: {
			writeToJson(strategy4(ignoreWords, dict), outFile);
			break;
		}
		case 5: {
			writeToJson(strategy5(ignoreWords, dict), outFile);
			break;
		}
		}

		
		if (options.classInfoFileName !=null) {
			try (PrintWriter writer = new PrintWriter(new File(options.classInfoFileName), "UTF-8")) {
				writer.println("{\"classinfo\": [");
				String comma1 = "";				
				for (SootClass sc : Scene.v().getApplicationClasses()) {
					if (sc.resolvingLevel() < SootClass.SIGNATURES) continue;
					writer.print(comma1);
					writer.println("  {\"name\" : \"" + sc.getName() +"\",");
					
					if (sc.hasSuperclass()) {
						writer.println("\t\"super\" : \"" + sc.getSuperclass() +"\",");
					}
					
					writer.print("\t\"interfaces\" : [");
					String comma2="";
					for (SootClass interf : sc.getInterfaces()) {
						writer.print(comma2);
						writer.print("\""+interf.getName()+"\"");
						comma2 = ",\n\t\t";
					}
					writer.println("],");

					
					writer.print("\t\"fields\" : [");
					comma2="";
					for (SootField sf : sc.getFields()) {
						writer.print(comma2);
						writer.print("{\"name\" : ");
						writer.print("\""+sf.getName()+"\",\n\t\t");
						writer.print("\"type\" : ");
						writer.print("\""+sf.getType()+"\"}");

						comma2 = ",\n\t\t";
					}
					writer.println("],");

					writer.println("\t\"methods\" : [");
					comma2="";
					for (SootMethod sm : sc.getMethods()) {
						writer.print(comma2);
						sootMethodToJson(sm, writer, "\t\t");
						comma2 = ",\n\t\t";
					}
					writer.println("]");

					writer.println("}");
					comma1 = ",\n";
				}				
				writer.println("]}");
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}
	
		
		if (options.classFieldMapFileName != null) {
			/*
			 * For each SootClass that is not a library class,
			 * create a map entry that maps from this class to
			 * all fields in the scene that are of that type.
			 * This is later used to identify clusters of names
			 * of the same type. E.g.:
			 * 
			 * Vector3f -> [Body.position, Material.color, Ray.direction]
			 */
			File mapFile = new File(options.classFieldMapFileName);

			Map<String, Collection<SootField>> fieldsOfType = new HashMap<String, Collection<SootField>>();

			for (SootClass sc : Scene.v().getApplicationClasses()) {
				if (sc.resolvingLevel() >= SootClass.SIGNATURES) {
					for (SootField sf : sc.getFields()) {
						// ignore this referneces.
						if (!sf.getName().startsWith("this")) {
							String key = sf.getType().toString();
							if (sf.getType() instanceof RefType) {
								SootClass declClass = ((RefType) sf.getType()).getSootClass();
								key = declClass.toString();
							}
							if (!fieldsOfType.containsKey(key)) {
								fieldsOfType.put(key, new LinkedList<SootField>());
							}
							fieldsOfType.get(key).add(sf);
						}
					}
				}
			}

			System.out.println("Print field mapping for " + fieldsOfType.size() + " classes.");

			writeFieldsToJson(fieldsOfType, mapFile);

			if (options.wordFieldMapFileName != null) {

				final WordsTokenizer tokenizer = Tokenizers.tokenizeString();

				final List<Map<String, List<String>>> result = new ArrayList<>();

				// index: field-name -> declaring class name
				final Map<String, String> index = new HashMap<>();

				for(Collection<SootField> each : fieldsOfType.values()){
					final Set<String> allFields = each.stream()
						.map(SootField::getName)
						.collect(Collectors.toSet());

					final Corpus<String> corpus 		= Corpus.ofStrings();

					each.forEach(e -> {
						corpus.add(e.getName());
						index.put(e.getName(), e.getDeclaringClass().getName());
					});

					final Map<List<Word>, List<Word>> relevantMaps = Introspector.buildWordsMap(
						corpus, tokenizer
					);

					if(relevantMaps.isEmpty()) continue;

					final List<Word> a = Iterables.get(relevantMaps.keySet(), 0);
					final List<Word> b = Iterables.get(relevantMaps.values(), 0);

					final List<Word> 	wordList	= b.isEmpty() ? a/*frequent words*/ : b/*typical words*/;

					final Set<String>	relevant	= wordList.stream()
						.map(Word::element)
						.collect(Collectors.toSet());

					final Set<String> universe	= corpus.dataSet();

					Map<String, List<String>> wordFieldsMap = Recommend.mappingOfLabels(
						relevant, universe
					);

					if(wordFieldsMap.isEmpty()) continue;

					// removes entries where a label is mapped to an empty list (e.g., food -> ())
					wordFieldsMap = wordFieldsMap.entrySet().stream()
						.filter(e -> !e.getValue().isEmpty()) // pick entries with non empty values
						.filter(e -> e.getValue().size() > 1) // pick entries with values size > 1
						.filter(e -> e.getValue().containsAll(allFields)) // pick entries that don't contain ALL available fields
						.collect(Collectors.toMap(Entry::getKey, Entry::getValue));

					if(wordFieldsMap.isEmpty()) continue;

					result.add(wordFieldsMap);
				}

				if(!result.isEmpty()){
					final File wordMapFile = new File(options.wordFieldMapFileName);

					writeMappingsToJson(result, index, wordMapFile);
				} else {
					System.out.println("Warning: Unable to produce any clusters!");
				}
			}

		}

	}

	private static void sootMethodToJson(SootMethod sm, PrintWriter pw, final String indent) {
		pw.println(indent+"{\"methodname\" : \"" + sm.getSignature()+"\",");
		pw.println(indent+"\"returntype\" : \"" + sm.getReturnType()+"\",");
		
		pw.print(indent+"\"paramtypes\" : [" );
		String comma = "";
		for (Type t : sm.getParameterTypes()) {
			pw.print(comma);
			pw.print(indent+"\t\"" + t+"\"");
			comma = ",\n";
		}
		pw.println("]");
		pw.print(indent+"}");
	}
	
	private static void writeToJson(Map<String, Set<SootClass>> clusters, File outfile) {
		try (PrintWriter writer = new PrintWriter(outfile, "UTF-8");) {
			writer.println("{\n\t\"mappings\": [");
			boolean first = true;
			for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
				if (first) {
					first = false;
				} else {
					writer.println(",");
				}
				writer.println("\t\t{");
				writer.println("\t\t \"types\":[");
				boolean firstSignature = true;
				for (SootClass sc : entry.getValue()) {
					if (firstSignature) {
						firstSignature = false;
					} else {
						writer.println(",");
					}
					writer.print("\t\t\t\"");
					writer.print(sc.getName());
					writer.print("\"");
				}
				writer.println("\n\t\t ],");
				writer.println("\t\t \"labels\":[");
				writer.println("\t\t\t\"" + entry.getKey() + "\"");
				writer.println("\t\t ]");
				writer.print("\n\t\t}");
			}
			writer.println("\n\t]\n}");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void writeFieldsToJson(Map<String, Collection<SootField>> classToFields, File outfile) {
		try (PrintWriter writer = new PrintWriter(outfile, "UTF-8")) {
			writer.println("{\n\t\"mappings\": [");
			boolean first = true;
			for (Entry<String, Collection<SootField>> entry : classToFields.entrySet()) {
				if (first) {
					first = false;
				} else {
					writer.println(",");
				}
				writer.println("\t\t{");
				writer.println("\t\t \"fields\":[");
				boolean firstSignature = true;
				for (SootField sf : entry.getValue()) {
					if (firstSignature) {
						firstSignature = false;
					} else {
						writer.println(",");
					}
					writer.print("\t\t\t\"");
					writer.print(sf.getName());
					writer.print("\"");
				}
				writer.println("\n\t\t ],");
				writer.println("\t\t \"class\":[");
				writer.println("\t\t\t\"" + entry.getKey() + "\"");
				writer.println("\t\t ]");
				writer.print("\n\t\t}");
			}
			writer.println("\n\t]\n}");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void writeMappingsToJson(List<Map<String, List<String>>> wordToFields, Map<String, String> index, File outfile) {
		try (PrintWriter writer = new PrintWriter(outfile, "UTF-8")) {
			writer.println("{\n\t\"mappings\": [");
			boolean first = true;
			for (Map<String, List<String>> map : wordToFields) {
				for(String eachKey : map.keySet()){
					final List<String> eachValue = map.get(eachKey);

					if (first) {
						first = false;
					} else {
						writer.println(",");
					}

					writer.println("\t\t{");
					writer.println("\t\t \"fields\":[");
					boolean firstSignature = true;

					for (String eachField : eachValue) {
						if (firstSignature) {
							firstSignature = false;
						} else {
							writer.println(",");
						}
						writer.print("\t\t\t\"");
						if (index.containsKey(eachField)) {
							writer.print(index.get(eachField) + "." + eachField);
						} else {
							writer.print(eachField);
						}
						writer.print("\"");
					}

					writer.println("\n\t\t ],");
					writer.println("\t\t \"label\":[");
					writer.println("\t\t\t\"" + eachKey + "\"");
					writer.println("\t\t ]");
					writer.print("\n\t\t}");
				}

			}
			writer.println("\n\t]\n}");
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

	/**
	 * map from FunFactory to "fun;factory" unless super class contains
	 * "factory", then only map to "fun".
	 * 
	 * @param ignoreWords
	 * @param dict
	 * @return
	 */
	private static Map<String, Set<SootClass>> strategy2(Set<String> ignoreWords, Set<String> dict) {
		Map<String, Set<SootClass>> clusters = new LinkedHashMap<String, Set<SootClass>>();

		for (SootClass sc : getAllClasses()) {

			if (innerOrStaticNested(sc)) {
				// ignore nested classes
				continue;
			}

			List<String> stemmedWords = splitIntoWords(sc.getJavaStyleName(), dict);

			if (sc.resolvingLevel() >= SootClass.HIERARCHY && sc.hasSuperclass()
					&& sc.getSuperclass().isApplicationClass()) {
				List<String> stemmedParentWords = splitIntoWords(sc.getSuperclass().getJavaStyleName(), dict);
				stemmedWords.removeAll(stemmedParentWords);
			}

			stemmedWords.removeAll(ignoreWords);
			if (!stemmedWords.isEmpty()) {
				final String key = makeKey(stemmedWords);
				if (!clusters.containsKey(key)) {
					clusters.put(key, new ArraySet<SootClass>());
				}
				clusters.get(key).add(sc);
			}
		}

		System.out.println("Total clusters: " + clusters.size());

		List<String> toRemove = new LinkedList<String>();
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			if (entry.getValue().size() <= 1) {
				toRemove.add(entry.getKey());
			}
		}
		for (String s : toRemove) {
			clusters.remove(s);
		}

		System.out.println("Total clusters >1: " + clusters.size());
		int ttword = 0;
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			ttword += entry.getValue().size();
		}
		System.out.println("Relabeled terms : " + ttword);

		return clusters;
	}

	private static Set<SootClass> getAllClasses() {
		Set<SootClass> allClasses = new ArraySet<SootClass>();
		allClasses.addAll(Scene.v().getApplicationClasses());
		allClasses.addAll(Scene.v().getLibraryClasses());
		System.out.println("Total classes loaded: " + allClasses.size());
		return allClasses;
	}

	private static Map<String, Set<SootClass>> strategy5(Set<String> ignoreWords, Set<String> dict) {
		SortedMap<String, Set<SootClass>> clusters = new TreeMap<>();

		for (SootClass sc : getAllClasses()) {

			if (innerOrStaticNested(sc)) {
				// ignore nested classes
				continue;
			}

			if (sc.getJavaStyleName().contains("Enumeration") || sc.getJavaStyleName().contains("Decompositor")){
				System.out.println("What?");
			}

			List<String> stemmedWords = splitIntoWords(sc.getJavaStyleName(), dict);

			if (sc.resolvingLevel() >= SootClass.HIERARCHY && sc.hasSuperclass()
					&& sc.getSuperclass().isApplicationClass()) {
				List<String> stemmedParentWords = splitIntoWords(sc.getSuperclass().getJavaStyleName(), dict);
				int sharedWords = 0;
				for (String s : stemmedParentWords) {
					if (stemmedWords.contains(s)) {
						sharedWords++;
					}
				}
				if (sharedWords > 0) {
					stemmedWords.retainAll(stemmedParentWords);
				}
			}

			stemmedWords.removeAll(ignoreWords);
			if (!stemmedWords.isEmpty()) {
				final String key = makeKey(stemmedWords);
				final String candKey = resolveKey(key, stemmedWords, clusters);

				if (!clusters.containsKey(candKey)) {
					clusters.put(key, new ArraySet<>());
				}

				clusters.get(candKey).add(sc);

			}
		}

		System.out.println("Total clusters: " + clusters.size());

		List<String> toRemove = new LinkedList<>();
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			if (entry.getValue().size() <= 1) {
				toRemove.add(entry.getKey());
			}
		}
		for (String s : toRemove) {
			clusters.remove(s);
		}

		System.out.println("Total clusters >1: " + clusters.size());

		toRemove = new LinkedList<>();
		Map<String, Set<SootClass>> errorAndExceptions = new HashMap<>();
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			if (entry.getKey().startsWith("exception;")){
				if (!errorAndExceptions.containsKey("exception;")){
					errorAndExceptions.put("exception;", new HashSet<>());
				}

				errorAndExceptions.get("exception;").addAll(entry.getValue());
				toRemove.add(entry.getKey());
			} else if (entry.getKey().startsWith("error;")){
				if (!errorAndExceptions.containsKey("error;")){
					errorAndExceptions.put("error;", new HashSet<>());
				}

				errorAndExceptions.get("error;").addAll(entry.getValue());
				toRemove.add(entry.getKey());
			}
		}

		for (String s : toRemove) {
			clusters.remove(s);
		}

		clusters.putAll(errorAndExceptions);

		System.out.println("Total clusters (after exception coalescing): " + clusters.size());

		int ttword = 0;
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			ttword += entry.getValue().size();
		}
		System.out.println("Relabeled terms : " + ttword);
		return clusters;
	}

	static boolean innerOrStaticNested(SootClass sc){
		return sc.getJavaStyleName().contains("$");
	}

	@SuppressWarnings("unchecked")
	static <T> Stream<T> reverse(Stream<T> input) {
		Object[] temp = input.toArray();
		return (Stream<T>) IntStream.range(0, temp.length)
				.mapToObj(i -> temp[temp.length - i - 1]);
	}


	static String resolveKey(String key, List<String> stemmedWords, SortedMap<String, Set<SootClass>> clusters){
		List<String> reversed = reverse(stemmedWords.stream()).collect(Collectors.toList());

		double longest = 0.0d;
		String candKey = BLANK;
		String keyword = reversed.get(0);
		if (keyword.length() > 8){
			keyword = keyword.substring(0, 8);
		}
		Set<Map.Entry<String, Set<SootClass>>> matches = searchByPrefix(clusters, keyword).entrySet();
		for(Map.Entry<String, Set<SootClass>> entry : matches){
			if (BLANK.equals(candKey)){
				candKey = entry.getKey();
				longest = RatcliffObershelp.similarity(key, candKey);
			} else {
				double newLongest = RatcliffObershelp.similarity(key, entry.getKey());
				if (Double.compare(newLongest, longest) > 0){
					candKey = entry.getKey();
					longest = newLongest;
				}
			}
		}

		if (BLANK.equals(candKey)){
			candKey = key;
		} else {
			if (Double.compare(longest, 0.6) <= 0){
				candKey = key;
			}
		}

		return candKey;
	}


	private static Map<String, Set<SootClass>> strategy3(Set<String> ignoreWords, Set<String> dict) {
		SortedMap<String, Set<SootClass>> clusters = new TreeMap<>();

		for (SootClass sc : getAllClasses()) {

			if (innerOrStaticNested(sc)) {
				// ignore nested classes
				continue;
			}

			List<String> stemmedWords = splitIntoWords(sc.getJavaStyleName(), dict);

			if (sc.resolvingLevel() >= SootClass.HIERARCHY && sc.hasSuperclass()
					&& sc.getSuperclass().isApplicationClass()) {
				List<String> stemmedParentWords = splitIntoWords(sc.getSuperclass().getJavaStyleName(), dict);
				int sharedWords = 0;
				for (String s : stemmedParentWords) {
					if (stemmedWords.contains(s)) {
						sharedWords++;
					}
				}
				if (sharedWords > 0) {
					stemmedWords.retainAll(stemmedParentWords);
				}
			}

			stemmedWords.removeAll(ignoreWords);
			if (!stemmedWords.isEmpty()) {
				final String key = makeKey(stemmedWords);
				if (!clusters.containsKey(key)) {
					clusters.put(key, new ArraySet<>());
				}

				clusters.get(key).add(sc);
			}
		}

		System.out.println("Total clusters: " + clusters.size());

		List<String> toRemove = new LinkedList<String>();
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			if (entry.getValue().size() <= 1) {
				toRemove.add(entry.getKey());
			}
		}
		for (String s : toRemove) {
			clusters.remove(s);
		}

		System.out.println("Total clusters >1: " + clusters.size());
		int ttword = 0;
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			ttword += entry.getValue().size();
		}
		System.out.println("Relabeled terms : " + ttword);
		return clusters;
	}

	private static Map<String, Set<SootClass>> strategy4(Set<String> ignoreWords, Set<String> dict) {
		Map<String, Set<SootClass>> clusters = new LinkedHashMap<String, Set<SootClass>>();

		for (SootClass sc : getAllClasses()) {

			if (sc.getJavaStyleName().contains("$")) {
				// ignore nested classes
				continue;
			}

			List<String> stemmedWords = splitIntoWords(sc.getJavaStyleName(), dict);

			if (sc.resolvingLevel() >= SootClass.HIERARCHY && sc.hasSuperclass()
					&& sc.getSuperclass().isApplicationClass()) {
				List<String> stemmedParentWords = splitIntoWords(sc.getSuperclass().getJavaStyleName(), dict);
				int sharedWords = 0;
				for (String s : stemmedParentWords) {
					if (stemmedWords.contains(s)) {
						sharedWords++;
					}
				}
				if (sharedWords > 0) {
					stemmedWords.retainAll(stemmedParentWords);
				}
			}

			stemmedWords.removeAll(ignoreWords);

			List<String> minSynonyms = new LinkedList<String>();
			for (String s : stemmedWords) {
				String syn = findLowestSynonym(s);
				if (!minSynonyms.contains(syn)) {
					minSynonyms.add(syn);
				}
			}

			if (!minSynonyms.isEmpty()) {
				final String key = makeKey(minSynonyms);
				if (!clusters.containsKey(key)) {
					clusters.put(key, new ArraySet<SootClass>());
				}
				clusters.get(key).add(sc);
			}
		}

		System.out.println("Total clusters: " + clusters.size());

		List<String> toRemove = new LinkedList<String>();
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			if (entry.getValue().size() <= 1) {
				toRemove.add(entry.getKey());
			}
		}
		for (String s : toRemove) {
			clusters.remove(s);
		}

		System.out.println("Total clusters >1: " + clusters.size());
		int ttword = 0;
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			ttword += entry.getValue().size();
		}
		System.out.println("Relabeled terms : " + ttword);
		return clusters;
	}

	private static Map<String, Set<SootClass>> strategy1(Set<String> ignoreWords, Set<String> dict) {
		Map<String, Set<SootClass>> clusters = new LinkedHashMap<String, Set<SootClass>>();

		for (SootClass sc : getAllClasses()) {

			if (innerOrStaticNested(sc)) {
				// ignore nested classes
				continue;
			}

			List<String> stemmedWords = splitIntoWords(sc.getJavaStyleName(), dict);
			stemmedWords.removeAll(ignoreWords);
			if (!stemmedWords.isEmpty()) {
				final String key = makeKey(stemmedWords);
				if (!clusters.containsKey(key)) {
					clusters.put(key, new ArraySet<SootClass>());
				}
				clusters.get(key).add(sc);
			}
		}

		System.out.println("Total clusters: " + clusters.size());

		List<String> toRemove = new LinkedList<String>();
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			if (entry.getValue().size() <= 1) {
				toRemove.add(entry.getKey());
			}
		}
		for (String s : toRemove) {
			clusters.remove(s);
		}

		System.out.println("Total clusters >1: " + clusters.size());
		int ttword = 0;
		for (Entry<String, Set<SootClass>> entry : clusters.entrySet()) {
			ttword += entry.getValue().size();
		}
		System.out.println("Relabeled terms : " + ttword);

		return clusters;
	}

	private static String makeKey(List<String> words) {
		List<String> stemmedWords = new LinkedList<>(words);
		if (!stemmedWords.isEmpty()){
			stemmedWords = reverse(stemmedWords.stream()).collect(Collectors.toList());
		}

		StringBuilder sb = new StringBuilder();
		for (String s : stemmedWords) {
			sb.append(s);
			sb.append(";");
		}
		return sb.toString();
	}

	private static String wordNetStuff(String word) {
		SnowballStemmer stemmer = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
		String shortStem = word.toLowerCase();
		try {
			String stemmedWord = stemmer.stem(word).toString();
			if (stemmedWord.length() < shortStem.length()) {
				shortStem = stemmedWord.toLowerCase();
			}
		} catch (IllegalArgumentException e) {
			System.err.println("Something bad in " + shortStem);
		}
		return shortStem;
	}

	private static List<String> splitIntoWords(final String identifierName, Set<String> dict) {
		// split the came case first
		List<String> words = new LinkedList<>();
		for (String word : identifierName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")) {
			String lowerCaseWord = word.toLowerCase();
			String longestWordFwd = null;
			for (int i = 0; i <= lowerCaseWord.length(); i++) {
				String subStr = lowerCaseWord.substring(0, i);
				if (subStr.length() > 2) {
					if (dict.contains(subStr)) {
						longestWordFwd = subStr;
					}
				}
			}
			if (longestWordFwd == null) {
				longestWordFwd = lowerCaseWord;
			}
			longestWordFwd = wordNetStuff(longestWordFwd);
			words.add(longestWordFwd);
		}

		return words;
	}

	private static Set<String> getEnglishDict() {
		final File unixDict = new File("/usr/share/dict/words");
		Set<String> words = new HashSet<String>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(unixDict), StandardCharsets.UTF_8));) {
			String line;
			while ((line = in.readLine()) != null) {
				words.add(line.replace(System.getProperty("line.separator"), "").toLowerCase());
			}
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}
		return words;
	}

	private static Map<String, String> synmap = new HashMap<>();

	/**
	 * Gets all synonyms from wordnet, sorts them alphabetically, and picks the
	 * smallest.
	 * 
	 * @param word
	 * @return
	 */
	private static String findLowestSynonym(String word) {
		if (!synmap.containsKey(word)) {
			try {
				String line;
				Process p = Runtime.getRuntime().exec("python syn.py " + word);
				BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
				while ((line = input.readLine()) != null) {
					synmap.put(word, line);
				}
				input.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!synmap.containsKey(word)) {
				synmap.put(word, word);
			}
		}
		return synmap.get(word);
	}

	public static <V> SortedMap<String, V> searchByPrefix(SortedMap<String,V> baseMap, String prefix) {
		if(prefix.length() > 0) {
			char nextLetter = (char) (prefix.charAt(prefix.length() - 1) + 1);
			String end = prefix.substring(0, prefix.length() - 1) + nextLetter;
			return baseMap.subMap(prefix, end);
		}
		return baseMap;
	}


}