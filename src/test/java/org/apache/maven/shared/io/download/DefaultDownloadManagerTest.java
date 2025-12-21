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
import java.nio.file.Files;
import java.util.Collections;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
