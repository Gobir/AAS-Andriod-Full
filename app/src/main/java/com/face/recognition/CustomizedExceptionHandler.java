package com.face.recognition;

/**
 * CustomizedExceptionHandler.java
 *
 * Saves any crashes on a file.
 */

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;

public class CustomizedExceptionHandler implements UncaughtExceptionHandler {

    private UncaughtExceptionHandler defaultUEH;
    private Log log;
    private FileReadWrite file;
    private Context context;

    public CustomizedExceptionHandler(Context context) {
        //Getting the the default exception handler
        //that's executed when uncaught exception terminates a thread.
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        this.context = context;
        //Creates a file and log objects.
        file = new FileReadWrite(context);
        log = new Log();
    }

    public void uncaughtException(Thread t, Throwable e) {
        //Write a printable representation of this Throwable
        //The StringWriter gives the lock used to synchronize access to this writer.
        final Writer stringBuffSync = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringBuffSync);
        e.printStackTrace(printWriter);
        String stacktrace = stringBuffSync.toString();
        printWriter.close();
        writeToFile(stacktrace);
        defaultUEH.uncaughtException(t, e);
    }

    private void writeToFile(String currentStacktrace) {
        try {
            //Saves logs for crashes into the "crashReports" folder.
            String folderName = context.getExternalFilesDir(null) + "/crashReports/";
            File dir = new File(folderName);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            //Adds a unique date / time to the created logs file.
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date();
            String filename = dateFormat.format(date) + "_crash.txt";
            File reportFile = new File(dir, filename);
            FileWriter fileWriter = new FileWriter(reportFile);
            fileWriter.append(currentStacktrace);
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) {
            //Saves any errors to a log file.
            log.save(log.printStackTraceToString(e), file);
        }
    }

}