package com.example.simpledashcam;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.MultipartContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.io.CharStreams;


import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;

public class UploadFileService extends Service {
    private ServiceHandler serviceHandler;
    static final String LOG_TYPE = "UploadFileService";
    protected String apiKey;
    protected String apiSecret;
    protected String accessToken;
    protected String tokenSecret;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(final Message msg) {
            if (apiKey == null) {
                Log.e(LOG_TYPE, "User not logged in, service exiting!");
                stopSelf();
            } else {
                final File fileToUpload = (File)msg.obj;
                Log.i(LOG_TYPE, "Service received file: " + fileToUpload.getPath() + " size: " + fileToUpload.length() + " to upload.");

                if (uploadFile(fileToUpload)) {
                    deleteFile(fileToUpload);
                }

                // Stop the service using the startId, so that we don't stop
                // the service in the middle of handling another job
                stopSelf(msg.arg1);
            }
        }
    }

    @Override
    public void onCreate() {
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
                apiKey = c.getString(c.getColumnIndex(FlickrLoginProvider.API_KEY));
                apiSecret = c.getString(c.getColumnIndex(FlickrLoginProvider.API_KEY_SECRET));
                accessToken = c.getString(c.getColumnIndex(FlickrLoginProvider.ACCESS_TOKEN));
                tokenSecret = c.getString(c.getColumnIndex(FlickrLoginProvider.TOKEN_SECRET));
            }
        }

        HandlerThread thread = new HandlerThread("UploadFileServiceThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        Looper serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);

        Log.i(LOG_TYPE, "Service created.");
        c.close();
    }

    public UploadFileService() {
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
        if (null != intent.getStringExtra(PrepareFileService.UPLOAD_FILE_URI)) {
            msg.obj = new File(intent.getStringExtra(PrepareFileService.UPLOAD_FILE_URI));
        }

        serviceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    private boolean uploadFile(File file) {
        try {
            OAuthParameters parameters = new OAuthParameters();
            parameters.consumerKey = apiKey;
            OAuthHmacSigner hmacSigner = new OAuthHmacSigner();
            hmacSigner.clientSharedSecret = apiSecret;
            hmacSigner.tokenSharedSecret = tokenSecret;
            parameters.signer = hmacSigner;
            parameters.token = accessToken;
            parameters.computeNonce();
            parameters.computeTimestamp();

            NetHttpTransport transport = new NetHttpTransport();
            HttpRequestFactory requestFactory = transport.createRequestFactory(parameters);
            GenericUrl genericUrl = new GenericUrl(StartPage.FLICKR_UPLOAD_ENDPOINT);
            genericUrl.set("format", "json")
                    .set("nojsoncallback", 1);
            FileContent fileContent = new FileContent(getMimeType(file.getPath()), file);
            MultipartContent multipartContent = new MultipartContent()
                    .setMediaType(
                            new HttpMediaType("multipart/form-data")
                                    .setParameter("boundary", "__END_OF_PART__"));
            MultipartContent.Part part = new MultipartContent.Part(fileContent);
            part.setHeaders(new HttpHeaders().set(
                    "Content-Disposition",
                    String.format("form-data; name=\"photo\"; filename=\"%s\"", file.getAbsolutePath())));

            Log.i(StartPage.LOG_TYPE_FLICKR, "Upload mimetype: " + getMimeType(file.getPath()));
            Log.i(StartPage.LOG_TYPE_FLICKR, "Upload absfilepath: " + file.getAbsolutePath());
            multipartContent.addPart(part);
            HttpRequest request = requestFactory.buildPostRequest(genericUrl, multipartContent);
            Log.i(StartPage.LOG_TYPE_FLICKR, "HTTP Request: " + request.getHeaders().toString());

            HttpResponse response = request.execute();
            String textResponse = null;
            try (Reader reader = new InputStreamReader(response.getContent())) {
                textResponse = CharStreams.toString(reader);
            }

            Log.i(StartPage.LOG_TYPE_FLICKR, "Upload response: " + textResponse);
            return response.getStatusCode() == 200;
        } catch (Exception e) {
            Log.e(StartPage.LOG_TYPE_FLICKR, "Failed upload to Flickr: " + e.toString());
            return false;
        }
    }

    /**
     *
     * @param file
     */
    private void deleteFile(File file) {
        Log.i(StartPage.LOG_TYPE_FLICKR, "Deleting file post upload: " + file.getPath());
        boolean deleted = file.delete();
        File parentDir = file.getParentFile();

        try {
            if (Files.isDirectory(parentDir.toPath())) {
                if (!Files.list(parentDir.toPath()).findAny().isPresent()) {
                    Log.i(StartPage.LOG_TYPE_FLICKR, "Deleting empty dir post upload: " + parentDir.getPath());
                    boolean deletedDir = parentDir.delete();
                }
            }
        } catch (Exception e) {
            Log.e(StartPage.LOG_TYPE_FLICKR, "Failed to delete dir: " + parentDir.getPath());
        }
    }

    public static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }
}
