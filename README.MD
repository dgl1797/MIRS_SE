# PROJECT DESCRIPTION

The project has been developed by:

- [Fabio Cognata](https://github.com/FabioCognata)
- [Giovanni Paolini](https://github.com/Paulfinex)
- [Luca Di Giacomo](https://github.com/DgL1797)

For the [University of Pisa](https://www.unipi.it/) during the attendance of the course _`Multimedia Information Retrieval Systems`_ during the academic year 2022/2023

The task of the project was to develop a search engine capable of indexing a corpus of 8,841,823 documents findable at: https://microsoft.github.io/msmarco/TREC-Deep-Learning-2020.

[_direct download link_](https://msmarco.blob.core.windows.net/msmarcoranking/collection.tar.gz)

To develop the project it has been used Java programming language along with Maven project manager. The repository is mainly composed by a _components_ folder, including all the pieces to make both the index manager and the search engine work, and an _utilities_ folder where text normalization functions and compression functions are placed. Other folders contain helper classes that implement some basic graphics and data classes to help with data conversions.

# MAIN CLASSES AND USAGE

To use the search engine, download the jar files and place them in the same folder, so that the IndexManager will create the file system that the Search Engine will exploit for queries. Input files must be placed in the `data/input` folder and query input files in the `data/input/queries` folder.

- IndexManager -> Runs a console-based GUI that generates the necessary filesystem relatively to the .jar file and initializes the index by saving it into .dat files inside the `data/output` folder by reading a formatted file in `.tar`; `.tar.gz`; `.gz` and `.tsv` formats. The input file must be composed of lines formatted as **_docno_`\t`_docbody_**. IndexManager also allows to create a compressed version of the index and to enable/disable stopwords filtering and stemming. To use the index manager it is only necessary to run the _`java -jar IndexManager.jar`_ command and follow the menu options

- SearchEngine -> Allows different types of queries in an interactive way by using `/command/` to set-up the query modes, like the scoring function to use (TFIDF; BM25), whether or not to use the compressed version of the posting lists to lighten the quantity of data to be stored on memory and disabling/enabling filtering of stopwords and stemming. To run the search engine, run the _`java -jar SearchEngine.jar`_ command, then a guide will be printed with all the accepted commands, notice that every other string outside the commands will be used as query string and will produce a top 20 of the most fitting document identifiers with the relative assigned score. To print again the command list use the _`/help/`_ command. The conjunctive/disjunctive mode is recognizable by the respective initial lower case letter in the squared parenthesis immediately after _`Search`_.

## CORE CLASSES

- IndexBuilder -> It is a class that keeps track of all the informations needed to handle chunks of index, an IndexBuilder instance is able to load the chunk's information into memory and save them into a file, automatically increasing the chunksize and resetting the structures for when the chunk's limit is reached. The core function is the addDocument function which loads the new passed document's information and terms in memory by updating the mapper between terms its informations. The chunks produced are then merged by using a merge*sort-like merging of chunk pairs. Chunks are merged from the least recent to the most recent so that the posting list chunks of common terms can be simply concatenated. The average building time is *14 minutes\* for unfiltered index and _10 minutes_ for the filtered one.

- [Compressed]PostingList -> These classes hold the logic to implement the necessary search operations like next() which passes to the next document's informations for the term and nextGEQ for implementing skips. Classical Posting List implements skippings by using binary search between the ending position and the current position, while the Compressed version uses skips stored as VB encoded byte offsets from the last skip, one every sqrt(#postings) for that list. Both the classes allow the implementation of pruning by storing the upper bound for the term in the collection reaching computation times of the order of milliseconds.

- VariableByteEncoder -> holds the logic of the integers compression using Variable Byte. For implementing the stream of an integer the bit 0 indicates that the stream of the integer will continue on the next byte, 1 instead indicates the stream is over. In this class there are 3 methods, the encode is for class usage and encodes a single integer, while the encodeList and decodeInt are usade together with binary buffers to perform encodings and decodings as-needed.
