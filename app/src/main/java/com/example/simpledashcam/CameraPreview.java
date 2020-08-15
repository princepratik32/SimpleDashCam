package com.example.simpledashcam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class CameraPreview extends AppCompatActivity {
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private HandlerThread camThread1;
    private Handler camHandler1;
    private HandlerThread camThread2;
    private Handler camHandler2;
    private CameraManager cameraManager;
    private List<Surface> surfaceList;
    private CaptureRequest captureRequest;
    private SurfaceHolder previewSurfaceHolder;
    private SurfaceView previewSurfaceView;
    private Intent startIntent;
    private String selectedCameraId;
    private boolean recording;
    protected Button recordButton;
    protected MediaRecorder mediaRecorder;

    private final static String LOG_TYPE = "SimpleDashCamLogs";
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startIntent = getIntent();
        surfaceList = new LinkedList<>();
        camThread1 = new HandlerThread("CameraThread1");
        camThread1.start();
        camHandler1 = new Handler(camThread1.getLooper());
        camThread2 = new HandlerThread("CameraThread2");
        camThread2.start();
        camHandler2 = new Handler(camThread2.getLooper());
        selectedCameraId = startIntent.getStringExtra(StartPage.EXTRA_SELECTED_CAMERA_ID);
        recording = false;
        setContentView(R.layout.activity_camera_preview);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    1); }

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Log.e(LOG_TYPE, ie.getMessage());
        }
        try {
            cameraManager.openCamera(selectedCameraId, stateCallback, camHandler1);
        } catch (Exception se) {
            Log.e(LOG_TYPE, se.getMessage());
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Log.e(LOG_TYPE, ie.getMessage());
        }
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoSize(640, 480);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO));

        camHandler1.post(new Runnable() {
            public void run() {
                previewSurfaceView = findViewById(R.id.camera_preview);
                previewSurfaceHolder = previewSurfaceView.getHolder();
                previewSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        Thread t = Thread.currentThread();
                        Log.d(LOG_TYPE, "Surface is created." + t.getName());
                        surfaceList.add(previewSurfaceHolder.getSurface());

                        mediaRecorder.setPreviewDisplay(previewSurfaceHolder.getSurface());
                        try {
                            mediaRecorder.prepare();
                        } catch (IOException ioe) {
                            Log.e(LOG_TYPE, ioe.getMessage());
                        }

                        surfaceList.add(mediaRecorder.getSurface());

                        try {
                            cameraDevice.createCaptureSession(surfaceList, camcapturesessionStatecallback, camHandler1);
                        } catch (Exception e) {
                            Log.e(LOG_TYPE, e.getMessage());
                        }
                    }

                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                        Log.d(LOG_TYPE, "Surface has changed.");
                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        Log.d(LOG_TYPE, "Surface is destroyed.");
                    }
                });
            }
        });

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Log.e(LOG_TYPE, ie.getMessage());
        }

        recordButton = findViewById(R.id.recordButton);
        recordButton.setText(R.string.start_recording);
        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (recording) {
                    mediaRecorder.stop();
                    recording = false;
                    recordButton.setText(R.string.start_recording);
                } else {
                    recordButton.setText(R.string.stop_recording);
                    recording = true;
                    CaptureRequest.Builder recordCaptureRequestBuilder = null;
                    try {
                        recordCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        recordCaptureRequestBuilder.addTarget(mediaRecorder.getSurface());
                        recordCaptureRequestBuilder.addTarget(previewSurfaceHolder.getSurface());
                    } catch (Exception e) {
                        Log.e(LOG_TYPE, e.getMessage());
                    }
                    final CaptureRequest recordCaptureRequest = recordCaptureRequestBuilder.build();
                    camHandler1.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                cameraCaptureSession.setRepeatingRequest(recordCaptureRequest, camcapturesessionCapturecallback, null);
                            } catch (Exception e) {
                                Log.e(LOG_TYPE, e.getMessage());
                            }
                        }
                    }, 100);

                    mediaRecorder.start();
                }
            }
        });
    }

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            Log.d(LOG_TYPE, "Camera is open");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            Log.d(LOG_TYPE, "Camera is Disconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(LOG_TYPE, "Opening camera failed, passing: " + error);
        }
    };

    protected CameraCaptureSession.StateCallback camcapturesessionStatecallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            cameraCaptureSession = session;
            Log.d(LOG_TYPE, "CameraCaptureSession created");
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            cameraCaptureSession = null;
            Log.d(LOG_TYPE, "CameraCaptureSession failed");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            Log.d(LOG_TYPE, "CameraCaptureSession closed");
        }
    };

    protected CameraCaptureSession.CaptureCallback camcapturesessionCapturecallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            Log.d(LOG_TYPE, "Capture started");
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        CaptureRequest.Builder captureRequestBuilder = null;
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurfaceHolder.getSurface());
            captureRequestBuilder.addTarget(mediaRecorder.getSurface());
        } catch (Exception e) {
            Log.e(LOG_TYPE, e.getMessage());
        }
        captureRequest = captureRequestBuilder.build();
        camHandler2.postDelayed(new Runnable() {
            public void run() {
                try {
                    cameraCaptureSession.setRepeatingRequest(captureRequest, camcapturesessionCapturecallback, null);
                } catch (Exception e) {
                    Log.e(LOG_TYPE, e.getMessage());
                }
            }
        }, 500);
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "SimpleDashcam");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()){
                Log.d(LOG_TYPE, "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }
}