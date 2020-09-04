package com.face.recognition;

/**
 * FileReadWrite.java
 *
 * Reads and writes into a file.
 */

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileReadWrite {

    private Context context;
    private Log log;

    public FileReadWrite(Context context){
        this.context = context;
        log = new Log();
    }

    public String initFile(String fileName) {
        try {
            new OutputStreamWriter(context.openFileOutput(fileName, context.MODE_PRIVATE));
            return "";
        }
        catch (FileNotFoundException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        }
    }

    public String writeToFile(String fileName, String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(fileName, context.MODE_APPEND));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            return "GPS position stored locally";
        }
        catch (FileNotFoundException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (UnsupportedEncodingException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        }
    }

    public String readFromFile(String fileName, String type) {
        try {
            FileInputStream fis = null;
            if(type.equals("internal")){
                File file = context.getFileStreamPath(fileName);
                if(!file.exists()){
                    initFile(fileName);
                }
                fis = context.openFileInput(fileName);
            }else if(type.equals("external")){
                fis = new FileInputStream(new File(context.getExternalFilesDir(null) + "/crashReports/", fileName));
            }
            InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            isr.close();
            return sb.toString();
        } catch (FileNotFoundException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (UnsupportedEncodingException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        }
    }

    public String saveDataInternal(String fileName, Boolean saveOnFileError, String params){
        //Saves data to a file locally when GPS coordinates cannot be sent online to the server.
        Map<String, String> keyVal = splitQuery(params);
        String response = "";
        if(!keyVal.containsKey("Error")){
            if(saveOnFileError){
                String email, latitude, longitude, time, date, timezone;
                email = keyVal.get("email");
                latitude = keyVal.get("latitude");
                longitude = keyVal.get("longitude");
                time = keyVal.get("time");
                date = keyVal.get("date");
                timezone = keyVal.get("timezone");
                if(email != null && latitude != null && longitude != null && time != null && date != null && timezone != null){
                    String line = email + "," + latitude + "," + longitude + "," + time + "," + date + "," + timezone + "\n";
                    response = writeToFile(fileName, line);
                }
            }
        }else{
            response = keyVal.get("Error");
        }
        return response;
    }

    private Map<String, String> splitQuery(String url) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        try{
            String[] pairs = url.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
            return query_pairs;
        }catch(UnsupportedEncodingException e){
            query_pairs.put("Error", e.getMessage());
            log.save(log.printStackTraceToString(e), this);
            return query_pairs;
        }
    }
}
