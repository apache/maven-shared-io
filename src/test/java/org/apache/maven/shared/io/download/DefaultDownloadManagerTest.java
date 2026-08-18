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
package org.apache.maven.shared.io.download;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.shared.io.logging.DefaultMessageHolder;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.easymock.Capture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DefaultDownloadManagerTest {

    private WagonManager wagonManager;

    private Wagon wagon;

    @BeforeEach
    void setUp() {
        wagonManager = createMock(WagonManager.class);
        wagon = createMock(Wagon.class);
    }

    @Test
    void shouldConstructWithNoParamsAndHaveNonNullMessageHolder() {
        new DefaultDownloadManager();
    }

    @Test
    void shouldFailToDownloadWhenWagonManagerIsNull() {
        DefaultDownloadManager downloadManager = new DefaultDownloadManager();
        try {
            downloadManager.download("http://example.com/file.txt", new DefaultMessageHolder());
            fail("Should have thrown DownloadFailedException.");
        } catch (DownloadFailedException e) {
            assertTrue(e.getMessage().contains("WagonManager not set"));
        }
    }

    @Test
    void shouldFailToDownloadWhenGetWagonReturnsNull() throws Exception {
        expect(wagonManager.getWagon("file")).andReturn(null);
        replay(wagonManager);

        DefaultDownloadManager downloadManager = new DefaultDownloadManager(wagonManager);
        try {
            downloadManager.download(
                    Files.createTempFile("download-source", "test")
                            .toFile()
                            .toURI()
                            .toASCIIString(),
                    new DefaultMessageHolder());
            fail("Should have thrown DownloadFailedException.");
        } catch (DownloadFailedException e) {
            assertTrue(e.getMessage().contains("No wagon available"));
        }

        verify(wagonManager);
    }

    @Test
    void shouldConstructWithWagonManager() {
        replay(wagonManager);

        new DefaultDownloadManager(wagonManager);

        verify(wagonManager);
    }

    @Test
    void shouldFailToDownloadMalformedURL() {
        replay(wagonManager);

        DownloadManager mgr = new DefaultDownloadManager(wagonManager);

        try {
            mgr.download("://nothing.com/index.html", new DefaultMessageHolder());

            fail("Should not download with invalid URL.");
        } catch (DownloadFailedException e) {
            assertTrue(e.getMessage().contains("invalid URL"));
        }

        verify(wagonManager);
    }

    @Test
    void shouldDownloadFromTempFileWithNoTransferListeners() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupDefaultMockConfiguration();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDownloadFromTempFileTwiceAndUseCache() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupDefaultMockConfiguration();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        File first = downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

        MessageHolder mh = new DefaultMessageHolder();

        File second = downloadManager.download(tempFile.toURI().toASCIIString(), mh);

        assertSame(first, second);
        assertEquals(1, mh.size());
        assertTrue(mh.render().contains("Using cached"));

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDownloadFromTempFileWithOneTransferListener() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupDefaultMockConfiguration();

        TransferListener transferListener = createMock(TransferListener.class);

        wagon.addTransferListener(transferListener);

        wagon.removeTransferListener(transferListener);

        replay(wagon, wagonManager, transferListener);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        downloadManager.download(
                tempFile.toURI().toASCIIString(),
                Collections.singletonList(transferListener),
                new DefaultMessageHolder());

        verify(wagon, wagonManager, transferListener);
    }

    @Test
    void shouldFailToDownloadWhenWagonProtocolNotFound() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonManagerGetException(new UnsupportedProtocolException("not supported"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

            fail("should have failed to retrieve wagon.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("UnsupportedProtocolException"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldFailToDownloadWhenWagonConnectThrowsConnectionException() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonConnectionException(new ConnectionException("connect error"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

            fail("should have failed to connect wagon.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("ConnectionException"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldFailToDownloadWhenWagonConnectThrowsAuthenticationException() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonConnectionException(new AuthenticationException("bad credentials"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

            fail("should have failed to connect wagon.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("AuthenticationException"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldFailToDownloadWhenWagonGetThrowsTransferFailedException() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonGetException(new TransferFailedException("bad transfer"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

            fail("should have failed to get resource.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("TransferFailedException"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldFailToDownloadWhenWagonGetThrowsResourceDoesNotExistException() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonGetException(new ResourceDoesNotExistException("bad resource"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

            fail("should have failed to get resource.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("ResourceDoesNotExistException"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldFailToDownloadWhenWagonGetThrowsAuthorizationException() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonGetException(new AuthorizationException("bad transfer"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

            fail("should have failed to get resource.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("AuthorizationException"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldFailToDownloadWhenWagonDisconnectThrowsConnectionException() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonDisconnectException(new ConnectionException("not connected"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        MessageHolder mh = new DefaultMessageHolder();

        downloadManager.download(tempFile.toURI().toASCIIString(), mh);

        assertTrue(mh.render().contains("ConnectionException"));

        verify(wagon, wagonManager);
    }

    @Test
    void shouldUseCorrectBaseUrlWhenUrlHasQueryString() throws Exception {
        String urlWithQuery = "http://example.com/path/file.jar?token=abc";

        Capture<Repository> repoCapture = newCapture();

        expect(wagonManager.getWagon("http")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        wagon.connect(capture(repoCapture), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        wagon.get(anyString(), anyObject(File.class));
        wagon.disconnect();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);
        downloadManager.download(urlWithQuery, new DefaultMessageHolder());

        verify(wagon, wagonManager);

        assertEquals("http://example.com", repoCapture.getValue().getUrl());
    }

    @Test
    void shouldUseCorrectBaseUrlWhenUrlHasFragment() throws Exception {
        String urlWithFragment = "http://example.com/path/file.jar#section";

        Capture<Repository> repoCapture = newCapture();

        expect(wagonManager.getWagon("http")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        wagon.connect(capture(repoCapture), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        wagon.get(anyString(), anyObject(File.class));
        wagon.disconnect();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);
        downloadManager.download(urlWithFragment, new DefaultMessageHolder());

        verify(wagon, wagonManager);

        assertEquals("http://example.com", repoCapture.getValue().getUrl());
    }

    @Test
    void shouldPreserveUserInfoAndPortInBaseUrl() throws Exception {
        String urlWithCredentials = "http://user:secret@example.com:8080/path/file.jar?token=abc";

        Capture<Repository> repoCapture = newCapture();
        Capture<String> pathCapture = newCapture();

        expect(wagonManager.getWagon("http")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        wagon.connect(capture(repoCapture), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        wagon.get(capture(pathCapture), anyObject(File.class));
        wagon.disconnect();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);
        downloadManager.download(urlWithCredentials, new DefaultMessageHolder());

        verify(wagon, wagonManager);

        Repository repo = repoCapture.getValue();
        assertEquals("example.com", repo.getHost());
        assertEquals(8080, repo.getPort());
        assertEquals("user", repo.getUsername());
        assertEquals("secret", repo.getPassword());
        // Repository strips the credentials from the URL once it has parsed them out.
        assertEquals("http://example.com:8080", repo.getUrl());
        assertEquals("/path/file.jar", pathCapture.getValue());
    }

    @Test
    void shouldPreserveBracketedIpv6HostAndPortInBaseUrl() throws Exception {
        String ipv6Url = "http://[::1]:8081/path/file.jar#section";

        Capture<Repository> repoCapture = newCapture();
        Capture<String> pathCapture = newCapture();

        expect(wagonManager.getWagon("http")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        wagon.connect(capture(repoCapture), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        wagon.get(capture(pathCapture), anyObject(File.class));
        wagon.disconnect();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);
        downloadManager.download(ipv6Url, new DefaultMessageHolder());

        verify(wagon, wagonManager);

        Repository repo = repoCapture.getValue();
        assertEquals("::1", repo.getHost());
        assertEquals(8081, repo.getPort());
        assertEquals("http://[::1]:8081", repo.getUrl());
        assertEquals("/path/file.jar", pathCapture.getValue());
    }

    @Test
    void shouldFailToDownloadNonFileUrlWithoutAuthority() throws Exception {
        // The wagon is resolved before the base URL is built, so this gets past the protocol check.
        expect(wagonManager.getWagon("http")).andReturn(wagon);

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download("http:/path/file.jar", new DefaultMessageHolder());

            fail("Should not download a URL without an authority component.");
        } catch (DownloadFailedException e) {
            assertTrue(e.getMessage().contains("without an authority component"));
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDownloadFileUrlWithoutAuthority() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        Capture<Repository> repoCapture = newCapture();

        expect(wagonManager.getWagon("file")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        wagon.connect(capture(repoCapture), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        wagon.get(anyString(), anyObject(File.class));
        wagon.disconnect();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        // File URLs have no authority (file:/tmp/...); wagon resolves these against localhost.
        downloadManager.download("file:" + tempFile.getAbsolutePath(), new DefaultMessageHolder());

        verify(wagon, wagonManager);

        assertEquals("file:", repoCapture.getValue().getUrl());
    }

    @Test
    void shouldDownloadConcurrentlyAndCacheResults() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expect(wagonManager.getWagon("file")).andReturn(wagon).anyTimes();
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null).anyTimes();
        expect(wagonManager.getProxy(anyString())).andReturn(null).anyTimes();
        wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        expectLastCall().anyTimes();
        wagon.get(anyString(), anyObject(File.class));
        expectLastCall().anyTimes();
        wagon.disconnect();
        expectLastCall().anyTimes();

        replay(wagon, wagonManager);

        DefaultDownloadManager mgr = new DefaultDownloadManager(wagonManager);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(executor.submit(() -> {
                try {
                    mgr.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());
                } catch (DownloadFailedException e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        verify(wagon, wagonManager);
    }

    @Test
    void shouldDeleteTempFileOnConnectionFailure() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupMocksWithWagonConnectionException(new ConnectionException("connect error"));

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        Set<String> filesBefore = listDownloadTempFiles();

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());
            fail("should have failed to connect wagon.");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("ConnectionException"));
        }

        Set<String> filesAfter = listDownloadTempFiles();
        filesAfter.removeAll(filesBefore);
        assertTrue(filesAfter.isEmpty(), "Temp file must be deleted immediately when connection fails, not leaked");

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDeleteTempFileOnTransferFailure() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expect(wagonManager.getWagon("file")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        try {
            wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        Capture<File> capturedTempFile = newCapture();
        try {
            wagon.get(anyString(), capture(capturedTempFile));
            expectLastCall().andThrow(new TransferFailedException("bad transfer"));
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(() -> wagon.disconnect(), "This shouldn't happen!!");

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        try {
            downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());
            fail("should have thrown DownloadFailedException");
        } catch (DownloadFailedException e) {
            assertTrue(ExceptionUtils.getStackTrace(e).contains("TransferFailedException"));
        }

        assertTrue(capturedTempFile.hasCaptured(), "wagon.get() should have been called");
        assertFalse(
                capturedTempFile.getValue().exists(),
                "Temp file must be deleted immediately when transfer fails, not leaked");

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDownloadIntoTheSharedTempDirectoryInsteadOfRegisteringDeleteOnExit() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expect(wagonManager.getWagon("file")).andReturn(wagon);
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);
        expect(wagonManager.getProxy(anyString())).andReturn(null);
        wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));

        Capture<File> capturedTempFile = newCapture();
        wagon.get(anyString(), capture(capturedTempFile));
        wagon.disconnect();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

        // Downloads live under one directory that a single shutdown hook removes, so the amount of
        // JVM shutdown bookkeeping stays constant instead of growing with every download.
        Path downloadPath = capturedTempFile.getValue().toPath().toAbsolutePath();
        Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();

        assertTrue(downloadPath.startsWith(tempRoot), "Download must stay in the temp directory: " + downloadPath);
        assertTrue(
                tempRoot.relativize(downloadPath).getName(0).toString().startsWith("maven-shared-io-downloads-"),
                "Download must land in the shared download directory: " + downloadPath);

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDownloadAgainWhenTheCachedFileWasDeleted() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expect(wagonManager.getWagon("file")).andReturn(wagon).anyTimes();
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null).anyTimes();
        expect(wagonManager.getProxy(anyString())).andReturn(null).anyTimes();
        wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        expectLastCall().anyTimes();
        wagon.get(anyString(), anyObject(File.class));
        expectLastCall().anyTimes();
        wagon.disconnect();
        expectLastCall().anyTimes();

        replay(wagon, wagonManager);

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        String url = tempFile.toURI().toASCIIString();

        File first = downloadManager.download(url, new DefaultMessageHolder());
        assertTrue(first.delete(), "should have deleted the downloaded file");

        File second = downloadManager.download(url, new DefaultMessageHolder());

        assertTrue(second.exists(), "must not hand back the stale cache entry of a deleted file");
        assertFalse(first.equals(second), "must download to a fresh file");

        // The stale entry has been replaced, so the next request is served from the cache again.
        assertSame(second, downloadManager.download(url, new DefaultMessageHolder()));

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDeleteDownloadedFilesOnCleanup() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupDefaultMockConfiguration();

        replay(wagon, wagonManager);

        DefaultDownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        File downloaded = downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());
        assertTrue(downloaded.exists());

        downloadManager.cleanup();

        assertFalse(downloaded.exists(), "cleanup() must delete the downloaded file");
        assertFalse(downloaded.getParentFile().exists(), "cleanup() must delete the manager's directory");

        verify(wagon, wagonManager);
    }

    @Test
    void shouldStillBeUsableAfterCleanup() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expect(wagonManager.getWagon("file")).andReturn(wagon).anyTimes();
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null).anyTimes();
        expect(wagonManager.getProxy(anyString())).andReturn(null).anyTimes();
        wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        expectLastCall().anyTimes();
        wagon.get(anyString(), anyObject(File.class));
        expectLastCall().anyTimes();
        wagon.disconnect();
        expectLastCall().anyTimes();

        replay(wagon, wagonManager);

        DefaultDownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        String url = tempFile.toURI().toASCIIString();

        downloadManager.download(url, new DefaultMessageHolder());
        downloadManager.cleanup();

        File afterCleanup = downloadManager.download(url, new DefaultMessageHolder());

        assertTrue(afterCleanup.exists(), "must download into a freshly created directory after cleanup()");

        verify(wagon, wagonManager);
    }

    @Test
    void shouldNotDeleteTheFilesOfAnotherManagerOnCleanup() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expect(wagonManager.getWagon("file")).andReturn(wagon).anyTimes();
        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null).anyTimes();
        expect(wagonManager.getProxy(anyString())).andReturn(null).anyTimes();
        wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        expectLastCall().anyTimes();
        wagon.get(anyString(), anyObject(File.class));
        expectLastCall().anyTimes();
        wagon.disconnect();
        expectLastCall().anyTimes();

        replay(wagon, wagonManager);

        String url = tempFile.toURI().toASCIIString();

        DefaultDownloadManager first = new DefaultDownloadManager(wagonManager);
        DefaultDownloadManager second = new DefaultDownloadManager(wagonManager);

        File keptFile = first.download(url, new DefaultMessageHolder());
        File droppedFile = second.download(url, new DefaultMessageHolder());

        second.cleanup();

        assertFalse(droppedFile.exists(), "cleanup() must delete the files of its own manager");
        assertTrue(keptFile.exists(), "cleanup() must not delete the files of another manager");

        first.cleanup();

        verify(wagon, wagonManager);
    }

    @Test
    void shouldRegisterAtMostOneShutdownHookHoweverManyDownloadsAndManagers() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expectAnyNumberOfDownloads();

        String url = tempFile.toURI().toASCIIString();

        // Trigger the first download so that the root, and its hook, exist before counting.
        DefaultDownloadManager warmUp = new DefaultDownloadManager(wagonManager);
        warmUp.download(url, new DefaultMessageHolder());

        int hooksAfterFirstDownload = DefaultDownloadManager.registeredShutdownHooks();

        assertEquals(1, hooksAfterFirstDownload, "the root must be removed by a single shutdown hook");

        List<DefaultDownloadManager> managers = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            DefaultDownloadManager manager = new DefaultDownloadManager(wagonManager);
            managers.add(manager);

            manager.download(url, new DefaultMessageHolder());
            manager.download(url + "?run=" + i, new DefaultMessageHolder());
            manager.cleanup();
            manager.download(url, new DefaultMessageHolder());
        }

        assertEquals(
                hooksAfterFirstDownload,
                DefaultDownloadManager.registeredShutdownHooks(),
                "downloads, managers and cleanup() must not add shutdown hooks");

        for (DefaultDownloadManager manager : managers) {
            manager.cleanup();
        }

        warmUp.cleanup();

        verify(wagon, wagonManager);
    }

    @Test
    void shouldNotRegisterAnotherShutdownHookWhenTheRootIsRemovedBehindOurBack() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expectAnyNumberOfDownloads();

        String url = tempFile.toURI().toASCIIString();

        DefaultDownloadManager downloadManager = new DefaultDownloadManager(wagonManager);
        downloadManager.download(url, new DefaultMessageHolder());

        int hooksBefore = DefaultDownloadManager.registeredShutdownHooks();

        // A temp dir sweeper removes the whole root between downloads, repeatedly.
        for (int i = 0; i < 25; i++) {
            for (Path root : listDownloadRoots()) {
                deleteRecursively(root);
            }

            File downloaded = downloadManager.download(url + "?sweep=" + i, new DefaultMessageHolder());

            assertTrue(downloaded.exists(), "must recreate the root and keep downloading after a sweep");
        }

        assertEquals(
                hooksBefore,
                DefaultDownloadManager.registeredShutdownHooks(),
                "recreating the root must reuse the existing shutdown hook, not add one per root");

        downloadManager.cleanup();

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDeleteNestedDirectoriesOnCleanupWithoutFollowingSymbolicLinks() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        expectAnyNumberOfDownloads();

        DefaultDownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        File downloaded = downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

        // Whatever a wagon leaves in the download directory has to go too, including a subdirectory
        // and a link pointing outside the tree, whose target must survive.
        Path directory = downloaded.toPath().getParent();
        Path nested = Files.createDirectories(directory.resolve("nested/deeper"));
        Path nestedFile = Files.createFile(nested.resolve("leftover.tmp"));

        Path outsideTarget = Files.createTempFile("outside-target", ".tmp");
        outsideTarget.toFile().deleteOnExit();

        Path link = directory.resolve("link-to-outside");
        boolean linkCreated;
        try {
            Files.createSymbolicLink(link, outsideTarget);
            linkCreated = true;
        } catch (IOException | UnsupportedOperationException e) {
            // Some platforms need a privilege for this; the rest of the assertions still apply.
            linkCreated = false;
        }

        downloadManager.cleanup();

        assertFalse(Files.exists(nestedFile), "cleanup() must delete files in nested directories");
        assertFalse(Files.exists(directory), "cleanup() must delete the download directory itself");

        if (linkCreated) {
            assertTrue(Files.exists(outsideTarget), "cleanup() must not follow a symbolic link out of the tree");
        }

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDeleteTheRootWhenTheShutdownHookCanNoLongerLoadClasses() throws Exception {
        // The hook runs at JVM exit, when the class loader that defined DefaultDownloadManager may
        // already be closed, as a Maven plugin realm is at the end of a build. A class the hook only
        // needs then, commons-io for one, could no longer be resolved, so the hook would fail and
        // delete nothing. Load the manager in isolation, hide commons-io once the root exists, and
        // check the hook still deletes: it must load what it needs before it is registered.
        URL classes = DefaultDownloadManager.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();

        HidingClassLoader hiding = new HidingClassLoader(DefaultDownloadManagerTest.class.getClassLoader());

        try (URLClassLoader isolated = new URLClassLoader(new URL[] {classes}, hiding)) {
            Class<?> isolatedManager = isolated.loadClass(DefaultDownloadManager.class.getName());

            assertSame(isolated, isolatedManager.getClassLoader(), "the manager must come from the isolated loader");

            Method downloadDirectory = isolatedManager.getDeclaredMethod("downloadDirectory");
            downloadDirectory.setAccessible(true);
            Method deleteDownloadRoot = isolatedManager.getDeclaredMethod("deleteDownloadRoot");
            deleteDownloadRoot.setAccessible(true);

            File directory = (File)
                    downloadDirectory.invoke(isolatedManager.getConstructor().newInstance());
            Path downloaded = Files.createFile(directory.toPath().resolve("download-0"));
            Path nested = Files.createDirectories(directory.toPath().resolve("nested"));
            Path nestedFile = Files.createFile(nested.resolve("leftover.tmp"));

            // Stand in for the closed plugin realm: from here on no commons-io class can be loaded.
            // Classes the manager already resolved stay usable, exactly as they do in a closed realm,
            // which is why the manager has to resolve them before the hook is registered.
            hiding.hideCommonsIo();

            assertThrows(
                    ClassNotFoundException.class,
                    () -> isolated.loadClass("org.apache.commons.io.monitor.FileAlterationMonitor"),
                    "no commons-io class may still be loaded through the isolated loader by now");

            deleteDownloadRoot.invoke(null);

            assertFalse(Files.exists(downloaded), "the shutdown hook must delete the downloaded files");
            assertFalse(Files.exists(nestedFile), "the shutdown hook must delete nested files");
            assertFalse(Files.exists(directory.toPath()), "the shutdown hook must delete the download directory");
            assertFalse(Files.exists(directory.toPath().getParent()), "the shutdown hook must delete the root itself");
        }
    }

    /**
     * Hides the download package, so that the child loader has to define it itself, and hides
     * commons-io from {@link #hideCommonsIo()} on, standing in for a plugin realm that is closed
     * while the shutdown hook runs. Everything else comes from the test's own loader.
     */
    private static final class HidingClassLoader extends ClassLoader {

        private volatile boolean commonsIoHidden;

        HidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        void hideCommonsIo() {
            commonsIoHidden = true;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.apache.maven.shared.io.download.")
                    || (commonsIoHidden && name.startsWith("org.apache.commons.io."))) {
                throw new ClassNotFoundException(name + " is hidden by this test");
            }

            return super.loadClass(name, resolve);
        }
    }

    private void expectAnyNumberOfDownloads() {
        assertDoesNotThrow(
                () -> expect(wagonManager.getWagon("file")).andReturn(wagon).anyTimes(), "This shouldn't happen!!");

        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null).anyTimes();
        expect(wagonManager.getProxy(anyString())).andReturn(null).anyTimes();

        assertDoesNotThrow(
                () -> {
                    wagon.connect(
                            anyObject(Repository.class),
                            anyObject(AuthenticationInfo.class),
                            anyObject(ProxyInfo.class));
                    expectLastCall().anyTimes();

                    wagon.get(anyString(), anyObject(File.class));
                    expectLastCall().anyTimes();

                    wagon.disconnect();
                    expectLastCall().anyTimes();
                },
                "This shouldn't happen!!");

        replay(wagon, wagonManager);
    }

    private List<Path> listDownloadRoots() throws Exception {
        Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"));
        List<Path> roots = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempRoot, "maven-shared-io-downloads-*")) {
            for (Path root : stream) {
                roots.add(root);
            }
        }

        return roots;
    }

    private void deleteRecursively(Path path) throws Exception {
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private Set<String> listDownloadTempFiles() throws Exception {
        Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"));
        Set<String> files = new HashSet<>();

        try (DirectoryStream<Path> roots = Files.newDirectoryStream(tempRoot, "maven-shared-io-downloads-*")) {
            for (Path root : roots) {
                try (Stream<Path> paths = Files.walk(root)) {
                    paths.filter(Files::isRegularFile).map(Path::toString).forEach(files::add);
                }
            }
        }

        return files;
    }

    private void setupDefaultMockConfiguration() {
        assertDoesNotThrow(
                () -> {
                    expect(wagonManager.getWagon("file")).andReturn(wagon);
                },
                "This shouldn't happen!!");

        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);

        expect(wagonManager.getProxy(anyString())).andReturn(null);

        try {
            wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        try {
            wagon.get(anyString(), anyObject(File.class));
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(() -> wagon.disconnect(), "This shouldn't happen!!");
    }

    private void setupMocksWithWagonManagerGetException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    expect(wagonManager.getWagon("file")).andThrow(error);
                },
                "This shouldn't happen!!");
    }

    private void setupMocksWithWagonConnectionException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    expect(wagonManager.getWagon("file")).andReturn(wagon);
                },
                "This shouldn't happen!!");

        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);

        expect(wagonManager.getProxy(anyString())).andReturn(null);

        try {
            wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
            expectLastCall().andThrow(error);
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }
    }

    private void setupMocksWithWagonGetException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    expect(wagonManager.getWagon("file")).andReturn(wagon);
                },
                "This shouldn't happen!!");

        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);

        expect(wagonManager.getProxy(anyString())).andReturn(null);

        try {
            wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        try {
            wagon.get(anyString(), anyObject(File.class));
            expectLastCall().andThrow(error);
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(() -> wagon.disconnect(), "This shouldn't happen!!");
    }

    private void setupMocksWithWagonDisconnectException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    expect(wagonManager.getWagon("file")).andReturn(wagon);
                },
                "This shouldn't happen!!");

        expect(wagonManager.getAuthenticationInfo(anyString())).andReturn(null);

        expect(wagonManager.getProxy(anyString())).andReturn(null);

        try {
            wagon.connect(anyObject(Repository.class), anyObject(AuthenticationInfo.class), anyObject(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        try {
            wagon.get(anyString(), anyObject(File.class));
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(
                () -> {
                    wagon.disconnect();
                    expectLastCall().andThrow(error);
                },
                "This shouldn't happen!!");
    }
}
