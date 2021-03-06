/*
 * Copyright 2002-2009 Andy Clark, Marc Guillemot
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.htmlunit.cyberneko;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.xerces.impl.Version;
import org.apache.xerces.xni.parser.XMLDocumentFilter;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParserConfiguration;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * This test generates canonical result using the <code>Writer</code> class
 * and compares it against the expected canonical output. Simple as that.
 *
 * @author Andy Clark
 * @author Marc Guillemot
 * @author Ahmed Ashour
 */
public class CanonicalTest extends TestCase {

    private static final File dataDir = new File("src/test/resources");
    private static final File canonicalDir = new File("src/test/resources/canonical");
    private static final File outputDir = new File("target/data/output/" + Version.getVersion());
    private final File dataFile;

    public static Test suite() throws Exception {
        System.out.println(canonicalDir.getAbsolutePath());
        outputDir.mkdirs();

        final TestSuite suite = new TestSuite();
        final List<File> dataFiles = new ArrayList<>();
        dataDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File file) {
                final String name = file.getName();
                if (file.isDirectory() && !"canonical".equals(name)) {
                    file.listFiles(this);
                }
                else if (name.startsWith("test") && name.endsWith(".html")) {
                    dataFiles.add(file);
                }
                return false; // we don't care to listFiles' result
            }
        });
        Collections.sort(dataFiles);

        for (int i=0; i < dataFiles.size(); i++) {
            suite.addTest(new CanonicalTest(dataFiles.get(i)));
        }
        return suite;
    }

    CanonicalTest(final File dataFile) throws Exception {
        super(dataFile.getName() + " [" + Version.getVersion() + "]");
        this.dataFile = dataFile;
    }


    @Override
    protected void runTest() throws Exception {
        final String dataLines = getResult(dataFile);
        try
        {
            // prepare for future changes where canonical files are next to test file
            File canonicalFile = new File(dataFile.getParentFile(), dataFile.getName() + ".canonical");
            if (!canonicalFile.exists()) {
                canonicalFile = new File(canonicalDir, dataFile.getName());
            }
            if (!canonicalFile.exists()) {
                fail("Canonical file not found for input: " + dataFile.getAbsolutePath() + ": " + dataLines);
            }

            File nyiFile = new File(dataFile.getParentFile(), dataFile.getName() + ".notyetimplemented");
            if (nyiFile.exists()) {
                try {
                    assertEquals(dataFile.toString(), getCanonical(canonicalFile), dataLines);
                    fail("test " + dataFile.getName() + "is marked as not yet implemented but already works");
                }
                catch (final AssertionFailedError e) {
                    // expected
                }
                assertEquals("NYI: " + dataFile.toString(), getCanonical(nyiFile), dataLines);
            } else {
                assertEquals(dataFile.toString(), getCanonical(canonicalFile), dataLines);
            }
        }
        catch (final AssertionFailedError e) {
            final File output = new File(outputDir, dataFile.getName());
            try (final PrintWriter pw = new PrintWriter(new FileOutputStream(output))) {
                pw.print(dataLines);
            }
            throw e;
        }
    }

    private static String getCanonical(final File infile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new UTF8BOMSkipper(new FileInputStream(infile)), "UTF-8"))) {
            final StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private static String getResult(final File infile) throws IOException {
        try (StringWriter out = new StringWriter()) {
            // create filters
            final XMLDocumentFilter[] filters = { new Writer(out) };

            // create parser
            final XMLParserConfiguration parser = new HTMLConfiguration();

            // parser settings
            parser.setProperty("http://cyberneko.org/html/properties/filters", filters);
            final String infilename = infile.toString();
            final File insettings = new File(infilename+".settings");
            if (insettings.exists()) {
                try (BufferedReader settings = new BufferedReader(new FileReader(insettings))) {
                    String settingline;
                    while ((settingline = settings.readLine()) != null) {
                        final StringTokenizer tokenizer = new StringTokenizer(settingline);
                        final String type = tokenizer.nextToken();
                        final String id = tokenizer.nextToken();
                        final String value = tokenizer.nextToken();
                        if (type.equals("feature")) {
                            parser.setFeature(id, value.equals("true"));
                            if (HTMLScanner.REPORT_ERRORS.equals(id)) {
                                parser.setErrorHandler(new HTMLErrorHandler(out));
                            }
                        }
                        else {
                            parser.setProperty(id, value);
                        }
                    }
                }
            }

            // parse
            parser.parse(new XMLInputSource(null, infilename, null));
            final BufferedReader reader = new BufferedReader(new StringReader(out.toString()));
            final StringBuffer sb = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
