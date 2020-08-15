package com.example.simpledashcam;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;

public class StartPage extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    protected String selectedCamera;
    protected CameraManager cameraManager;
    protected CameraCharacteristics cameraCharacteristics;
    private String[] cameraIdList;
    public final String LOG_TYPE = "SimpleDashCamLog";
    static final String EXTRA_SELECTED_CAMERA_ID = "com.example.previewCameraId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_page);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    1);
        }
    }

    public void setCamera(String cameraId) {
        selectedCamera = cameraId;
        try {
            TextView cameraFacingValue = (TextView)findViewById(R.id.camera_face_value);
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
            TextView sensorHeight = (TextView)findViewById(R.id.sensor_height_value);
            TextView sensorWidth = (TextView)findViewById(R.id.sensor_width_value);

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
        Spinner spinner = (Spinner) findViewById(R.id.camera_list);
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
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setCamera(cameraIdList[(int)id]);
        Log.d(LOG_TYPE, "onItemSelected called for id: " + id);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}