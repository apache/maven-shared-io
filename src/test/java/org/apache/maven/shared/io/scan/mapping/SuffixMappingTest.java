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
package org.apache.maven.shared.io.scan.mapping;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jdcasey
 */
class SuffixMappingTest {
    @Test
    void shouldReturnSingleClassFileForSingleJavaFile() throws Exception {
        String base = "path/to/file";

        File basedir = new File(".");

        SuffixMapping mapping = new SuffixMapping(".java", ".class");

        Set<File> results = mapping.getTargetFiles(basedir, base + ".java");

        assertEquals(1, results.size(), "Returned wrong number of target files.");

        assertEquals(new File(basedir, base + ".class"), results.iterator().next(), "Target file is wrong.");
    }

    @Test
    void shouldNotReturnClassFileWhenSourceFileHasWrongSuffix() throws Exception {
        String base = "path/to/file";

        File basedir = new File(".");

        SuffixMapping mapping = new SuffixMapping(".java", ".class");

        Set<File> results = mapping.getTargetFiles(basedir, base + ".xml");

        assertTrue(results.isEmpty(), "Returned wrong number of target files.");
    }

    @Test
    void shouldReturnOneClassFileAndOneXmlFileForSingleJavaFile() throws Exception {
        String base = "path/to/file";

        File basedir = new File(".");

        Set<String> targets = new HashSet<>();
        targets.add(".class");
        targets.add(".xml");

        SuffixMapping mapping = new SuffixMapping(".java", targets);

        Set<File> results = mapping.getTargetFiles(basedir, base + ".java");

        assertEquals(2, results.size(), "Returned wrong number of target files.");

        assertTrue(results.contains(new File(basedir, base + ".class")), "Targets do not contain class target.");

        assertTrue(results.contains(new File(basedir, base + ".xml")), "Targets do not contain class target.");
    }

    @Test
    void shouldReturnNoTargetFilesWhenSourceFileHasWrongSuffix() throws Exception {
        String base = "path/to/file";

        File basedir = new File(".");

        Set<String> targets = new HashSet<>();
        targets.add(".class");
        targets.add(".xml");

        SuffixMapping mapping = new SuffixMapping(".java", targets);

        Set<File> results = mapping.getTargetFiles(basedir, base + ".apt");

        assertTrue(results.isEmpty(), "Returned wrong number of target files.");
    }

    @Test
    void singleTargetMapper() throws Exception {
        String base = "path/to/file";

        File basedir = new File("target/");

        SingleTargetMapping mapping = new SingleTargetMapping(".cs", "/foo");

        Set<File> results = mapping.getTargetFiles(basedir, base + ".apt");

        assertTrue(results.isEmpty());

        results = mapping.getTargetFiles(basedir, base + ".cs");

        assertEquals(1, results.size());
    }
}
