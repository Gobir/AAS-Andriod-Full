package com.face.recognition;

/**
 * Log.java
 *
 * Saves error logs to a file.
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class Log {

    private String logsFileName = "logcat.txt";

    //Saves logs in a file
    public void save(String msg, FileReadWrite file){
        file.writeToFile(logsFileName, msg + "\n");
    }

    //Converts printStackTrace object to string
    //Write a printable representation of this Throwable
    //The StringWriter gives the lock used to synchronize access to this writer.
    public String printStackTraceToString(Exception e){
        final Writer stringBuffSync = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringBuffSync);
        e.printStackTrace(printWriter);
        String stacktrace = stringBuffSync.toString();
        printWriter.close();
        return stacktrace.trim();
    }
}
