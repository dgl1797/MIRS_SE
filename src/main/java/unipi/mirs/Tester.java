package unipi.mirs;

import java.io.IOException;

import unipi.mirs.components.CompressedPostingList;
import unipi.mirs.components.PostingList;
import unipi.mirs.components.Vocabulary;
import unipi.mirs.models.VocabularyModel;

public class Tester {
  public static void main(String[] args) throws IOException {
    boolean stopnostem = false;
    boolean compressed = false;

    Vocabulary lexicon = Vocabulary.loadVocabulary(stopnostem, compressed);
    VocabularyModel vm = lexicon.vocabulary.get("hello");

    PostingList hellolist = PostingList.openList(vm.term, vm.dstartByte, vm.fstartByte, vm.plLength, false);
    CompressedPostingList chellolist = CompressedPostingList.from(hellolist);

    System.out.println(chellolist.totalLength);
    System.out.println(chellolist.upperBound);
    System.out.println(chellolist.getDIDBuffer().capacity());
    System.out.println(chellolist.getFRQBuffer().capacity());
  }
}
