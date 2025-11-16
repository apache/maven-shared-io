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

import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ClasspathResourceLocatorStrategyTest {

    @Test
    public void testShouldConstructWithNoParams() {
        new ClasspathResourceLocatorStrategy();
    }

    @Test
    public void testShouldConstructWithTempFileOptions() {
        new ClasspathResourceLocatorStrategy("prefix.", ".suffix", true);
    }

    @Test
    public void testShouldFailToResolveMissingClasspathResource() {
        MessageHolder mh = new DefaultMessageHolder();
        Location location = new ClasspathResourceLocatorStrategy().resolve("/some/missing/path", mh);

        assertNull(location);
        assertEquals(1, mh.size());
    }

    @Test
    public void testShouldResolveExistingClasspathResourceWithoutPrecedingSlash() {
        MessageHolder mh = new DefaultMessageHolder();
        Location location = new ClasspathResourceLocatorStrategy().resolve("META-INF/maven/test.properties", mh);

        assertNotNull(location);
        assertEquals(0, mh.size());
    }
}
