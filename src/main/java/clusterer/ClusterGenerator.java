/**
 * 
 */
package clusterer;

import com.google.common.base.Verify;
import com.vesperin.text.Corpus;
import com.vesperin.text.Introspector;
import com.vesperin.text.Recommend;
import com.vesperin.text.Selection.Word;
import com.vesperin.text.spelling.StopWords;
import com.vesperin.text.tokenizers.Tokenizers;
import com.vesperin.text.tokenizers.WordsTokenizer;
import edu.mit.jwi.morph.SimpleStemmer;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.util.ArraySet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ClusterGenerator {

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

			Map<SootClass, Collection<SootField>> fieldsOfType = new HashMap<SootClass, Collection<SootField>>();

			for (SootClass sc : Scene.v().getApplicationClasses()) {
				if (sc.resolvingLevel() >= SootClass.SIGNATURES) {
					for (SootField sf : sc.getFields()) {
						// ignore this referneces.
						if (sf.getType() instanceof RefType && !sf.getName().startsWith("this")) {
							SootClass declClass = ((RefType) sf.getType()).getSootClass();
							if (declClass.isApplicationClass()) {
								if (!fieldsOfType.containsKey(declClass)) {
									fieldsOfType.put(declClass, new LinkedList<SootField>());
								}
								fieldsOfType.get(declClass).add(sf);
							}
						}
					}
				}
			}

			System.out.println("Print field mapping for " + fieldsOfType.size() + " classes.");

			writeFieldsToJson(fieldsOfType, mapFile);


			if (options.wordFieldMapFileName != null) {
				final WordsTokenizer tokenizer 	= Tokenizers.tokenizeString(StopWords.all());
				final Corpus<String> corpus 		= Corpus.ofStrings();

				final List<Map<String, List<String>>> result = new ArrayList<>();

				// index: field-name -> declaring class name
				final Map<String, String> index = new HashMap<>();

				for(Collection<SootField> each : fieldsOfType.values()){
					each.forEach(e -> {
						corpus.add(e.getName());
						index.put(e.getName(), e.getDeclaringClass().getName());
					});


					final List<Word> 	typicalOnes	= Introspector.typicalWords(corpus, tokenizer);
					final Set<String>	relevant		= typicalOnes.stream().map(Word::element).collect(Collectors.toSet());
					final Set<String> universe		= corpus.dataSet();

					final Map<String, List<String>> wordFieldsMap = Recommend.mappingOfLabels(relevant, universe);

					result.add(wordFieldsMap);
				}

				final File wordMapFile = new File(options.wordFieldMapFileName);

				writeMappingsToJson(result, index, wordMapFile);
			}

		}

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

	private static void writeFieldsToJson(Map<SootClass, Collection<SootField>> classToFields, File outfile) {
		try (PrintWriter writer = new PrintWriter(outfile, "UTF-8")) {
			writer.println("{\n\t\"mappings\": [");
			boolean first = true;
			for (Entry<SootClass, Collection<SootField>> entry : classToFields.entrySet()) {
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
				writer.println("\t\t\t\"" + entry.getKey().getName() + "\"");
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

					for(String eachField : eachValue){
						if (firstSignature) {
							firstSignature = false;
						} else {
							writer.println(",");
						}
						writer.print("\t\t\t\"");
						if(index.containsKey(eachField)) {
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

				writer.println("\n\t]\n}");

			}
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

			if (sc.getJavaStyleName().contains("$")) {
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

	private static Map<String, Set<SootClass>> strategy3(Set<String> ignoreWords, Set<String> dict) {
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

			if (sc.getJavaStyleName().contains("$")) {
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
		List<String> stemmedWords = new LinkedList<String>(words);
		Collections.sort(stemmedWords);
		StringBuilder sb = new StringBuilder();
		for (String s : stemmedWords) {
			sb.append(s);
			sb.append(";");
		}
		return sb.toString();
	}

	private static String wordNetStuff(String word) {
		SimpleStemmer ss = new SimpleStemmer();
		String shortStem = word.toLowerCase();
		try {
			for (String s : ss.findStems(word, null)) {
				if (s.length() < shortStem.length()) {
					shortStem = s.toLowerCase();
				}
			}
		} catch (IllegalArgumentException e) {
			System.err.println("Something bad in " + shortStem);
		}
		return shortStem;
	}

	private static List<String> splitIntoWords(final String identifierName, Set<String> dict) {
		// split the came case first
		List<String> words = new LinkedList<String>();
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
			wordNetStuff(longestWordFwd);
			words.add(longestWordFwd);
		}

		return words;
	}

	private static Set<String> getEnglishDict() {
		final File unixDict = new File("/usr/share/dict/words");
		Set<String> words = new HashSet<String>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(unixDict), "UTF8"));) {
			String line = null;
			while ((line = in.readLine()) != null) {
				words.add(line.replace(System.getProperty("line.separator"), "").toLowerCase());
			}
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}
		return words;
	}

	private static Map<String, String> synmap = new HashMap<String, String>();

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
}