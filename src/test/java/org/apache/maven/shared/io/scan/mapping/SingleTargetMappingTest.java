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
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTargetMappingTest {

    @Test
    void testGetTargetFilesShouldReturnEmptySetWhenSuffixDoesNotMatch() throws Exception {
        SingleTargetMapping mapping = new SingleTargetMapping(".cs", "/foo");
        File basedir = new File("target/");

        Set<File> results = mapping.getTargetFiles(basedir, "path/to/file.apt");

        assertTrue(results.isEmpty());
    }

    @Test
    void testGetTargetFilesShouldReturnCorrectFileWhenSuffixMatches() throws Exception {
        SingleTargetMapping mapping = new SingleTargetMapping(".cs", "/foo");
        File basedir = new File("target/");

        Set<File> results = mapping.getTargetFiles(basedir, "path/to/file.cs");

        assertEquals(1, results.size());
        assertEquals(new File(basedir, "/foo"), results.iterator().next());
    }

    @Test
    void testGetTargetFilesShouldHandleNullSourceAndSuffixGracefully() {
        SingleTargetMapping mapping = new SingleTargetMapping(null, "/foo");
        File basedir = new File("target/");

        assertDoesNotThrow(() -> {
            Set<File> results = mapping.getTargetFiles(basedir, null);
            assertTrue(results.isEmpty());
        });
    }
}
