/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.io.scan;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.shared.io.scan.mapping.SuffixMapping;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author dengliming
 */
public class SimpleResourceInclusionScannerTest {

    @Test
    public void testGetIncludedSources() throws IOException, InclusionScanException {
        File baseDir = new File("target");
        baseDir.deleteOnExit();
        baseDir.mkdirs();
        File f = Files.createTempFile(baseDir.toPath(), "source1.", ".test").toFile();

        Set<String> sourceIncludes = new HashSet<>();
        sourceIncludes.add("**/*" + f.getName());
        Set<String> sourceExcludes = new HashSet<>();
        SimpleResourceInclusionScanner simpleResourceInclusionScanner =
                new SimpleResourceInclusionScanner(sourceIncludes, sourceExcludes);
        Set<String> targets = new HashSet<>();
        targets.add(".class");
        simpleResourceInclusionScanner.addSourceMapping(new SuffixMapping(".java", targets));
        Set results = simpleResourceInclusionScanner.getIncludedSources(baseDir, baseDir);
        assertTrue(results.size() > 0);
        Object file = results.iterator().next();
        assertTrue(file instanceof File);
        assertEquals(f.getName(), ((File) file).getName());
    }
}
