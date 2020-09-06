package com.example.simpledashcam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.MediaCodec;
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
    private HandlerThread camThread2;
    private HandlerThread camThread3;
    private HandlerThread previewThread;
    private HandlerThread callbackThread;
    private Handler camHandler1;
    private Handler camHandler2;
    private Handler camHandler3;
    private Handler previewHandler;
    private Handler callbackHandler;
    private CameraManager cameraManager;
    private List<Surface> surfaceList;
    private CaptureRequest captureRequest;
    private SurfaceHolder previewSurfaceHolder;
    private SurfaceView previewSurfaceView;
    private Intent startIntent;
    private String selectedCameraId;
    private boolean recording;
    protected Button recordButton;
    protected MediaRecorder mediaRecorder1;
    protected MediaRecorder mediaRecorder2;
    protected Surface recoderSurface1;
    protected Surface recoderSurface2;
    protected boolean mr1done;
    protected boolean mr2done;

    private final static String LOG_TYPE = "SimpleDashCamLogs";
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MAX_VIDEO_DURATION = 60000;
    public static final int VIDEO_SWITCH_GAP = 10000;

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

        camThread3 = new HandlerThread("CameraThread3");
        camThread3.start();
        camHandler3 = new Handler(camThread3.getLooper());

        previewThread = new HandlerThread("previewThread ");
        previewThread.start();
        previewHandler = new Handler(previewThread.getLooper());

        callbackThread = new HandlerThread("callbackThread ");
        callbackThread.start();
        callbackHandler = new Handler(callbackThread.getLooper());

        selectedCameraId = startIntent.getStringExtra(StartPage.EXTRA_SELECTED_CAMERA_ID);
        recording = false;
        setContentView(R.layout.activity_camera_preview);
        mediaRecorder1 = new MediaRecorder();
        recoderSurface1 = MediaCodec.createPersistentInputSurface();
        mediaRecorder2 = new MediaRecorder();
        recoderSurface2 = MediaCodec.createPersistentInputSurface();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                    1); }

        cameraManager = (CameraManager)getSystemService(CAMERA_SERVICE);

        try {
            cameraManager.openCamera(selectedCameraId, stateCallback, callbackHandler);
        } catch (Exception se) {
            Log.e(LOG_TYPE, se.getMessage());
        }

        surfaceList.add(recoderSurface1);
        surfaceList.add(recoderSurface2);
        this.preprareRecordButton();
        setupPreviewSurface();
        mr1done = true;
        mr2done = true;
    }

    protected CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
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

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            Log.d(LOG_TYPE, "Capture Progressed");
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.d(LOG_TYPE, "Capture Completed");
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.d(LOG_TYPE, "Capture Failed");
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            Log.d(LOG_TYPE, "Capture Sequence Completed");
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            Log.d(LOG_TYPE, "Capture Sequence Aborted");
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
            Log.d(LOG_TYPE, "Capture buffer lost");
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        this.startPreview();
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

    private void preprareRecordButton() {
        recordButton = findViewById(R.id.recordButton);
        recordButton.setText(R.string.start_recording);
        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (recording) {
                    stopAllRecorders();
                    reconfigureMediaRecorder(mediaRecorder1, recoderSurface1);
                    reconfigureMediaRecorder(mediaRecorder2, recoderSurface2);
                } else {
                    recordButton.setText(R.string.stop_recording);
                    recording = true;
                    camHandler3.post(new Runnable() {
                        public void run() {
                            boolean currentRecorder = true;
                            startRepeatingRequest();
                            while (recording) {
                                if (currentRecorder) {
                                    try {
                                        while (!mr1done) {
                                            Thread.sleep(100);
                                        }

                                        mediaRecorder1.start();
                                        mr1done = false;
                                    } catch (IllegalStateException ise) {
                                        mediaRecorder1.reset();
                                        reconfigureMediaRecorder(mediaRecorder1, recoderSurface1);
                                    } catch (Exception e) {
                                        Log.e(LOG_TYPE, e.getMessage());
                                    }
                                } else {
                                    try {
                                        while (!mr2done) {
                                            Thread.sleep(100);
                                        }

                                        mediaRecorder2.start();
                                        mr2done = false;
                                    } catch (IllegalStateException ise) {
                                        mediaRecorder2.reset();
                                        reconfigureMediaRecorder(mediaRecorder2, recoderSurface2);
                                    } catch (Exception e) {
                                        Log.e(LOG_TYPE, e.getMessage());
                                    }
                                }

                                try {
                                    Thread.sleep(MAX_VIDEO_DURATION - VIDEO_SWITCH_GAP);
                                } catch (Exception e) {
                                    Log.e(LOG_TYPE, e.getMessage());
                                }

                                currentRecorder = !currentRecorder;
                            }
                        }
                    });
                }
            }
        });
    }

    private void setupMediaRecorder(MediaRecorder mediaRecorder, final Surface recoderSurface) {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setMaxDuration(MAX_VIDEO_DURATION);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO));
        mediaRecorder.setOrientationHint(90);
        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mediaRecorder, int i, int i1) {
                if (i == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.d(LOG_TYPE, "MediaRecoder max duration reached.");
                    mediaRecorder.reset();
                    reconfigureMediaRecorder(mediaRecorder, recoderSurface);
                    if (mediaRecorder == mediaRecorder1) {
                        mr1done = true;
                    } else {
                        mr2done = true;
                    }
                }
            }
        });

        mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mediaRecorder, int i, int i1) {
                if (i == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
                    Log.e(LOG_TYPE, "Unknown error");
                } else if (i == MediaRecorder.MEDIA_ERROR_SERVER_DIED) {
                    Log.e(LOG_TYPE, "Server Died");
                }
            }
        });

        try {
            mediaRecorder.setPreviewDisplay(previewSurfaceHolder.getSurface());
            mediaRecorder.setInputSurface(recoderSurface);
            mediaRecorder.prepare();
        } catch (IOException ioe) {
            Log.e(LOG_TYPE, ioe.getMessage());
        } catch (Exception e) {
            Log.e(LOG_TYPE, e.getMessage());
        }
    }

    protected void setupPreviewSurface() {
        previewSurfaceView = findViewById(R.id.camera_preview);
        previewSurfaceHolder = previewSurfaceView.getHolder();
        previewSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Thread t = Thread.currentThread();
                Log.d(LOG_TYPE, "Surface is created." + t.getName());
                surfaceList.add(previewSurfaceHolder.getSurface());
                setupMediaRecorder(mediaRecorder1, recoderSurface1);
                setupMediaRecorder(mediaRecorder2, recoderSurface2);
                createCaptureSession();
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

    private void startPreview() {
        CaptureRequest.Builder captureRequestBuilder = null;
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurfaceHolder.getSurface());
            captureRequestBuilder.addTarget(recoderSurface1);
            captureRequestBuilder.addTarget(recoderSurface2);
        } catch (Exception e) {
            Log.e(LOG_TYPE, e.getMessage());
        }
        captureRequest = captureRequestBuilder.build();
        previewHandler.post(new Runnable() {
            public void run() {
                try {
                    while (cameraCaptureSession == null) {
                        Thread.sleep(100);
                    }
                    cameraCaptureSession.setRepeatingRequest(captureRequest, camcapturesessionCapturecallback, callbackHandler);
                } catch (Exception e) {
                    Log.e(LOG_TYPE, e.getMessage());
                }
            }
        });
    }

    private void reconfigureMediaRecorder(MediaRecorder mediaRecorder, Surface recoderSurface) {
        this.setupMediaRecorder(mediaRecorder, recoderSurface);
        createCaptureSession();
        startPreview();
    }

    private void createCaptureSession() {
        try {
            while (cameraDevice == null) {
                Thread.sleep(100);
            }

            if (cameraCaptureSession == null) {
                cameraDevice.createCaptureSession(surfaceList, camcapturesessionStatecallback, callbackHandler);
            }
        } catch (Exception e) {
            Log.e(LOG_TYPE, e.getMessage());
        }
    }

    private void stopAllRecorders() {
        recording = false;
        recordButton.setText(R.string.start_recording);
        try {
            mediaRecorder1.stop();
        } catch (Exception e) {}

        try {
            mediaRecorder2.stop();
        } catch (Exception e) {}

        mediaRecorder1.reset();
        mediaRecorder2.reset();

        mr1done = true;
        mr2done = true;
    }

    private void startRecording(MediaRecorder mediaRecorder, Surface recoderSurface, Handler handler) {
        CaptureRequest.Builder recordCaptureRequestBuilder = null;
        try {
            recordCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordCaptureRequestBuilder.addTarget(recoderSurface);
            recordCaptureRequestBuilder.addTarget(previewSurfaceHolder.getSurface());
        } catch (Exception e) {
            Log.e(LOG_TYPE, e.getMessage());
        }
        final CaptureRequest recordCaptureRequest = recordCaptureRequestBuilder.build();
        final MediaRecorder localMediaRecorder = mediaRecorder;
        handler.post(new Runnable() {
            public void run() {
                try {
                    cameraCaptureSession.setRepeatingRequest(recordCaptureRequest, camcapturesessionCapturecallback, callbackHandler);
                    localMediaRecorder.start();
                } catch (Exception e) {
                    Log.e(LOG_TYPE, e.getMessage());
                }
            }
        });
    }

    private void startRepeatingRequest() {
        CaptureRequest.Builder recordCaptureRequestBuilder = null;
        try {
            recordCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordCaptureRequestBuilder.addTarget(recoderSurface1);
            recordCaptureRequestBuilder.addTarget(recoderSurface2);
            recordCaptureRequestBuilder.addTarget(previewSurfaceHolder.getSurface());
        } catch (Exception e) {
            Log.e(LOG_TYPE, e.getMessage());
        }

        final CaptureRequest recordCaptureRequest = recordCaptureRequestBuilder.build();
        previewHandler.post(new Runnable() {
            public void run() {
                try {
                    cameraCaptureSession.setRepeatingRequest(recordCaptureRequest, camcapturesessionCapturecallback, callbackHandler);
                } catch (Exception e) {
                    Log.e(LOG_TYPE, e.getMessage());
                }
            }
        });
    }
}