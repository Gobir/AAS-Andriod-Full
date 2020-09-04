package com.face.recognition;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.widget.RelativeLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpRequest extends AsyncTask<Void,Void,String> {
    private ProgressDialog progressDialog;
    private Activity activity;
    private AlertDialog.Builder alertDialogBuilder;
    private AlertDialog alertDialog;
    private final String lat, lng;
    private final String action;
    private RelativeLayout checkIn, checkOut;
    private static String mIntervalMinutes;
    private Runnable mRunnable;
    private static Thread mThread;
    private static Boolean isRunning;
    private MainActivity.ToastHandler toastHandler;
    private boolean kill = false;
    private FileReadWrite file;
    private String trackingFileName = "tracking.csv";
    private String logsFileName = "logcat.txt";
    private Log log;
    private String baseUrl = "https://vmi338910.contaboserver.net/~devouss/face_recognition/api.php";
    private String email;

    public HttpRequest (final Activity activity, final String lat, final String lng, RelativeLayout checkIn, RelativeLayout checkOut, final String action, MainActivity.ToastHandler toastHandler, String email) {
        this.activity = activity;
        this.action = action;
        this.lat = lat;
        this.lng = lng;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.email = email;
        isRunning = MainActivity.getThreadStatus();
        this.toastHandler = toastHandler;
        file = new FileReadWrite(activity);
        log = new Log();
        alertDialogBuilder  = new AlertDialog.Builder(activity);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        if(action.equals("Check Out") && kill){
                            activity.finish();
                            System.exit(0);
                        }
                    }
                });
        alertDialog = alertDialogBuilder.create();
        mRunnable = new Runnable() {
            public void run() {
                while (isRunning) {
                    try {
                        if (lat != null && lng != null) {
                            //Send Lat Long to Server
                            sendPosition(lat, lng);
                        }
                        Thread.sleep((long) ((Integer.parseInt(mIntervalMinutes) * 60) * 1000));
                    } catch (Exception e) {
                        log.save(log.printStackTraceToString(e), file);
                    }
                }
            }
        };
    }

    public static void stopThread(){
        isRunning = false;
        if(mThread != null){
            if(mThread.isAlive()){
                mThread.interrupt();
            }
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Processing");
        progressDialog.setMessage("Sending your face capture for "+ action + ".\n\nThis process may take few minutes, please wait...");
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        if(!activity.isFinishing()){
            progressDialog.show();
        }
    }

    @Override
    protected String doInBackground(Void... voids) {
        String okHttpResponseReturned;
        //Checking position if Within premises.
        OkHttpClient clientPosition = createClient();
        RequestBody formBodyPosition = new FormBody.Builder()
                .add("latitude", this.lat)
                .add("longitude", this.lng)
                .add("email", email)
                .add("method", "checkPosition")
                .build();
        Request requestPosition = createRequest(baseUrl, formBodyPosition);
        try {
            Response responsePosition = clientPosition.newCall(requestPosition).execute();
            okHttpResponseReturned = responsePosition.body().string();
            JSONObject jsonObject = new JSONObject(okHttpResponseReturned);
            if(jsonObject.has("premisesWithin") && jsonObject.has("result") && jsonObject.has("timeInterval")){
                String premisesWithin, result = null;
                try {
                    premisesWithin = jsonObject.get("premisesWithin").toString();
                    result = jsonObject.get("result").toString();
                    if(result.equals("pass")){
                        mIntervalMinutes = jsonObject.get("timeInterval").toString();
                        //Position checked and valid.
                        //Uploading face capture to server.
                        File imageFile = new File(MainActivity.currentImagePath);
                        OkHttpClient clientUpload = createClient();
                        RequestBody formBodyUpload = new MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("method", "upload")
                                .addFormDataPart("file", imageFile.getName(), RequestBody.create(MediaType.parse("image/jpg"), imageFile))
                                .build();
                        Request requestUpload = createRequest(baseUrl, formBodyUpload);
                        try {
                            Response responseUpload = clientUpload.newCall(requestUpload).execute();
                            okHttpResponseReturned = responseUpload.body().string();
                            if(responseUpload.code() == 200){
                                //Face capture successfully uploaded.
                                //Send image name to do authentication.
                                OkHttpClient clientAuth = createClient();
                                RequestBody formBodyAuth = new FormBody.Builder()
                                        .add("image_name", MainActivity.currentImageName)
                                        .add("email", email)
                                        .add("method", "doFaceRecognition")
                                        .build();
                                Request requestAuth = createRequest(baseUrl, formBodyAuth);
                                try {
                                    Response responseAuth = clientAuth.newCall(requestAuth).execute();
                                    okHttpResponseReturned = responseAuth.body().string();
                                    //Upload exceptions logs if available
                                    uploadExceptionsLogs(logsFileName);
                                    //Upload crashes report if available;
                                    uploadCrashReportsData();
                                } catch (IOException e) {
                                    okHttpResponseReturned = e.getMessage();
                                    log.save(log.printStackTraceToString(e), file);
                                }
                            }
                        } catch (IOException e) {
                            okHttpResponseReturned = e.getMessage();
                            log.save(log.printStackTraceToString(e), file);
                        }
                    }else{
                        okHttpResponseReturned = action + " Failed!\n\nYou are away from the company by "+premisesWithin+" meters.";
                    }
                } catch (JSONException e) {
                    okHttpResponseReturned = e.getMessage();
                    log.save(log.printStackTraceToString(e), file);
                }
            }
        } catch (IOException e) {
            okHttpResponseReturned = e.getMessage();
            log.save(log.printStackTraceToString(e), file);
        } catch (JSONException e) {
            okHttpResponseReturned = e.getMessage();
            log.save(log.printStackTraceToString(e), file);
        }
        return okHttpResponseReturned;
    }

    @Override
    protected void onPostExecute(String response) {
        super.onPostExecute(response);
        String message = null;
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if(!response.contains("Failed!") && !response.contains("Error!")
                && !response.contains("Traceback (most recent call last):")
                && !response.isEmpty() && response != null){
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(response);
                if(jsonObject.has("faces") && jsonObject.has("match")){
                    String match = null;
                    try {
                        match = jsonObject.get("match").toString();
                        if(match.equals("true")){
                            message = action + " Succeeded!";
                            MainActivity.currentImagePath = null;
                            MainActivity.currentImageName = null;
                            checkIn.setEnabled(false);
                            checkOut.setEnabled(true);
                            if(action.equals("Check In")){
                                MainActivity.setThreadStatus(true);
                                isRunning = true;
                                mThread = new Thread(mRunnable);
                                mThread.start();
                            }else if(action.equals("Check Out")){
                                stopThread();
                                Intent i = new Intent(activity, GPS_Service.class);
                                activity.stopService(i);
                                MainActivity.setThreadStatus(false);
                                isRunning = false;
                                kill = true;
                            }
                        }else{
                            message = action + " Failed!\n\nPlease try to take a new capture.";
                        }
                    } catch (JSONException e) {
                        message = e.getMessage();
                        log.save(log.printStackTraceToString(e), file);
                    }
                }else{
                    message = action + " Failed! Invalid JSON server response.\n\n" + response;
                }
            } catch (JSONException e) {
                message = e.getMessage();
                log.save(log.printStackTraceToString(e), file);
            }
        }else{
            message = response;
        }
        alertDialog.setMessage(message);
        if(!activity.isFinishing()){
            alertDialog.show();
        }
    }

    private OkHttpClient createClient(){
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
        return client;
    }

    private Request createRequest(String Url, RequestBody formBodyPost){
        Request requestPost = new Request.Builder()
                .url(Url)
                .post(formBodyPost)
                .build();
        return requestPost;
    }

    private void sendPosition(final String lat, final String lng){
        new Thread() {
            public void run() {
                //Check if there is GPS data saved offline due to Network issues.
                String dataRead = file.readFromFile(trackingFileName, "internal");
                if (dataRead.contains("Error:")) {
                    sendToastMessage("GPS position stored locally could not be read! We will try next time.");
                } else {
                    if (dataRead.length() != 0) {
                        //File has content to upload to the server, do the upload and empty the file.
                        if(!MainActivity.haveNetworkConnection(activity)){
                            try {
                                String param = "method=" + URLEncoder.encode("savePositionOffline", "UTF-8") +
                                        "&data=" + URLEncoder.encode(dataRead, "UTF-8");
                                String msg = file.saveDataInternal(trackingFileName, true, param);
                                sendToastMessage(msg);
                            }catch (UnsupportedEncodingException e) {
                                sendToastMessage(e.getMessage());
                                log.save(log.printStackTraceToString(e), file);
                            }
                        }else{
                            OkHttpClient clientPositionOffline = createClient();
                            RequestBody formBodyPositionOffline = new FormBody.Builder()
                                    .add("dataRead", dataRead)
                                    .add("method", "savePositionOffline")
                                    .build();
                            Request requestPositionOffline = createRequest(baseUrl, formBodyPositionOffline);
                            try {
                                Response responsePositionOffline = clientPositionOffline.newCall(requestPositionOffline).execute();
                                String responseOfflineBody = responsePositionOffline.body().string();
                                if (!responseOfflineBody.contains("Error:")) {
                                    //Offline data saved, empty the file
                                    sendToastMessage("GPS position stored locally successfully uploaded to the server.");
                                    String initStatus = file.initFile(trackingFileName);
                                    if (initStatus.contains("Error:")) {
                                        sendToastMessage(initStatus);
                                    }
                                } else {
                                    sendToastMessage(responseOfflineBody);
                                    log.save(responseOfflineBody, file);
                                }
                            }catch (IOException e){
                                sendToastMessage(e.getMessage());
                                log.save(log.printStackTraceToString(e), file);
                            }
                        }
                    }
                }
                //Do normal GPS data saving
                //Check in Internet connection is available, otherwise save locally.
                if(!MainActivity.haveNetworkConnection(activity)){
                    try {
                        String param = "method=" + URLEncoder.encode("savePosition", "UTF-8") +
                                "&email=" + URLEncoder.encode(email, "UTF-8") +
                                "&latitude=" + URLEncoder.encode(lat, "UTF-8") +
                                "&longitude=" + URLEncoder.encode(lng, "UTF-8") +
                                "&time=" + URLEncoder.encode(getDateTime("HH:mm"), "UTF-8") +
                                "&date=" + URLEncoder.encode(getDateTime("MM/dd/yyyy"), "UTF-8") +
                                "&timezone=" + URLEncoder.encode(getTimeZone(), "UTF-8");
                        String msg = file.saveDataInternal(trackingFileName, true, param);
                        sendToastMessage("No internet access found!" + "\n\n" + msg);
                    }catch (UnsupportedEncodingException e) {
                        sendToastMessage(e.getMessage());
                        log.save(log.printStackTraceToString(e), file);
                    }
                }else{
                    OkHttpClient clientPosition = createClient();
                    RequestBody formBodyPosition = new FormBody.Builder()
                            .add("latitude", lat)
                            .add("longitude", lng)
                            .add("email", email)
                            .add("time", getDateTime("HH:mm"))
                            .add("date", getDateTime("MM/dd/yyyy"))
                            .add("timezone", getTimeZone())
                            .add("method", "savePosition")
                            .build();
                    Request requestPosition = createRequest(baseUrl, formBodyPosition);
                    try {
                        Response responsePosition = clientPosition.newCall(requestPosition).execute();
                        JSONObject jsonObject = null;
                        String responseMsg = responsePosition.body().string();
                        if(!responseMsg.contains("Error") && !responseMsg.isEmpty() && responseMsg != null){
                            try {
                                jsonObject = new JSONObject(responseMsg);
                                if (jsonObject.has("timeInterval") && jsonObject.has("message")) {
                                    String timeInterval = jsonObject.get("timeInterval").toString();
                                    String msg = jsonObject.get("message").toString();
                                    if(!mIntervalMinutes.equals(timeInterval)){
                                        //Update Thread
                                        stopThread();
                                        MainActivity.setThreadStatus(true);
                                        isRunning = true;
                                        mThread = new Thread(mRunnable);
                                        mThread.start();
                                        msg = "GPS position saving changed from " + mIntervalMinutes + " to " + timeInterval + " by Administrator\n\n\"GPS position will be saved every" + timeInterval + " minutes";
                                        mIntervalMinutes = timeInterval;
                                    }
                                    sendToastMessage(msg);
                                }else{
                                    sendToastMessage(responseMsg);
                                }
                            } catch (JSONException e) {
                                sendToastMessage(e.getMessage());
                                log.save(log.printStackTraceToString(e), file);
                            }
                        }else{
                            sendToastMessage(responseMsg);
                        }
                    }catch(IOException e){
                        sendToastMessage(e.getMessage());
                        log.save(log.printStackTraceToString(e), file);
                    }
                }
            }
        }.start();
    }

    private void sendToastMessage(String response){
        Message oMsg = toastHandler.obtainMessage();
        Bundle oBundle = new Bundle();
        oMsg.setData(oBundle);
        oBundle.putString("response", response);
        toastHandler.sendMessage(oMsg);
    }

    public static String getTimeZone() {
        Calendar mCalendar = new GregorianCalendar();
        TimeZone mTimeZone = mCalendar.getTimeZone();
        int mUTCOffset = mTimeZone.getRawOffset();
        return "UTC+" + TimeUnit.HOURS.convert(mUTCOffset, TimeUnit.MILLISECONDS);
    }

    public static String getDateTime(String pattern){
        String dateTime = new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
        return dateTime;
    }

    private void echo (String msg){
        System.out.println(msg);
    }

    private void uploadExceptionsLogs(String logsFileName) {
        String LogRead = file.readFromFile(logsFileName, "internal");
        if (LogRead.length() != 0) {
            if(MainActivity.haveNetworkConnection(activity)){
                //Log file has content to upload to the server, do the upload.
                OkHttpClient clientLogs = createClient();
                RequestBody formBodyLogs = new FormBody.Builder()
                    .add("data", LogRead)
                    .add("email", email)
                    .add("time", getDateTime("HH:mm"))
                    .add("date", getDateTime("MM/dd/yyyy"))
                    .add("timezone", getTimeZone())
                    .add("errorType", "Exception")
                    .add("method", "errors")
                    .build();
                Request requestLogs = createRequest(baseUrl, formBodyLogs);
                try {
                    Response responseLogs = clientLogs.newCall(requestLogs).execute();
                } catch (IOException e) {
                    log.save(log.printStackTraceToString(e), file);
                }
            }
        }
    }

    private void uploadCrashReportsData(){
        String folderName = activity.getExternalFilesDir(null) + "/crashReports/";
        File dir = new File(folderName);
        if(dir.exists() && dir.isDirectory()) {
            File[] listFiles = dir.listFiles();
            String date = getDateTime("yyyy-MM-dd");
            StringBuilder sb = new StringBuilder();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < listFiles.length; i++) {
                if (listFiles[i].isFile()) {
                    String fileName = listFiles[i].getName();
                    if (fileName.startsWith(date) && fileName.endsWith("_crash.txt")) {
                        sb.append(file.readFromFile(fileName, "external")).append("\n");
                        list.add(fileName);
                    }
                }
            }
            if (sb.length() != 0) {
                //Upload to the server the crash reports.
                if(MainActivity.haveNetworkConnection(activity)){
                    OkHttpClient clientCrash = createClient();
                    RequestBody formBodyCrash = new FormBody.Builder()
                            .add("data", sb.toString())
                            .add("email", email)
                            .add("time", getDateTime("HH:mm"))
                            .add("date", getDateTime("MM/dd/yyyy"))
                            .add("timezone", getTimeZone())
                            .add("errorType", "Crash")
                            .add("method", "errors")
                            .build();
                    Request requestCrash = createRequest(baseUrl, formBodyCrash);
                    try {
                        Response responseCrash = clientCrash.newCall(requestCrash).execute();
                        String okHttpResponseReturned = responseCrash.body().string();
                        if (!okHttpResponseReturned.contains("Error:")) {///ADD CORRESPONDING RESPONSE IN PHP and all the logs and crash methods created somewhere
                            for(int i = 0, size = list.size(); i < size; i++) {
                                File file = new File(folderName + list.get(i));
                                file.delete();
                            }
                        }else{
                            log.save(okHttpResponseReturned, file);
                        }
                    } catch (IOException e) {
                        log.save(log.printStackTraceToString(e), file);
                    }
                }
            }
        }
    }

}
