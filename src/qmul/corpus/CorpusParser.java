/*******************************************************************************
 * Copyright (c) 2013, 2014 Matthew Purver, Queen Mary University of London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Matthew Purver, Queen Mary University of London - initial API and implementation
 ******************************************************************************/
package qmul.corpus;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import qmul.util.parse.PennTreebankTokenizer;
import qmul.util.parse.StanfordParser;
import qmul.util.parse.TreeParser;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.Parser;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;

/**
 * Parse a corpus
 * 
 * @author mpurver
 */
public class CorpusParser {

	private static Parser parser;
	private static final PennTreebankTokenizer tok = new PennTreebankTokenizer(true);
	private static boolean leaveExisting = false;

	/**
	 * Set up the default Stanford parser with default options
	 */
	public static void setParser() {
		setParser(new StanfordParser());
	}

	/**
	 * @param parser
	 *            the parser to set
	 */
	public static void setParser(Parser parser) {
		CorpusParser.parser = parser;
	}

	/**
	 * @return true if the parser should leave existing syntactic info, and only parse where there is none; false if it
	 *         should overwrite with new parse info (default)
	 */
	public static boolean isLeaveExisting() {
		return leaveExisting;
	}

	/**
	 * @param leaveExisting
	 *            true if the parser should leave existing syntactic info, and only parse where there is none; false if
	 *            it should overwrite with new parse info (default)
	 */
	public static void setLeaveExisting(boolean leaveExisting) {
		CorpusParser.leaveExisting = leaveExisting;
	}

	/**
	 * Parse a corpus, replacing any existing syntactic annotation unless isLeaveExisting()
	 * 
	 * @param corpus
	 *            the corpus to parse (which gets modified)
	 * @return the number of {@link DialogueSentence}s actually affected (i.e. successfully parsed)
	 */
	public static int parse(DialogueCorpus corpus) {
		if (parser == null) {
			System.err.println("WARNING: null parser, setting default ...");
			setParser();
		}
		int iD = 0;
		int iS = 0;
		int iP = 0;
		for (Dialogue d : corpus.getDialogues()) {
			System.out.println("Parsing dialogue " + ++iD + " of " + corpus.getDialogues().size());
			for (DialogueSentence s : d.getSents()) {
				if (leaveExisting && (s.getSyntax() != null)) {
					continue;
				}
				List<? extends HasWord> words = s.getTokens();
				if (words == null) {
					words = tok.getWordsFromString(s.getTranscription());
				}
				if ((words == null) || words.isEmpty()) {
					System.err.println("No words in sentence " + ++iS + " " + s.getNum() + ", skipping ...");
					continue;
				} else {
					System.err.print("Parsing sentence " + ++iS + " " + s.getNum() + " " + words + " ...");
				}
				boolean success = parser.parse(words);
				if (success) {
					iP++;
					Tree t = null;
					if (parser instanceof LexicalizedParser) {
						t = ((LexicalizedParser) parser).getBestParse();
					} else if (parser instanceof TreeParser) {
						// why doesn't the Stanford Parser interface include a method for returning the tree??
						t = ((TreeParser) parser).getBestParse();
					} else {
						throw new RuntimeException("unknown parser class " + parser);
					}
					System.err.println(" success!\n" + t.pennString());
					s.setSyntax(t);
				} else {
					System.err.println(" failed.");
					s.setSyntax(null);
				}
				System.out.println("Mem " + Runtime.getRuntime().freeMemory() + " "
						+ Runtime.getRuntime().totalMemory());
				System.out.println("Parser done " + iD + " dialogues, " + iS + " sentences ...");
			}
		}
		System.out.println("Finished (parsed " + iP + " sentences)");
		return iP;
	}

	/**
	 * Copy parses from corpus1 to corpus2, replacing any existing syntactic annotation unless isLeaveExisting()
	 * 
	 * @param corpus1
	 *            the source (which does not get modified)
	 * @param corpus2
	 *            the corpus to parse (which gets modified)
	 * @return the number of {@link DialogueSentence}s actually affected
	 */
	public static int copyParses(DialogueCorpus corpus1, DialogueCorpus corpus2) {
		// first hash corpus2's sentences by ID so they're easy to find
		HashMap<String, DialogueSentence> sents = new HashMap<String, DialogueSentence>();
		for (Dialogue d : corpus1.getDialogues()) {
			for (DialogueSentence s : d.getSents()) {
				sents.put(s.getId(), s);
			}
		}
		// now go through and copy
		int iD = 0;
		int iS = 0;
		int iP = 0;
		for (Dialogue d : corpus2.getDialogues()) {
			System.out.println("Parsing dialogue " + ++iD + " of " + corpus2.getDialogues().size());
			for (DialogueSentence s : d.getSents()) {
				iS++;
				if (leaveExisting && (s.getSyntax() != null)) {
					continue;
				}
				if (!sents.containsKey(s.getId())) {
					System.err.println("Can't find parsed sentence " + s);
					continue;
				}
				DialogueSentence src = sents.get(s.getId());
				Tree t = src.getSyntax();
				if (t == null) {
					System.err.println("Null parse found for sentence " + s + " = " + sents.get(s.getId()));
					continue;
				}
				s.setSyntax(t);
				if (!Double.isNaN(src.getSyntaxProb())) {
					s.setSyntaxProb(src.getSyntaxProb());
				}
				iP++;
			}
			System.out.println("Copied " + iD + " dialogues, " + iP + " of " + iS + " sentences ...");
		}
		System.out.println("Finished (copied " + iP + " parses for " + iS + " sentences)");
		return iP;
	}

	/**
	 * just for testing
	 * 
	 * @args put in your local corpus base dir if you want
	 */
	public static void main(String[] args) {
		DialogueCorpus corpus1 = DialogueCorpus.readFromFile(new File("bnc_trim_ccg.corpus"));
		DialogueCorpus corpus2 = DialogueCorpus.readFromFile(new File("bnc_timed_trim.corpus"));
		copyParses(corpus1, corpus2);
		corpus2.writeToFile(new File("bnc_timed_trim_ccg.corpus"));
		for (int i = 0; i < 10; i++) {
			DialogueTurn t = corpus2.getDialogues().get(0).getTurns()
					.get(Math.min(i, corpus2.getDialogues().get(0).numTurns() - 1));
			if (t.getSents().size() > 0) {
				System.out.println(t.getSents().get(0));
				System.out.println(t.getSents().get(0).getSyntax());
			}
		}
	}
}
