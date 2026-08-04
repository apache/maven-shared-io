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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.maven.shared.io.logging.MessageHolder;

/**
 * file locator strategy.
 *
 * <p>
 * The location specification is normalized before use, so <code>..</code> and <code>.</code> elements do not remain in
 * the resolved file. If the location specification comes from an untrusted source, use
 * {@link #FileLocatorStrategy(File)} to give a base directory. The strategy then resolves relative specifications
 * against that base directory and refuses to resolve a file outside of it.
 * </p>
 */
public class FileLocatorStrategy implements LocatorStrategy {

    private final File baseDirectory;

    /**
     * Create a strategy that resolves any file, and resolves a relative specification against the current directory.
     */
    public FileLocatorStrategy() {
        this.baseDirectory = null;
    }

    /**
     * Create a strategy that only resolves files in the given base directory.
     *
     * @param baseDirectory the directory that contains the files to resolve; a relative specification is resolved
     *            against this directory, and a specification that points outside of it is refused.
     */
    public FileLocatorStrategy(File baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
    }

    /** {@inheritDoc} */
    public Location resolve(String locationSpecification, MessageHolder messageHolder) {
        Objects.requireNonNull(locationSpecification, "locationSpecification");

        File file;
        try {
            Path path = Paths.get(locationSpecification);

            if (baseDirectory != null) {
                path = baseDirectory.toPath().resolve(path);
            }

            file = path.normalize().toFile();
        } catch (InvalidPathException e) {
            messageHolder.addMessage("File: " + locationSpecification + " is not a valid path.");

            return null;
        }

        if (baseDirectory != null && !isInBaseDirectory(file)) {
            messageHolder.addMessage(
                    "File: " + file.getPath() + " is outside of the base directory: " + baseDirectory.getPath() + ".");

            return null;
        }

        Location location = null;

        if (file.exists()) {
            location = new FileLocation(file, locationSpecification);
        } else {
            messageHolder.addMessage("File: " + file.getAbsolutePath() + " does not exist.");
        }

        return location;
    }

    /**
     * Test if the given file is the base directory itself or a file in it. The test uses canonical paths, so a symbolic
     * link that points outside of the base directory is also refused.
     *
     * @param file the normalized file to test.
     * @return <code>true</code> if the file is in the base directory.
     */
    private boolean isInBaseDirectory(File file) {
        try {
            return file.getCanonicalFile()
                    .toPath()
                    .startsWith(baseDirectory.getCanonicalFile().toPath());
        } catch (IOException e) {
            // The canonical path is unavailable; compare the absolute paths instead.
            return file.getAbsoluteFile()
                    .toPath()
                    .normalize()
                    .startsWith(baseDirectory.getAbsoluteFile().toPath().normalize());
        }
    }
}
