import os, sys

from nltk.corpus import wordnet as wn


def main(word):
  synonyms = []

  for syn in wn.synsets(word):
    for l in syn.lemmas():
      synonyms.append(l.name())

  synonyms.sort()
  if len(synonyms)==0:    
    print word
  else:
    print synonyms[0]

if __name__ == '__main__':
  main(sys.argv[1])
