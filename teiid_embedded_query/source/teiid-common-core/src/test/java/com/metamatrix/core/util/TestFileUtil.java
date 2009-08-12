/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.core.util;

import java.io.File;
import junit.framework.TestCase;

public class TestFileUtil extends TestCase {

    public TestFileUtil(String name) {
        super(name);
    }

    public void testWriteBytes() {
        String text = "This is some sample text."; //$NON-NLS-1$
        byte[] bytes = text.getBytes();
        String testFileName = UnitTestUtil.getTestDataPath() + File.separator+ "fakeFileWriteBytes"; //$NON-NLS-1$
        FileUtil testFile = new FileUtil(testFileName);
        testFile.delete();
        testFile.writeBytes(bytes);
        String result = testFile.read();
        assertEquals(text + StringUtil.LINE_SEPARATOR, result);
        testFile.delete();
    }
}
