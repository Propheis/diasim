/*******************************************************************************
 * Copyright (c) 2013, 2014 Matthew Purver, Queen Mary University of London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * <p/>
 * Contributors:
 * Matthew Purver, Queen Mary University of London - initial API and implementation
 * Kevin Zuern, Queen's University at Kingston - modification of implementation to suit needs of project
 ******************************************************************************/
package quak.corpus;

import csli.util.FileUtils;
import qmul.corpus.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple corpora created from transcript text files.
 *
 * @author mpurver
 * @author Kevin
 */
public class TextCorpus extends DialogueCorpus {

    // Pattern:                                                     "00:00 SpeakerID: Transcript"
    private static final Pattern LINE_PAT = Pattern.compile("(?:[0-9]{2}:[0-9]{2}) ([\\S][^:]+): ([\\S \t]+)");

    private final String GENRE = "QU_Genre";

    public TextCorpus(String id, File file, boolean dynamic) {
        super(id, file, dynamic);
    }

    /**
     * @return a list of [DATag, SpeakerID, Transcript], or null if no match
     */
    protected List<String> matchLine(String line, String dialogueId) {
        Matcher m = LINE_PAT.matcher(line);
        if (m.matches()) {
            // Use to create hashcode for daTags. Just a placeholder - we don't need Dialogue Act tags.
            Date d = new Date();
            String daTags = null;

            String spkId = m.group(1);
            String trans = m.group(2).trim();
            String[] s = {daTags, spkId, trans};
            return Arrays.asList(s);
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see qmul.corpus.DialogueCorpus#loadDialogue(java.lang.String)
     */
    @Override
    public boolean loadDialogue(String name) {
        System.out.println("Load dialogue " + name);
        File file = new File(getDir(), name);
        ArrayList<String> lines = new ArrayList<String>();
        try {
            FileUtils.getFileLines(file, lines);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        String id = name.replaceAll("\\.txt", "");
        Dialogue d = addDialogue(id, getGenre());
        DialogueTurn t = null;
        DialogueSpeaker lastSpk = null;
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            List<String> matches = matchLine(line, id);
            if (matches != null) {
                String daTags = null;
                String spkId = matches.get(1);
                String trans = matches.get(2);
                DialogueSpeaker spk = getSpeakerMap().get(spkId);
                if (spk == null) {
                    spk = new DialogueSpeaker(spkId, null, null, null, null, null);
                    getSpeakerMap().put(spkId, spk);
                }
                if ((t == null) || (!spk.equals(lastSpk))) {
                    t = d.addTurn(-1, spk);
                }
                DialogueSentence s = d.addSent(-1, t, trans, null);
                if (daTags != null && !daTags.isEmpty()) {
                    for (String daTag : daTags.split(",")) {
                        s.getDaTags().add(daTag);
                        t.getDaTags().add(daTag);
                    }
                }
                lastSpk = spk;
                // System.out.println(s.getId() + " " + s.getDaTags());
            } else {
                System.err.println("WARNING strange line " + line);
            }
        }
        return checkDialogue(d);
    }

    /*
     * (non-Javadoc)
     *
     * @see qmul.corpus.DialogueCorpus#setupCorpus()
     */
    @Override
    public boolean setupCorpus() {
        getGenreCounts().put(getGenre(), Integer.MAX_VALUE);
        for (File file : getDir().listFiles()) {
            if (!loadDialogue(file.getName())) {
                return false;
            }
        }
        if (!sanityCheck()) {
            new RuntimeException("Failed sanity check!").printStackTrace();
            System.exit(0);
        }
        return true;
    }

    /**
     * @return the default genre for this corpus
     */
    protected String getGenre() {
        return GENRE;
    }

}
