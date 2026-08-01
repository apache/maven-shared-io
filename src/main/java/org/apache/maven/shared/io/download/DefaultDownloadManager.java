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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.shared.io.logging.MessageHolder;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.repository.Repository;

/**
 * The Implementation of the {@link DownloadManager}
 *
 */
public class DefaultDownloadManager implements DownloadManager {

    /**
     * Role hint.
     */
    public static final String ROLE_HINT = "default";

    private WagonManager wagonManager;

    private Map<String, File> cache = new ConcurrentHashMap<>();

    /**
     * Shared parent of all download directories. One JVM shutdown hook deletes it.
     */
    private static File downloadRoot;

    /**
     * Whether the shutdown hook registration was already attempted. Keeps it to one hook.
     */
    private static boolean shutdownHookAttempted;

    /**
     * Number of shutdown hooks registered.
     */
    private static int registeredShutdownHooks;

    /**
     * @return how many JVM shutdown hooks this class registered.
     */
    static synchronized int registeredShutdownHooks() {
        return registeredShutdownHooks;
    }

    /**
     * This manager's own download directory, so {@link #cleanup()} only deletes its own files.
     */
    private File downloadDirectory;

    /**
     * Create an instance of the {@code DefaultDownloadManager}.
     */
    public DefaultDownloadManager() {}

    /**
     * @param wagonManager {@link org.apache.maven.repository.legacy.WagonManager}
     */
    public DefaultDownloadManager(WagonManager wagonManager) {
        this.wagonManager = wagonManager;
    }

    /**
     * Deletes the temporary files downloaded through this manager and empties its cache, so that
     * subsequent requests download again. Calling this is optional: the files are removed when the
     * JVM exits anyway. It is worth calling in a long-lived JVM, such as a Maven daemon or an
     * embedded build, once the downloaded files are no longer needed. Do not call it while a
     * download is in progress on another thread, as that download writes into the directory being
     * removed.
     */
    public void cleanup() {
        cache.clear();

        File directory;
        synchronized (this) {
            directory = downloadDirectory;
            downloadDirectory = null;
        }

        if (directory != null) {
            FileUtils.deleteQuietly(directory);
        }
    }

    /**
     * @return the directory of this manager, creating it, the shared root and the shutdown hook that
     *         removes the root on first use.
     * @throws IOException if the directory cannot be created.
     */
    private synchronized File downloadDirectory() throws IOException {
        if (downloadDirectory == null || !downloadDirectory.isDirectory()) {
            downloadDirectory = Files.createTempDirectory(downloadRoot().toPath(), "manager-")
                    .toFile();
        }

        return downloadDirectory;
    }

    private static synchronized File downloadRoot() throws IOException {
        // Recreate the root if something else deleted it, such as a temp dir sweeper.
        if (downloadRoot == null || !downloadRoot.isDirectory()) {
            downloadRoot =
                    Files.createTempDirectory("maven-shared-io-downloads-").toFile();
            registerShutdownHook();
        }

        return downloadRoot;
    }

    /**
     * Registers, at most once, the hook that removes {@link #downloadRoot} at JVM exit. Registering
     * one hook for the lifetime of the class, instead of one per root, is what keeps the JVM's hook
     * set from growing: a root that a temp dir sweeper removes is replaced without a second hook.
     */
    private static void registerShutdownHook() {
        if (shutdownHookAttempted) {
            return;
        }

        // Set before the attempt, so a failure is not retried on every recreation of the root.
        shutdownHookAttempted = true;

        preloadDeleteClasses();

        Thread hook = new Thread(DefaultDownloadManager::deleteDownloadRoot, "maven-shared-io-download-cleanup");

        // The hook lives until JVM exit, so give it as few references as possible. The inherited
        // context class loader is a plugin class realm in Maven and would be kept alive for the
        // whole run of a long-lived JVM.
        hook.setContextClassLoader(null);

        try {
            Runtime.getRuntime().addShutdownHook(hook);
            registeredShutdownHooks++;
        } catch (IllegalStateException e) {
            // Already shutting down, so no hook can be added. Leave the files to the OS temp cleanup.
        } catch (SecurityException e) {
            // Not allowed to register a hook. Downloading must still work, so fall back to the
            // operating system's temp directory cleanup, as above.
        }
    }

    /**
     * Deletes a throwaway directory tree so the classes {@link #deleteDownloadRoot()} needs are
     * loaded up front. At JVM exit the class loader may be closed, {@link FileUtils} would fail to
     * load and the hook would delete nothing.
     * Called while holding the class lock, so {@link #downloadRoot} is the root just created.
     */
    private static void preloadDeleteClasses() {
        File warmUp = new File(downloadRoot, ".warm-up");

        try {
            Files.createDirectories(warmUp.toPath().resolve("nested"));
            Files.createFile(warmUp.toPath().resolve("nested/file"));
        } catch (IOException e) {
            // Nothing to walk, so fewer classes load. The hook is no worse off.
        }

        FileUtils.deleteQuietly(warmUp);
    }

    /**
     * Deletes the current download root, ignoring failures. Called only by the shutdown hook.
     * The lock only reads {@link #downloadRoot}; the deletion itself need not be exclusive, because
     * a concurrent {@link #cleanup()} also uses {@link FileUtils#deleteQuietly(File)}.
     */
    private static void deleteDownloadRoot() {
        File root;
        synchronized (DefaultDownloadManager.class) {
            root = downloadRoot;
        }

        if (root != null) {
            FileUtils.deleteQuietly(root);
        }
    }

    /** {@inheritDoc} */
    public File download(String url, MessageHolder messageHolder) throws DownloadFailedException {
        return download(url, Collections.<TransferListener>emptyList(), messageHolder);
    }

    /** {@inheritDoc} */
    public File download(String url, List<TransferListener> transferListeners, MessageHolder messageHolder)
            throws DownloadFailedException {
        File downloaded = cache.get(url);

        if (downloaded != null && downloaded.exists()) {
            messageHolder.addMessage("Using cached download: " + downloaded.getAbsolutePath());

            return downloaded;
        }

        URL sourceUrl;
        try {
            sourceUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new DownloadFailedException(url, "Download failed due to invalid URL.", e);
        }

        Wagon wagon = null;

        // Retrieve the correct Wagon instance used to download the remote archive
        try {
            wagon = wagonManager.getWagon(sourceUrl.getProtocol());
        } catch (UnsupportedProtocolException e) {
            throw new DownloadFailedException(url, "Download failed", e);
        }

        messageHolder.addMessage("Using wagon: " + wagon + " to download: " + url);

        try {
            // create the landing file for the downloaded source archive, in the temp directory that
            // is removed as a whole at JVM exit, so no per-file exit hook is needed.
            downloaded = Files.createTempFile(downloadDirectory().toPath(), "download-", null)
                    .toFile();
        } catch (IOException e) {
            throw new DownloadFailedException(url, "Failed to create temporary file target for download.", e);
        }

        messageHolder.addMessage("Download target is: " + downloaded.getAbsolutePath());

        // split the download URL into base URL and remote path for connecting, then retrieving.
        String remotePath = sourceUrl.getPath();
        String authority = sourceUrl.getAuthority();

        // Repository derives host and port by re-parsing this URL, and tolerates a missing
        // authority only for file: URLs. Fail fast instead of letting its parser guess.
        if ((authority == null || authority.isEmpty()) && !"file".equalsIgnoreCase(sourceUrl.getProtocol())) {
            throw new DownloadFailedException(
                    url, "Download failed due to URL without an authority component (expected protocol://host/path).");
        }

        // Assemble from components instead of substring(url), which lets a query string or fragment leak in.
        // Authority is copied verbatim so port and bracketed IPv6 host survive.
        String baseUrl = sourceUrl.getProtocol() + ":" + (authority != null ? "//" + authority : "");

        for (Iterator<TransferListener> it = transferListeners.iterator(); it.hasNext(); ) {
            wagon.addTransferListener(it.next());
        }

        // connect to the remote site, and retrieve the archive. Note the separate methods in which
        // base URL and remote path are used.
        Repository repo = new Repository(sourceUrl.getHost(), baseUrl);

        messageHolder.addMessage("Connecting to: " + repo.getHost() + "(baseUrl: " + repo.getUrl() + ")");

        boolean retainTempFile = false;
        boolean connected = false;
        try {
            wagon.connect(
                    repo,
                    wagonManager.getAuthenticationInfo(repo.getId()),
                    wagonManager.getProxy(sourceUrl.getProtocol()));
            connected = true;

            messageHolder.addMessage("Getting: " + remotePath);

            wagon.get(remotePath, downloaded);

            // cache this for later download requests to the same instance...
            File cached = cache.putIfAbsent(url, downloaded);

            if (cached != null && cached.exists()) {
                // Another thread cached this URL first. Return its file, which callers may already
                // be using, and let the finally block delete this copy.
                return cached;
            }

            if (cached != null) {
                // The cached file is gone, so replace the entry with this one. Losing this race is
                // harmless: either file is valid and both are deleted with the temp directory.
                cache.replace(url, cached, downloaded);
            }

            retainTempFile = true;
            return downloaded;
        } catch (ConnectionException e) {
            throw new DownloadFailedException(url, "Download failed", e);
        } catch (AuthenticationException e) {
            throw new DownloadFailedException(url, "Download failed", e);
        } catch (TransferFailedException e) {
            throw new DownloadFailedException(url, "Download failed", e);
        } catch (ResourceDoesNotExistException e) {
            throw new DownloadFailedException(url, "Download failed", e);
        } catch (AuthorizationException e) {
            throw new DownloadFailedException(url, "Download failed", e);
        } finally {
            // Delete the temp file unless the cache now holds it. Covers a failed download and a
            // lost race to cache the same URL.
            if (!retainTempFile) {
                downloaded.delete();
            }

            if (wagon != null) {
                // Only disconnect if the connection was actually established.
                if (connected) {
                    try {
                        messageHolder.addMessage("Disconnecting.");

                        wagon.disconnect();
                    } catch (ConnectionException e) {
                        messageHolder.addMessage("Failed to disconnect wagon for: " + url, e);
                    }
                }

                // Listeners are added before connecting, so remove them even if connecting failed.
                // Otherwise they stay attached to a Wagon that may be reused.
                for (Iterator<TransferListener> it = transferListeners.iterator(); it.hasNext(); ) {
                    wagon.removeTransferListener(it.next());
                }
            }
        }
    }
}
