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
package org.apache.maven.shared.io.location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileLocatorStrategyTest {

    @Test
    void shouldResolveExistingTempFileLocation() throws Exception {
        File f = Files.createTempFile("file-locator.", ".test").toFile();
        f.deleteOnExit();

        FileLocatorStrategy fls = new FileLocatorStrategy();

        MessageHolder mh = new DefaultMessageHolder();

        Location location = fls.resolve(f.getAbsolutePath(), mh);

        assertNotNull(location);

        assertTrue(mh.isEmpty());

        assertEquals(f, location.getFile());
    }

    @Test
    void shouldFailToResolveNonExistentFileLocation() throws Exception {
        File f = Files.createTempFile("file-locator.", ".test").toFile();
        f.delete();

        FileLocatorStrategy fls = new FileLocatorStrategy();

        MessageHolder mh = new DefaultMessageHolder();

        Location location = fls.resolve(f.getAbsolutePath(), mh);

        assertNull(location);

        System.out.println(mh.render());

        assertEquals(1, mh.size());
    }

    @Test
    void shouldNormalizeTraversalSequencesInTheSpecification(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("file.test"));
        Files.createDirectory(tempDir.resolve("subdirectory"));

        String specification = tempDir.resolve("subdirectory/../file.test").toString();

        MessageHolder mh = new DefaultMessageHolder();

        Location location = new FileLocatorStrategy().resolve(specification, mh);

        assertNotNull(location);

        assertTrue(mh.isEmpty());

        assertEquals(file.toFile(), location.getFile());

        assertEquals(specification, location.getSpecification());
    }

    @Test
    void shouldResolveRelativeSpecificationAgainstTheBaseDirectory(@TempDir Path baseDirectory) throws Exception {
        Path file = Files.createFile(baseDirectory.resolve("file.test"));

        MessageHolder mh = new DefaultMessageHolder();

        Location location = new FileLocatorStrategy(baseDirectory.toFile()).resolve("file.test", mh);

        assertNotNull(location);

        assertTrue(mh.isEmpty());

        assertEquals(file.toFile(), location.getFile());
    }

    @Test
    void shouldRefuseRelativeSpecificationThatEscapesTheBaseDirectory(
            @TempDir Path baseDirectory, @TempDir Path otherDirectory) throws Exception {
        Files.createFile(otherDirectory.resolve("file.test"));

        String specification =
                baseDirectory.relativize(otherDirectory.resolve("file.test")).toString();

        MessageHolder mh = new DefaultMessageHolder();

        Location location = new FileLocatorStrategy(baseDirectory.toFile()).resolve(specification, mh);

        assertNull(location);

        assertEquals(1, mh.size());

        assertTrue(mh.render().contains("outside of the base directory"), mh.render());
    }

    @Test
    void shouldRefuseAbsoluteSpecificationOutsideTheBaseDirectory(
            @TempDir Path baseDirectory, @TempDir Path otherDirectory) throws Exception {
        Path file = Files.createFile(otherDirectory.resolve("file.test"));

        MessageHolder mh = new DefaultMessageHolder();

        Location location = new FileLocatorStrategy(baseDirectory.toFile()).resolve(file.toString(), mh);

        assertNull(location);

        assertEquals(1, mh.size());

        assertTrue(mh.render().contains("outside of the base directory"), mh.render());
    }

    @Test
    void shouldRefuseSymbolicLinkThatPointsOutsideTheBaseDirectory(
            @TempDir Path baseDirectory, @TempDir Path otherDirectory) throws Exception {
        Path target = Files.createFile(otherDirectory.resolve("file.test"));

        assumeTrue(createSymbolicLink(baseDirectory.resolve("link.test"), target));

        MessageHolder mh = new DefaultMessageHolder();

        Location location = new FileLocatorStrategy(baseDirectory.toFile()).resolve("link.test", mh);

        assertNull(location);

        assertEquals(1, mh.size());

        assertTrue(mh.render().contains("outside of the base directory"), mh.render());
    }

    @Test
    void shouldRejectNullSpecification() {
        FileLocatorStrategy fls = new FileLocatorStrategy();

        MessageHolder mh = new DefaultMessageHolder();

        assertThrows(NullPointerException.class, () -> fls.resolve(null, mh));
    }

    private static boolean createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            // Symbolic links are unavailable on this file system.
            return false;
        }
    }
}
