package com.example.simpledashcam;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetTemporaryToken;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.http.javanet.NetHttpTransport;

import java.util.ArrayList;

import static com.example.simpledashcam.R.layout.activity_start_page;

public class StartPage extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    protected String selectedCamera;
    protected CameraManager cameraManager;
    protected CameraCharacteristics cameraCharacteristics;
    protected boolean userIsLoggedIn;
    protected SharedPreferences sharedPreferences;
    protected HandlerThread threadB;
    protected Handler threadBHandler;
    protected TextView loginInfo;
    private String[] cameraIdList;
    protected Button flickrLoginButton;
    static final String LOG_TYPE = "SimpleDashCamLog";
    static final String LOG_TYPE_FLICKR = "Flickr";
    static final String EXTRA_SELECTED_CAMERA_ID = "com.example.previewCameraId";

    static final String FLICKR_API_KEY = "";
    static final String FLICKR_API_SECRET= "";
    static final String FLICKR_REQUEST_TOKEN_URL = "https://www.flickr.com/services/oauth/request_token";
    static final String FLICKR_REST_ENDPOINT = "https://www.flickr.com/services/rest/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(activity_start_page);
        loginInfo = findViewById(R.id.flickr_login_info);
        loginInfo.setText(R.string.flickr_login_loading);
        threadB = new HandlerThread("sideB");
        threadB.start();
        threadBHandler = new Handler(threadB.getLooper());
        sharedPreferences = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    1);
        }

        userIsLoggedIn = false;

        flickrLoginButton = findViewById(R.id.flickr_login);
        flickrLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (userIsLoggedIn) {
                    logoutOfFlickr();
                } else {
                    launchAuthFlow();
                }
            }
        });
    }

    public void setCamera(String cameraId) {
        selectedCamera = cameraId;
        try {
            TextView cameraFacingValue = findViewById(R.id.camera_face_value);
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            switch (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)) {
                case CameraMetadata.LENS_FACING_BACK:
                    cameraFacingValue.setText(R.string.camera_face_back);
                    break;
                case CameraMetadata.LENS_FACING_FRONT:
                    cameraFacingValue.setText(R.string.camera_face_front);
                    break;
            }

            Rect sensorInfo = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            TextView sensorHeight = findViewById(R.id.sensor_height_value);
            TextView sensorWidth = findViewById(R.id.sensor_width_value);

            sensorHeight.setText(String.valueOf(sensorInfo.height()));
            sensorWidth.setText(String.valueOf(sensorInfo.width()));
        } catch (CameraAccessException cae) {
            Log.e(LOG_TYPE, "CameraAccessException in setCamera.");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        cameraManager = (CameraManager)getSystemService(CAMERA_SERVICE);
        Spinner spinner = findViewById(R.id.camera_list);
        ArrayList<String> cameraList = new ArrayList<>();

        try {
            cameraIdList = cameraManager.getCameraIdList();
            for (String cameraId : cameraIdList) {
                cameraList.add("Camera " + cameraId);
            }
        } catch (CameraAccessException cae) {
            Log.d(LOG_TYPE, "Failed get list of cameras: " + cae.getMessage());
            return;
        }

        ArrayAdapter<String> cameraListAdapter = new ArrayAdapter<String>(this,
                R.layout.camera_name,
                cameraList);
        cameraListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setOnItemSelectedListener(this);
        spinner.setAdapter(cameraListAdapter);

        Button previewButton = (Button)findViewById(R.id.proceed_button);
        previewButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent cameraPreviewActivityIntent = new Intent();
                cameraPreviewActivityIntent.setComponent(new ComponentName(
                        "com.example.simpledashcam",
                        "com.example.simpledashcam.CameraPreview"));
                cameraPreviewActivityIntent.putExtra(EXTRA_SELECTED_CAMERA_ID, selectedCamera);
                startActivity(cameraPreviewActivityIntent);
            }
        });

        flickrLoginButton.setVisibility(View.INVISIBLE);
        loginInfo.setText(R.string.flickr_login_loading);
        loadFlickrLoginInfo();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setCamera(cameraIdList[(int)id]);
        Log.d(LOG_TYPE, "onItemSelected called for id: " + id);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    protected void launchAuthFlow() {
        final OAuthGetTemporaryToken oAuthGetTemporaryToken = new OAuthGetTemporaryToken(FLICKR_REQUEST_TOKEN_URL);
        oAuthGetTemporaryToken.consumerKey = FLICKR_API_KEY;
        oAuthGetTemporaryToken.transport = new NetHttpTransport();
        oAuthGetTemporaryToken.callback = "simpledashcam://flickrredir/";
        OAuthHmacSigner hmacSigner = new OAuthHmacSigner();
        hmacSigner.clientSharedSecret = FLICKR_API_SECRET;
        oAuthGetTemporaryToken.signer = hmacSigner;

        threadBHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    OAuthCredentialsResponse response = oAuthGetTemporaryToken.execute();
                    Log.d(LOG_TYPE_FLICKR, "Flickr temp token: " + response.token);
                    Log.d(LOG_TYPE_FLICKR, "Flickr temp token secret: " + response.tokenSecret);

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(getString(R.string.preference_temp_token_secret), response.tokenSecret);
                    editor.apply();

                    String url = "https://www.flickr.com/services/oauth/authorize?oauth_token="
                            + response.token;
                    CustomTabsIntent.Builder tabsIntentBuilder = new CustomTabsIntent.Builder();
                    CustomTabsIntent customTabsIntent = tabsIntentBuilder.build();
                    customTabsIntent.launchUrl(getApplicationContext(), Uri.parse(url));
                } catch (Exception e) {
                    Log.d(LOG_TYPE_FLICKR, "Failed get Flickr temp token: " + e.toString());
                }
            }
        });
    }

    protected void loadFlickrLoginInfo() {
        threadBHandler.post(new Runnable() {
            @Override
            public void run() {
                String url = FlickrLoginProvider.URL;
                Uri flickrLogin = Uri.parse(url);
                Cursor c = getContentResolver().query(
                        flickrLogin,
                        null,
                        null,
                        null
                );

                if (c.getCount() > 0) {
                    if (c.moveToFirst()) {
                        String username = c.getString(c.getColumnIndex(FlickrLoginProvider.USERNAME));
                        String nsid = c.getString(c.getColumnIndex(FlickrLoginProvider.NSID));
                        username = (username.isEmpty()) ? nsid : username;
                        loginInfo.setText(getText(R.string.you_are_logged_in_as) + " " + username);
                    }

                    flickrLoginButton.setText(R.string.logout_of_flickr);
                    flickrLoginButton.setVisibility(View.VISIBLE);
                    userIsLoggedIn = true;
                } else {
                    flickrLoginButton.setText(R.string.login_to_flickr);
                    flickrLoginButton.setVisibility(View.VISIBLE);
                    loginInfo.setText(null);
                    userIsLoggedIn = false;
                }
            }
        });
    }

    protected void logoutOfFlickr() {
        String url = FlickrLoginProvider.URL;
        Uri flickrLogin = Uri.parse(url);
        int count = getContentResolver().delete(flickrLogin, null, null);
        flickrLoginButton.setText(R.string.login_to_flickr);
        flickrLoginButton.setVisibility(View.VISIBLE);
        loginInfo.setText(null);
        userIsLoggedIn = false;
        Log.d(LOG_TYPE_FLICKR, "Logged out of Flickr: " + count);
    }
}