package org.apache.maven.shared.io.location;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;

public class ArtifactLocationTest
    extends TestCase
{

    public void testShouldConstructFromTempFileSpecification()
        throws IOException
    {
        File f = Files.createTempFile( "artifact-location.", ".test" ).toFile();
        f.deleteOnExit();

        Artifact a = new DefaultArtifact( "group", "artifact", VersionRange.createFromVersion( "1" ), null, "jar",
                                          null, new DefaultArtifactHandler() );

        a.setFile( f );

        ArtifactLocation location = new ArtifactLocation( a, f.getAbsolutePath() );

        assertSame( f, location.getFile() );
    }

    public void testShouldRead()
        throws IOException
    {
        File f = Files.createTempFile( "url-location.", ".test" ).toFile();
        f.deleteOnExit();

        String testStr = "This is a test";

        FileUtils.writeStringToFile( f, testStr, "US-ASCII" );

        Artifact a = new DefaultArtifact( "group", "artifact", VersionRange.createFromVersion( "1" ), null, "jar",
                                          null, new DefaultArtifactHandler() );

        a.setFile( f );

        ArtifactLocation location = new ArtifactLocation( a, f.getAbsolutePath() );

        location.open();

        byte[] buffer = new byte[testStr.length()];

        int read = location.read( buffer );

        assertEquals( testStr.length(), read );

        assertEquals( testStr, new String( buffer, "US-ASCII" ) );
    }

}
