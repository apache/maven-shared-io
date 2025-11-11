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

import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FileLocatorStrategyTest {

    @Test
    public void testShouldResolveExistingTempFileLocation() throws IOException {
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
    public void testShouldFailToResolveNonExistentFileLocation() throws IOException {
        File f = Files.createTempFile("file-locator.", ".test").toFile();
        f.delete();

        FileLocatorStrategy fls = new FileLocatorStrategy();

        MessageHolder mh = new DefaultMessageHolder();

        Location location = fls.resolve(f.getAbsolutePath(), mh);

        assertNull(location);

        System.out.println(mh.render());

        assertEquals(1, mh.size());
    }
}
