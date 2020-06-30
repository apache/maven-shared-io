package org.apache.maven.shared.io;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.maven.shared.utils.io.IOUtil;
import org.apache.maven.shared.utils.ReaderFactory;
import org.apache.maven.shared.utils.WriterFactory;

public final class Utils
{

    private Utils()
    {
    }

    private static void write( Writer writer, String content )
        throws IOException
    {
        try
        {
            writer.write( content );
        }
        finally
        {
            IOUtil.close( writer );
        }
    }

    public static void writePlatformFile( File file, String content )
        throws IOException
    {
        write( WriterFactory.newPlatformWriter( file ), content );
    }

    public static void writeFileWithEncoding( File file, String content, String encoding )
        throws IOException
    {
        write( WriterFactory.newWriter( file, encoding ), content );
    }

    public static void writeXmlFile( File file, String content )
        throws IOException
    {
        write( WriterFactory.newXmlWriter( file ), content );
    }

    /**
     * writes content to a file, using platform encoding.
     * @deprecated this API isn't explicit about encoding, use writePlatformFile() or writeXmlFile()
     * depending on your need
     */
    public static void writeToFile( File file, String testStr )
        throws IOException
    {
        writePlatformFile( file, testStr );
    }

    /**
     * Reads content from a file and normalizes EOLs to simple line feed (\\n), using platform encoding.
     * 
     * @deprecated this API isn't explicit about encoding nor EOL normalization, use readPlatformFile() or
     * readXmlFile() depending on your need, in conjunction with normalizeEndOfLine()
     */
    public static String readFile( File file ) throws IOException
    {
        return normalizeEndOfLine( readPlatformFile( file ) );
    }

    public static String readPlatformFile( File file ) throws IOException
    {
        StringWriter buffer = new StringWriter();

        try ( Reader reader = ReaderFactory.newPlatformReader( file ) ) {
            IOUtil.copy( reader, buffer );  
            return buffer.toString();
        }
    }

    public static String readXmlFile( File file ) throws IOException
    {
        StringWriter buffer = new StringWriter();

        try ( Reader reader = ReaderFactory.newXmlReader( file ) ) {
            IOUtil.copy( reader, buffer );  
            return buffer.toString();
        }
    }

    /**
     * normalize EOLs to simple line feed (\\n).
     */
    public static String normalizeEndOfLine( String content )
    {
        StringBuffer buffer = new StringBuffer();


        String line = null;

        try ( BufferedReader reader = new BufferedReader( new StringReader( content ) ) )
        {
            while( ( line = reader.readLine() ) != null )
            {
                if ( buffer.length() > 0 )
                {
                    buffer.append( '\n' );
                }

                buffer.append( line );
            }
        }
        catch ( IOException ioe )
        {
            // should not occur since everything happens in-memory
        }

        return buffer.toString();
    }

    public static String toString( Throwable error )
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter( sw );

        error.printStackTrace( pw );

        return sw.toString();
    }
}
