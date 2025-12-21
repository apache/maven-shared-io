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
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.shared.io.scan.mapping.SuffixMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author dengliming
 */
class SimpleResourceInclusionScannerTest {

    @Test
    void getIncludedSources() throws Exception {
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
        Set<File> results = simpleResourceInclusionScanner.getIncludedSources(baseDir, baseDir);
        assertFalse(results.isEmpty());
        File file = results.iterator().next();
        assertInstanceOf(File.class, file);
        assertEquals(f.getName(), file.getName());
    }
}
