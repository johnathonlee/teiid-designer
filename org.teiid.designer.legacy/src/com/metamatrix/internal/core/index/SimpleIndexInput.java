/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     MetaMatrix, Inc - repackaging and updates for use as a metadata store
 *******************************************************************************/
package com.metamatrix.internal.core.index;

import java.util.ArrayList;
import com.metamatrix.core.index.IDocument;
import com.metamatrix.core.index.IEntryResult;
import com.metamatrix.core.index.IQueryResult;

/**
 * A simpleIndexInput is an input on an in memory Index.
 */

public class SimpleIndexInput extends IndexInput {
    protected WordEntry[] sortedWordEntries;
    protected IndexedFile currentFile;
    protected IndexedFile[] sortedFiles;
    protected InMemoryIndex index;

    public SimpleIndexInput( InMemoryIndex index ) {
        super();
        this.index = index;
    }

    /**
     * @see IndexInput#clearCache()
     */
    @Override
    public void clearCache() {
    }

    /**
     * @see IndexInput#close()
     */
    @Override
    public void close() {
        sortedFiles = null;
    }

    /**
     * @see IndexInput#getCurrentFile()
     */
    @Override
    public IndexedFile getCurrentFile() {
        if (!hasMoreFiles()) return null;
        return currentFile;
    }

    /**
     * @see IndexInput#getIndexedFile(int)
     */
    @Override
    public IndexedFile getIndexedFile( int fileNum ) {
        for (int i = 0; i < sortedFiles.length; i++)
            if (sortedFiles[i].getFileNumber() == fileNum) return sortedFiles[i];
        return null;
    }

    /**
     * @see IndexInput#getIndexedFile(IDocument)
     */
    @Override
    public IndexedFile getIndexedFile( IDocument document ) {
        String name = document.getName();
        for (int i = index.getNumFiles(); i >= 1; i--) {
            IndexedFile file = getIndexedFile(i);
            if (name.equals(file.getPath())) return file;
        }
        return null;
    }

    /**
     * @see IndexInput#getNumFiles()
     */
    @Override
    public int getNumFiles() {
        return index.getNumFiles();
    }

    /**
     * @see IndexInput#getNumWords()
     */
    @Override
    public int getNumWords() {
        return sortedWordEntries.length;
    }

    /**
     * @see IndexInput#getSource()
     */
    @Override
    public Object getSource() {
        return index;
    }

    public void init() {
        index.init();

    }

    /**
     * @see IndexInput#moveToNextFile()
     */
    @Override
    public void moveToNextFile() {
        filePosition++;
        if (!hasMoreFiles()) {
            return;
        }
        currentFile = sortedFiles[filePosition - 1];
    }

    /**
     * @see IndexInput#moveToNextWordEntry()
     */
    @Override
    public void moveToNextWordEntry() {
        wordPosition++;
        if (hasMoreWords()) currentWordEntry = sortedWordEntries[wordPosition - 1];
    }

    /**
     * @see IndexInput#open()
     */
    @Override
    public void open() {
        sortedWordEntries = index.getSortedWordEntries();
        sortedFiles = index.getSortedFiles();
        filePosition = 1;
        wordPosition = 1;
        setFirstFile();
        setFirstWord();
    }

    @Override
    public IEntryResult[] queryEntriesPrefixedBy( char[] prefix ) {
        return null;
    }

    @Override
    public IQueryResult[] queryFilesReferringToPrefix( char[] prefix ) {
        return null;
    }

    /**
     * @see IndexInput#queryInDocumentNames(String)
     */
    @Override
    public IQueryResult[] queryInDocumentNames( String word ) {
        setFirstFile();
        ArrayList matches = new ArrayList();
        while (hasMoreFiles()) {
            IndexedFile file = getCurrentFile();
            if (file.getPath().indexOf(word) != -1) matches.add(file.getPath());
            moveToNextFile();
        }
        IQueryResult[] match = new IQueryResult[matches.size()];
        matches.toArray(match);
        return match;
    }

    /**
     * @see IndexInput#setFirstFile()
     */
    @Override
    protected void setFirstFile() {
        filePosition = 1;
        if (sortedFiles.length > 0) {
            currentFile = sortedFiles[0];
        }
    }

    /**
     * @see IndexInput#setFirstWord()
     */
    @Override
    protected void setFirstWord() {
        wordPosition = 1;
        if (sortedWordEntries.length > 0) currentWordEntry = sortedWordEntries[0];
    }
}
