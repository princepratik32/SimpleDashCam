package com.example.simpledashcam;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class PrepareFileService extends Service {
    private ServiceHandler serviceHandler;
    protected FFmpeg ffmpeg;
    static final String LOG_TYPE = "PrepareFileService";
    static final String CHUNK_LENGTH = "60";
    static final long MIN_FILE_SIZE = 10_000_000;
    static final String UPLOAD_FILE_URI = "com.example.UploadFileService.URI";

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(final Message msg) {
            final File fileToProcess = (File)msg.obj;
            Log.i(LOG_TYPE, "Service received file: " + fileToProcess.getPath() + " size: " + fileToProcess.length());

            File temporaryDir = null;
            File outFile = null;
            try {
                temporaryDir = getTemporaryDirectory(fileToProcess.getParent());
                outFile = new File(temporaryDir.getPath() + "/" + "out%03d.mp4");
            } catch (Exception e) {
                Log.e(LOG_TYPE, e.getMessage());
                stopSelf(msg.arg1);
            }

            // If the file is very small, don't split.
            if (fileToProcess.length() <= MIN_FILE_SIZE) {
                outFile = new File(temporaryDir.getPath() + "/" + fileToProcess.getName());
                try {
                    Path temp = Files.move(
                            fileToProcess.toPath(),
                            outFile.toPath()
                    );

                    queueFilesForUpload(new File[]{outFile});
                } catch (IOException e) {
                    Log.e(LOG_TYPE, "failed to move file: " + e.getMessage());
                }

                stopSelf(msg.arg1);
                return;
            }

            String[] cmd = {"-i", fileToProcess.getPath(),
                    "-map",
                    "0",
                    "-segment_time",
                    CHUNK_LENGTH,
                    "-preset",
                    "ultrafast",
                    "-f",
                    "segment",
                    "-vcodec",
                    "copy",
                    "-b:v",
                    "2097152",
                    "-b:a",
                    "48000",
                    "-ac",
                    "2",
                    "-ar",
                    "22050",
                    outFile.getPath()
            };

            try {
                final File finalTemporaryDir = temporaryDir;
                ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                    @Override
                    public void onStart() {}

                    @Override
                    public void onProgress(String message) {}

                    @Override
                    public void onFailure(String message) {
                        Log.e(LOG_TYPE, "FFMPEG failed: " + message);
                        stopSelf(msg.arg1);
                    }

                    @Override
                    public void onSuccess(String message) {
                        Log.i(LOG_TYPE, "FFMPEG successful: " + message);
                    }

                    @Override
                    public void onFinish() {
                        Log.i(LOG_TYPE, "FFMPEG Finished. Message: " + msg.arg1);

                        queueFilesForUpload(finalTemporaryDir.listFiles());
                        boolean deleted = fileToProcess.delete();
                        if (deleted) {
                            Log.i(LOG_TYPE, "Deleted file: " + fileToProcess.getPath());
                        } else {
                            Log.e(LOG_TYPE, "Failed to delete file: " + fileToProcess.getPath());
                        }
                        stopSelf(msg.arg1);
                    }
                });
            } catch (FFmpegCommandAlreadyRunningException e) {
                // Handle if FFmpeg is already running
                Log.e(LOG_TYPE, "FFMPEG already running: " + e.getMessage());
                stopSelf(msg.arg1);
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("PrepareFileServiceThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        Looper serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);

        ffmpeg = FFmpeg.getInstance(this);

        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {

                @Override
                public void onStart() {}

                @Override
                public void onFailure() {}

                @Override
                public void onSuccess() {}

                @Override
                public void onFinish() {}
            });
        } catch (FFmpegNotSupportedException e) {
            // Handle if FFmpeg is not supported by device
            Log.e(LOG_TYPE, "FFmpeg is not supported by device");
        }


        Log.e(LOG_TYPE, "Service created.");
    }

    public PrepareFileService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TYPE, "My watch has ended!");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        if (null != intent.getStringExtra(CameraPreview.PREPARE_FILE_URI)) {
            msg.obj = new File(intent.getStringExtra(CameraPreview.PREPARE_FILE_URI));
        }

        serviceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    protected File getTemporaryDirectory(String basePath) throws Exception {
        long currentTime = (new Date()).getTime() / 1000;
        File newDirectory = new File(basePath, Long.toString(currentTime));

        if (!newDirectory.exists()) {
            if (!newDirectory.mkdirs()) {
                throw new Exception("Failed to create directory: " + newDirectory.getPath());
            }
        }

        return newDirectory;
    }

    protected void queueFilesForUpload(File[] files) {
        if (null != files) {
            for (File f: files) {
                Intent uploadFileServiceIntent = new Intent();
                uploadFileServiceIntent.setComponent(new ComponentName(
                        "com.example.simpledashcam",
                        "com.example.simpledashcam.UploadFileService"));
                uploadFileServiceIntent.putExtra(PrepareFileService.UPLOAD_FILE_URI, f.getAbsolutePath());
                startService(uploadFileServiceIntent);
                Log.i(LOG_TYPE, "Queuing file for upload: " + f.getPath() + " size: " + f.length());
            }
        }
    }
}
