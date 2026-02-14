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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.expectLastCall;
import static org.mockito.Mockito.verify;
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
        wagonManager = mock(WagonManager.class);
        wagon = mock(Wagon.class);
    }

    @Test
    void shouldConstructWithNoParamsAndHaveNonNullMessageHolder() {
        new DefaultDownloadManager();
    }

    @Test
    void shouldConstructWithWagonManager() {

        new DefaultDownloadManager(wagonManager);

        verify(wagonManager);
    }

    @Test
    void shouldFailToDownloadMalformedURL() {

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

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        downloadManager.download(tempFile.toURI().toASCIIString(), new DefaultMessageHolder());

        verify(wagon, wagonManager);
    }

    @Test
    void shouldDownloadFromTempFileTwiceAndUseCache() throws Exception {
        File tempFile = Files.createTempFile("download-source", "test").toFile();
        tempFile.deleteOnExit();

        setupDefaultMockConfiguration();

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

        TransferListener transferListener = mock(TransferListener.class);

        wagon.addTransferListener(transferListener);

        wagon.removeTransferListener(transferListener);

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

        DownloadManager downloadManager = new DefaultDownloadManager(wagonManager);

        MessageHolder mh = new DefaultMessageHolder();

        downloadManager.download(tempFile.toURI().toASCIIString(), mh);

        assertTrue(mh.render().contains("ConnectionException"));

        verify(wagon, wagonManager);
    }

    private void setupDefaultMockConfiguration() {
        assertDoesNotThrow(
                () -> {
                    when(wagonManager.getWagon("file")).thenReturn(wagon);
                },
                "This shouldn't happen!!");

        when(wagonManager.getAuthenticationInfo(anyString())).thenReturn(null);

        when(wagonManager.getProxy(anyString())).thenReturn(null);

        try {
            wagon.connect(any(Repository.class), any(AuthenticationInfo.class), any(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        try {
            wagon.get(anyString(), any(File.class));
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(() -> wagon.disconnect(), "This shouldn't happen!!");
    }

    private void setupMocksWithWagonManagerGetException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    when(wagonManager.getWagon("file")).thenThrow(error);
                },
                "This shouldn't happen!!");
    }

    private void setupMocksWithWagonConnectionException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    when(wagonManager.getWagon("file")).thenReturn(wagon);
                },
                "This shouldn't happen!!");

        when(wagonManager.getAuthenticationInfo(anyString())).thenReturn(null);

        when(wagonManager.getProxy(anyString())).thenReturn(null);

        try {
            wagon.connect(any(Repository.class), any(AuthenticationInfo.class), any(ProxyInfo.class));
            expectLastCall().thenThrow(error);
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }
    }

    private void setupMocksWithWagonGetException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    when(wagonManager.getWagon("file")).thenReturn(wagon);
                },
                "This shouldn't happen!!");

        when(wagonManager.getAuthenticationInfo(anyString())).thenReturn(null);

        when(wagonManager.getProxy(anyString())).thenReturn(null);

        try {
            wagon.connect(any(Repository.class), any(AuthenticationInfo.class), any(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        try {
            wagon.get(anyString(), any(File.class));
            expectLastCall().thenThrow(error);
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(() -> wagon.disconnect(), "This shouldn't happen!!");
    }

    private void setupMocksWithWagonDisconnectException(Throwable error) {
        assertDoesNotThrow(
                () -> {
                    when(wagonManager.getWagon("file")).thenReturn(wagon);
                },
                "This shouldn't happen!!");

        when(wagonManager.getAuthenticationInfo(anyString())).thenReturn(null);

        when(wagonManager.getProxy(anyString())).thenReturn(null);

        try {
            wagon.connect(any(Repository.class), any(AuthenticationInfo.class), any(ProxyInfo.class));
        } catch (ConnectionException | AuthenticationException e) {
            fail("This shouldn't happen!!");
        }

        try {
            wagon.get(anyString(), any(File.class));
        } catch (TransferFailedException | AuthorizationException | ResourceDoesNotExistException e) {
            fail("This shouldn't happen!!");
        }

        assertDoesNotThrow(
                () -> {
                    wagon.disconnect();
                    expectLastCall().thenThrow(error);
                },
                "This shouldn't happen!!");
    }
}
