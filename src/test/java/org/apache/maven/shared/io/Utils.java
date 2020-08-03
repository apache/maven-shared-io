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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.maven.shared.utils.WriterFactory;

@Deprecated
public final class Utils
{

    private Utils()
    {
    }

    /**
     * deprecated use Apache Commons IO {@code FileUtils.writeStringToFile} instead
     */
    @Deprecated
    public static void writeFileWithEncoding( File file, String content, String encoding )
        throws IOException
    {
        try ( Writer writer = WriterFactory.newWriter( file, encoding ))
        {
            writer.write( content );
        }
    }

    /**
     * deprecated use Apache Commons Lang
     *     {@code org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(Throwable)} instead
     */
    @Deprecated
    public static String toString( Throwable error )
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter( sw );

        error.printStackTrace( pw );

        return sw.toString();
    }
}
