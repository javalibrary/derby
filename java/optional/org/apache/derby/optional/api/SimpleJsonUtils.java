/*

   Derby - Class org.apache.derby.optional.api.SimpleJsonUtils

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.optional.api;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.apache.derby.iapi.util.StringUtil;

/**
 * <p>
 * Utility methods for simple JSON support.
 * </p>
 */
public abstract class SimpleJsonUtils
{
    /////////////////////////////////////////////////////////////////
    //
    //  CONSTANTS
    //
    /////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////
    //
    //  STATE
    //
    /////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////
    //
    //  PUBLIC BEHAVIOR
    //
    /////////////////////////////////////////////////////////////////

    /**
     * <p>
     * Pack a ResultSet into a JSONArray. This method could be called
     * client-side on any query result from any DBMS. Each row is
     * converted into a JSONObject whose keys are the corresponding
     * column names from the ResultSet.
     * Closes the ResultSet once it has been drained. Datatypes map
     * to JSON values as follows:
     * </p>
     *
     * <ul>
     * <li><i>NULL</i> - The JSON null literal.</li>
     * <li><i>SMALLINT, INT, BIGINT</i> - JSON integer values.</li>
     * <li><i>DOUBLE, FLOAT, REAL, DECIMAL, NUMERIC</i> - JSON floating point values.</li>
     * <li><i>CHAR, VARCHAR, LONG VARCHAR, CLOB</i> - JSON string values.</li>
     * <li><i>BLOB, VARCHAR FOR BIT DATA, LONG VARCHAR FOR BIT DATA</i> - The
     * byte array is turned into a hex string (2 hex digits per byte) and the
     * result is returned as a JSON string.</li>
     * <li><i>All other types</i> - Converted to JSON
     * string values via their toString() methods.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public  static  JSONArray   toJSON( ResultSet rs )
        throws SQLException
    {
        ResultSetMetaData   rsmd = rs.getMetaData();
        int                 columnCount = rsmd.getColumnCount();
        JSONArray           result = new JSONArray();

        try {
            while( rs.next() )
            {
                JSONObject  row = new JSONObject();

                for ( int i = 1; i <= columnCount; i++ )
                {
                    String  keyName = rsmd.getColumnName( i );
                    Object  value = getLegalJsonValue( rs.getObject( i ) );

                    row.put( keyName, value );
                }

                result.add( row );
            }
        }
        finally
        {
            if ( rs != null )
            {
                rs.close();
            }
        }

        return result;
    }

    /**
     * Construct a JSONArray from a Reader.
     */
    public static JSONArray readArray( Reader reader )
        throws SQLException
    {
        JSONParser  parser = new JSONParser();
        
        Object  obj = null;
        try {
            obj = parser.parse( reader );
        }
        catch( Throwable t) { throw wrap( t ); }

        if ( (obj == null) || !(obj instanceof JSONArray) )
        {
            throw new SQLException( "Document is not a JSON array." );
        }

        return (JSONArray) obj;
    }

    /**
     * <p>
     * SQL FUNCTION to convert a JSON document string into a JSONArray.
     * This function is registered by the simpleJson optional tool.
     * </p>
     */
    public  static  JSONArray   readArrayFromString( String document )
        throws SQLException
    {
        if ( document == null ) { document = ""; }

        return readArray( new StringReader( document ) );
    }

    /**
     * Read a JSONArray from an InputStream. Close the stream
     * after reading the JSONArray.
     */
    public static JSONArray readArrayFromStream
        ( InputStream inputStream, String characterSetName )
        throws SQLException
    {
        try {
            return readArray( new InputStreamReader( inputStream, characterSetName ) );
        }
        catch (UnsupportedEncodingException uee) { throw wrap( uee ); }
        finally
        {
            try {
                inputStream.close();
            }
            catch (IOException ioe) { throw wrap( ioe ); }
        }
    }

    /**
     * SQL FUNCTION to read a JSONArray from a File. This function
     * is registered by the simpleJson optional tool.
     */
    public static JSONArray readArrayFromFile
        ( String fileName, String characterSetName )
        throws SQLException
    {
        FileInputStream fis = null;
        final String name_of_file = fileName;
        
        try {
            fis = AccessController.doPrivileged(
             new PrivilegedExceptionAction<FileInputStream>()
             {
                 public FileInputStream run() throws IOException
                 {
                     return new FileInputStream( name_of_file );
                 }
             }
             );
        }
        catch (PrivilegedActionException pae) { throw wrap( pae ); }

        return readArrayFromStream( fis, characterSetName );
    }

    /**
     * SQL FUNCTION to read a JSONArray from an URL address.
     * This function is registered by the simpleJson optional tool.
     */
    public static JSONArray readArrayFromURL
        ( String urlString, String characterSetName )
        throws SQLException
    {
        InputStream inputStream = null;
        final   String  url_string = urlString;
        
        try {
            inputStream = AccessController.doPrivileged(
             new PrivilegedExceptionAction<InputStream>()
             {
                 public InputStream run() throws IOException, MalformedURLException
                 {
                     URL url = new URL( url_string );
                     return url.openStream();
                 }
             }
             );
        }
        catch (PrivilegedActionException pae) { throw wrap( pae ); }
        
        return readArrayFromStream( inputStream, characterSetName );
    }


    /////////////////////////////////////////////////////////////////
    //
    //  MINIONS
    //
    /////////////////////////////////////////////////////////////////

    /**
     * <p>
     * Turns an object into something which is a legal JSON value.
     * </p>
     */
    private static  Object  getLegalJsonValue( Object obj )
        throws SQLException
    {
        if (
            (obj == null) ||
            (obj instanceof Long) ||
            (obj instanceof Double) ||
            (obj instanceof Boolean) ||
            (obj instanceof String) ||
            (obj instanceof JSONObject) ||
            (obj instanceof JSONArray)
            )
        {
            return obj;
        }
        // other exact integers
        else if (
                 (obj instanceof Byte) ||
                 (obj instanceof Short) ||
                 (obj instanceof Integer)
                 )
        {
            return ((Number) obj).longValue();
        }
        // all other numbers, including BigDecimal
        else if (obj instanceof Number) { return ((Number) obj).doubleValue(); }
        else if (obj instanceof Clob)
        {
            Clob    clob = (Clob) obj;
            return clob.getSubString( 1, (int) clob.length() );
        }
        else if (obj instanceof Blob)
        {
            Blob    blob = (Blob) obj;
            return formatBytes( blob.getBytes( 1, (int) blob.length() ) );
        }
        if (obj instanceof byte[])
        {
            return formatBytes( (byte[]) obj );
        }
        // catch-all
        else { return obj.toString(); }
    }

    private static  String  formatBytes( byte[] bytes )
    {
        return StringUtil.toHexString( bytes, 0, bytes.length );
    }

    private static  Connection  getDerbyConnection() throws SQLException
    {
        return DriverManager.getConnection( "jdbc:default:connection" );
    }
    
    /**
     * <p>
     * Wrap an exception in a SQLException.
     * </p>
     */
    static SQLException wrap( Throwable t )
    {
        String  message = t.getMessage();
        if ( (message == null) || (message.length() == 0) )
        {
            message = t.toString();
        }
        
        return new SQLException( message, t );
    }
    
}
