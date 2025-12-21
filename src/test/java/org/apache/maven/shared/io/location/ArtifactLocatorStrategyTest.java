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
import java.nio.file.Files;
import java.util.Collections;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ArtifactLocatorStrategyTest {

    private ArtifactFactory factory;

    private ArtifactResolver resolver;

    private ArtifactRepository localRepository;

    @BeforeEach
    void setUp() {
        factory = createMock(ArtifactFactory.class);
        resolver = createMock(ArtifactResolver.class);
        localRepository = createMock(ArtifactRepository.class);
    }

    @Test
    void shouldConstructWithoutDefaultArtifactType() {
        replay(factory, resolver, localRepository);

        new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);

        verify(factory, resolver, localRepository);
    }

    @Test
    void shouldConstructWithDefaultArtifactType() {
        replay(factory, resolver, localRepository);

        new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST, "zip");

        verify(factory, resolver, localRepository);
    }

    @Test
    void shouldFailToResolveSpecWithOneToken() {
        replay(factory, resolver, localRepository);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST, "zip");
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("one-token", mh);

        assertNull(location);
        assertEquals(1, mh.size());

        verify(factory, resolver, localRepository);
    }

    @Test
    void shouldFailToResolveSpecWithTwoTokens() {
        replay(factory, resolver, localRepository);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST, "zip");
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("two:tokens", mh);

        assertNull(location);
        assertEquals(1, mh.size());

        verify(factory, resolver, localRepository);
    }

    @Test
    void shouldResolveSpecWithThreeTokensUsingDefaultType() throws Exception {
        File tempFile = Files.createTempFile("artifact-location.", ".temp").toFile();
        tempFile.deleteOnExit();

        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(tempFile);
        expect(artifact.getFile()).andReturn(tempFile);

        expect(factory.createArtifact("group", "artifact", "version", null, "jar"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version", mh);

        assertNotNull(location);
        assertEquals(0, mh.size());

        assertSame(tempFile, location.getFile());

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldResolveSpecWithThreeTokensUsingCustomizedDefaultType() throws Exception {
        File tempFile = Files.createTempFile("artifact-location.", ".temp").toFile();
        tempFile.deleteOnExit();

        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(tempFile);
        expect(artifact.getFile()).andReturn(tempFile);

        expect(factory.createArtifact("group", "artifact", "version", null, "zip"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST, "zip");
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version", mh);

        assertNotNull(location);
        assertEquals(0, mh.size());

        assertSame(tempFile, location.getFile());

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldResolveSpecWithFourTokens() throws Exception {
        File tempFile = Files.createTempFile("artifact-location.", ".temp").toFile();
        tempFile.deleteOnExit();

        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(tempFile);
        expect(artifact.getFile()).andReturn(tempFile);

        expect(factory.createArtifact("group", "artifact", "version", null, "zip"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version:zip", mh);

        assertNotNull(location);
        assertEquals(0, mh.size());

        assertSame(tempFile, location.getFile());

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldResolveSpecWithFiveTokens() throws Exception {
        File tempFile = Files.createTempFile("artifact-location.", ".temp").toFile();
        tempFile.deleteOnExit();

        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(tempFile);
        expect(artifact.getFile()).andReturn(tempFile);

        expect(factory.createArtifactWithClassifier("group", "artifact", "version", "zip", "classifier"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version:zip:classifier", mh);

        assertNotNull(location);
        assertEquals(0, mh.size());

        assertSame(tempFile, location.getFile());

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldResolveSpecWithFiveTokensAndEmptyTypeToken() throws Exception {
        File tempFile = Files.createTempFile("artifact-location.", ".temp").toFile();
        tempFile.deleteOnExit();

        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(tempFile);
        expect(artifact.getFile()).andReturn(tempFile);

        expect(factory.createArtifactWithClassifier("group", "artifact", "version", "jar", "classifier"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version::classifier", mh);

        assertNotNull(location);
        assertEquals(0, mh.size());

        assertSame(tempFile, location.getFile());

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldResolveSpecWithMoreThanFiveTokens() throws Exception {
        File tempFile = Files.createTempFile("artifact-location.", ".temp").toFile();
        tempFile.deleteOnExit();

        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(tempFile);
        expect(artifact.getFile()).andReturn(tempFile);

        expect(factory.createArtifactWithClassifier("group", "artifact", "version", "zip", "classifier"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version:zip:classifier:six:seven", mh);

        assertNotNull(location);
        assertEquals(1, mh.size());

        assertTrue(mh.render().contains(":six:seven"));

        assertSame(tempFile, location.getFile());

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldNotResolveSpecToArtifactWithNullFile() throws Exception {
        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getFile()).andReturn(null);
        expect(artifact.getId()).andReturn("<some-artifact-id>");

        expect(factory.createArtifact("group", "artifact", "version", null, "jar"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version", mh);

        assertNull(location);
        assertEquals(1, mh.size());

        assertTrue(mh.render().contains("<some-artifact-id>"));

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldNotResolveWhenArtifactNotFoundExceptionThrown() throws Exception {
        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getId()).andReturn("<some-artifact-id>");

        expect(factory.createArtifact("group", "artifact", "version", null, "jar"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
            expectLastCall()
                    .andThrow(new ArtifactNotFoundException(
                            "not found",
                            "group",
                            "artifact",
                            "version",
                            "jar",
                            null,
                            Collections.<ArtifactRepository>emptyList(),
                            "http://nowhere.com",
                            Collections.<String>emptyList(),
                            new NullPointerException()));
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version", mh);

        assertNull(location);
        assertEquals(1, mh.size());

        assertTrue(mh.render().contains("<some-artifact-id>"));
        assertTrue(mh.render().contains("not found"));

        verify(factory, resolver, localRepository, artifact);
    }

    @Test
    void shouldNotResolveWhenArtifactResolutionExceptionThrown() throws Exception {
        Artifact artifact = createMock(Artifact.class);

        expect(artifact.getId()).andReturn("<some-artifact-id>");

        expect(factory.createArtifact("group", "artifact", "version", null, "jar"))
                .andReturn(artifact);

        try {
            resolver.resolve(artifact, Collections.<ArtifactRepository>emptyList(), localRepository);
            expectLastCall()
                    .andThrow(new ArtifactResolutionException(
                            "resolution failed",
                            "group",
                            "artifact",
                            "version",
                            "jar",
                            null,
                            Collections.<ArtifactRepository>emptyList(),
                            Collections.<String>emptyList(),
                            new NullPointerException()));

        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            // should never happen
            fail("This should NEVER happen. It's a mock!");
        }

        replay(factory, resolver, localRepository, artifact);

        LocatorStrategy strategy =
                new ArtifactLocatorStrategy(factory, resolver, localRepository, Collections.EMPTY_LIST);
        MessageHolder mh = new DefaultMessageHolder();

        Location location = strategy.resolve("group:artifact:version", mh);

        assertNull(location);
        assertEquals(1, mh.size());

        assertTrue(mh.render().contains("<some-artifact-id>"));
        assertTrue(mh.render().contains("resolution failed"));

        verify(factory, resolver, localRepository, artifact);
    }
}
