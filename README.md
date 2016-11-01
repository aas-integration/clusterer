## TL;DR

```
git clone https://github.com/aas-integration/clusterer.git
cd clusterer
gradle jar
java -jar build/libs/clusterer.jar build/classes/
```
Then look at the produced json files to see the clustering.

## Usage

sootwrapper.ClusterGenerateor takes as argument a directory 
that contains a set of directories for soot and an optional class path.

It iterates over all directories in the given directory and adds
them to the soot ProcessPath. That is, if there is any subdirectory 
(e.g, DS_store) that cannot be handled by soot, the whole thing may crash.

## Output

The tool currently produces several json files. Each json file clusters the
class names found in the subfolders. Class names are clustered if we consider
the classes to be *similar*. The notion of similarity is different for each
produced json file. All strategies start by splitting the last part of the 
class name into words based on its camel casing. Then it stems them and sorts
them alphabetically.

### strategy 1 
Takes the list of stored and stemmed words and concatenates them to a string
using semicolon as a separator. This string used used as a hash key to add 
the class name to a dictionary: `string -> list<string> `.

### strategy 2
Takes the list of stored and stemmed words for the current class and the 
super class, removes all words that occur in the super class list from the
current list and then concatenates the remainder like strategy 1.

### strategy 3
Takes the list of stored and stemmed words for the current class and the 
super class, and uses the intersection of both. If the intersection is empty,
it uses the original list. Then concatenates the remainder like strategy 1.

### strategy 4
Same as 3, but for each work in the stored and stemmed words, we look up in
a thesaurus for the list of synonyms and replace the word by its lexicographically 
smallest, stemmed, synonym. Finally, we sort the list again.


