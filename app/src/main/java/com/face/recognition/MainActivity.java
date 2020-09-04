package com.face.recognition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.util.Patterns.EMAIL_ADDRESS;


public class MainActivity extends AppCompatActivity {
    private static boolean isRunning = false;
    private String latitude, longitude = null;
    public static String currentImagePath, currentImageName = null;
    private static final int IMAGE_REQUEST = 1;
    private BroadcastReceiver broadcastReceiver;
    private RelativeLayout checkIn, checkOut, camera;
    private ImageView imageView;
    private EditText emailEditText;
    private final ToastHandler toastHandler = new ToastHandler(MainActivity.this);
    private Log log;
    private FileReadWrite file;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        checkIn = findViewById(R.id.checkIn);
        camera = findViewById(R.id.camera);
        imageView = findViewById(R.id.imageView);
        checkOut = findViewById(R.id.exit);
        emailEditText = findViewById(R.id.editTextTextEmailAddress);
        emailEditText.clearFocus();
        file = new FileReadWrite(MainActivity.this);
        log = new Log();
        //For handling the application crash errors
        Thread.setDefaultUncaughtExceptionHandler(new CustomizedExceptionHandler(MainActivity.this));
        checkOut.setEnabled(false);
        checkOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doPost("Check Out");
            }
        });
        checkIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doPost("Check In");
            }
        });
        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureImage(view);
            }
        });
        if (!runtime_permissions()) {
            init();
        }
    }

    public void captureImage(View view){
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(cameraIntent.resolveActivity(getPackageManager()) != null){
            File imageFile = null;
            try{
                imageFile = getImageFile();
            }catch (IOException e){
                e.printStackTrace();
            }
            if(imageFile != null){
                Uri imageUri = FileProvider.getUriForFile(this, "com.facerecognition.android.fileprovider", imageFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(cameraIntent, IMAGE_REQUEST);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (broadcastReceiver == null) {
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    latitude = intent.getExtras().get("latitude").toString();
                    longitude = intent.getExtras().get("longitude").toString();
                    setLatitudeLongitude(latitude, longitude);
                }
            };
        }
        registerReceiver(broadcastReceiver, new IntentFilter("location_update"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    public void onBackPressed() {
        //Puts the application on the background.
        Toast.makeText(getApplicationContext(), "Application will continue running on background.", Toast.LENGTH_SHORT).show();
        this.moveTaskToBack(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                runtime_permissions();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == IMAGE_REQUEST){
            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
            Bitmap image = BitmapFactory.decodeFile(MainActivity.currentImagePath,bmOptions);
            rotateImage(image);
        }
    }

    private File getImageFile() throws IOException{
        String timeStamp = new SimpleDateFormat("yyyMMdd_HHmmss").format(new Date());
        String imageName = "jpg_"+timeStamp+"_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(imageName, ".jpg", storageDir);
        MainActivity.currentImagePath = imageFile.getAbsolutePath();
        MainActivity.currentImageName = imageFile.getName();
        return imageFile;
    }

    private boolean runtime_permissions() {
        //Requests permissions to use GPS if not granted
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 100);
            return true;
        } else {
            return false;
        }
    }

    private void init() {
        Intent i = new Intent(getApplicationContext(), GPS_Service.class);
        startService(i);
    }

    private void showAlert(String message){
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {}
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void setLatitudeLongitude(String latitude, String longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private String[] getLatitudeLongitude(){
        String[] latitudeLongitude = new String[2];
        latitudeLongitude[0] = this.latitude;
        latitudeLongitude[1] = this.longitude;
        return latitudeLongitude;
    }

    private boolean isEmailValid(CharSequence email) {
        return EMAIL_ADDRESS.matcher(email).matches();
    }

    private void doPost(final String action) {
        String email = emailEditText.getText().toString();
        if(!haveNetworkConnection(MainActivity.this)){
            showAlert("Please make sure internet access is available before you try to " + action + "!");
        }else if(MainActivity.currentImagePath == null){
            String out = " ";
            if(action.equals("Check Out")){
                out = " new ";
            }
            showAlert("Please make a" + out + "face capture before you try to " + action + "!");
        }else if(!isEmailValid(email)){
            showAlert("Please a valid email address before you try to " + action + "!");
        }else {
            Long imageSize = getImageSize(MainActivity.currentImagePath);
            if(imageSize == 0){
                showAlert("Please make a face capture before you try to " + action + "!");
            }else{
                final String[] latLng = getLatitudeLongitude();
                if (latLng[0] == null || latLng[1] == null) {
                    showAlert("Please try to move to make sure your position (Latitude & Longitude) is detected before you try to " + action + "!");
                }else{
                    HttpRequest requestHttp = new HttpRequest(MainActivity.this, latLng[0], latLng[1], checkIn, checkOut, action, toastHandler, email);
                    requestHttp.execute();
                }
            }
        }
    }

    private Long getImageSize(String imagePath){
        File file = new File(imagePath);
        long length = file.length();
        return length;
    }

    private void rotateImage(Bitmap bitmap) {
        int rotate = 0;
        ExifInterface exif;
        try{
            exif = new ExifInterface(MainActivity.currentImagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(rotate);
            imageView.setImageBitmap(Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true));
        }catch(IOException e){
            imageView.setImageBitmap(bitmap);
            log.save(log.printStackTraceToString(e), file);
        }
    }

    public static boolean getThreadStatus(){
        return MainActivity.isRunning;
    }

    public static void setThreadStatus(Boolean status){
        MainActivity.isRunning = status;
    }

    //Checks if connection is available WIFI or MOBILE
    public static boolean haveNetworkConnection(Activity activity) {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(activity.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        return isConnected;
    }

    //Handler to update the application main UI layout by showing a toast message
    public static class ToastHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public ToastHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                Toast.makeText(activity, msg.getData().getString("response"), Toast.LENGTH_LONG).show();
            }
        }
    }
}