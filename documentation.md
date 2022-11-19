# DEVELOPEMENT IDEA

since every document is a independent block to be queried and scored the idea behind this branch is to split the collection in chunks where every chunk has its own lexicon living in node's main memory and postings lists living in node's filesystem.

The brokers instead contain a mapping for all the nodes the single broker is responsible of where each term is mapped to a bitset of `chunks_number` bits where a 1 means the term is present in the chunk and 0 means it is not.

Disjunctive and Conjunctive queries are then computed through a bitwise operation between the term's bitvectors to discover in which collections the term appears at least once, hence the query is sent to each node where the bitwise operation results in a 1

Ideally there would be a node computing its own top k then returing it to the reducer which will merge the top k by using a k-merge of the rankings where the k-merging is just a merge sort that stops at the best k documents, the top ks are just TreeMaps of docno:score sorted by value then merged by iterating k times over the `chunks_number` rankings with a `O[k*chunks_number]` time complexity.

The ideal situation would allow each node to have its own lexicon loaded in main memory, then scanning the postings lists.

Unfortunately with a single node in the real scenario it would mean loading `chunks_number` lexicons of duplicates or more simply a sort of postings list of the lexicons which means the bitvector is being substituted by an array of integers indicating the position of each term in each postings lists creating a global lexicon that reuses the structure of the postings lists but uses a fixed length of `chunks_number` integer in the range [0, `collection_size`/`chunks_number`] hence a number of bits equal to log2(`collection_size`/`chunks_number`), this would result in the loss of the size of postings which is consenquently moved inside the inverted indexes.
