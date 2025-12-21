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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileLocationTest {

    @Test
    void shouldConstructWithFileThenRetrieveSameFile() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        FileLocation location = new FileLocation(file, file.getAbsolutePath());

        assertSame(file, location.getFile());
        assertEquals(file.getAbsolutePath(), location.getSpecification());
    }

    @Test
    void shouldReadFileContentsUsingByteBuffer() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        String testStr = "This is a test";

        FileUtils.writeStringToFile(file, testStr, "US-ASCII");

        FileLocation location = new FileLocation(file, file.getAbsolutePath());

        location.open();

        ByteBuffer buffer = ByteBuffer.allocate(testStr.length());
        location.read(buffer);

        assertEquals(testStr, new String(buffer.array(), StandardCharsets.US_ASCII));
    }

    @Test
    void shouldReadFileContentsUsingStream() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        String testStr = "This is a test";

        FileUtils.writeStringToFile(file, testStr, "US-ASCII");

        FileLocation location = new FileLocation(file, file.getAbsolutePath());

        location.open();

        try (InputStream stream = location.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOUtils.copy(stream, out);

            assertEquals(testStr, new String(out.toByteArray(), StandardCharsets.US_ASCII));
        }
    }

    @Test
    void shouldReadFileContentsUsingByteArray() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        String testStr = "This is a test";

        FileUtils.writeStringToFile(file, testStr, "US-ASCII");

        FileLocation location = new FileLocation(file, file.getAbsolutePath());

        location.open();

        byte[] buffer = new byte[testStr.length()];
        location.read(buffer);

        assertEquals(testStr, new String(buffer, StandardCharsets.US_ASCII));
    }

    @Test
    void shouldReadThenClose() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        String testStr = "This is a test";

        FileUtils.writeStringToFile(file, testStr, "US-ASCII");

        FileLocation location = new FileLocation(file, file.getAbsolutePath());

        location.open();

        byte[] buffer = new byte[testStr.length()];
        location.read(buffer);

        assertEquals(testStr, new String(buffer, StandardCharsets.US_ASCII));

        location.close();
    }

    @Test
    void shouldOpenThenFailToSetFile() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        TestFileLocation location = new TestFileLocation(file.getAbsolutePath());

        location.open();

        assertThrows(IllegalStateException.class, () -> location.setFile(file));
    }

    @Test
    void shouldConstructWithoutFileThenSetFileThenOpen() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        TestFileLocation location = new TestFileLocation(file.getAbsolutePath());

        location.setFile(file);
        location.open();
    }

    @Test
    void shouldConstructWithLocationThenRetrieveEquivalentFile() throws Exception {
        File file = Files.createTempFile("test.", ".file-location").toFile();
        file.deleteOnExit();

        Location location = new TestFileLocation(file.getAbsolutePath());

        assertEquals(file, location.getFile());
        assertEquals(file.getAbsolutePath(), location.getSpecification());
    }

    private static final class TestFileLocation extends FileLocation {

        TestFileLocation(String specification) {
            super(specification);
        }
    }
}
