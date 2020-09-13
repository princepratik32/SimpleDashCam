package com.example.simpledashcam;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.io.CharStreams;
import org.json.JSONObject;
import java.io.InputStreamReader;
import java.io.Reader;

public class FlickrLogin extends AppCompatActivity {
    protected TextView textView;
    protected SharedPreferences preferences;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_flickr_login);
        textView = findViewById(R.id.login_process_text);
        textView.setText(R.string.processing_login);

        Intent startIntent = getIntent();
        Log.d(StartPage.LOG_TYPE_FLICKR, startIntent.getData().toString());
        preferences = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        final OAuthGetAccessToken oAuthGetAccessToken = new OAuthGetAccessToken("https://www.flickr.com/services/oauth/access_token");
        oAuthGetAccessToken.temporaryToken = startIntent.getData().getQueryParameter("oauth_token");
        oAuthGetAccessToken.verifier = startIntent.getData().getQueryParameter("oauth_verifier");
        oAuthGetAccessToken.consumerKey = StartPage.FLICKR_API_KEY;
        oAuthGetAccessToken.transport = new NetHttpTransport();
        OAuthHmacSigner hmacSigner = new OAuthHmacSigner();
        hmacSigner.clientSharedSecret = StartPage.FLICKR_API_SECRET;
        hmacSigner.tokenSharedSecret = preferences.getString(getString(R.string.preference_temp_token_secret), null);
        oAuthGetAccessToken.signer = hmacSigner;

        Log.d(StartPage.LOG_TYPE_FLICKR, startIntent.getData().toString());

        HandlerThread threadB = new HandlerThread("sideB");
        threadB.start();
        Handler threadBHandler = new Handler(threadB.getLooper());

        Log.d(StartPage.LOG_TYPE_FLICKR, "Flickr external URL: " + oAuthGetAccessToken.toURL().toExternalForm());

        threadBHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    OAuthCredentialsResponse response = oAuthGetAccessToken.execute();
                    Log.d(StartPage.LOG_TYPE_FLICKR, "Flickr final access token: " + response.token);

                    FlickrUser user = loginToFlickr(StartPage.FLICKR_API_KEY,
                            StartPage.FLICKR_API_SECRET,
                            response.token,
                            response.tokenSecret);

                    String username = (user.getUsername() != null && !user.getUsername().isEmpty()) ?
                            user.getUsername() : "";
                    String nsid = (user.getNsid() != null && !user.getNsid().isEmpty()) ?
                            user.getNsid() : "";
                    storeFlickrLogin(StartPage.FLICKR_API_KEY,
                            StartPage.FLICKR_API_SECRET,
                            nsid,
                            username,
                            response.token,
                            response.tokenSecret);
                    textView.setText(R.string.successful_login_message);
                    Thread.sleep(2000);
                    finish();
                } catch (Exception e) {
                    Log.d(StartPage.LOG_TYPE_FLICKR, "Failed get Flickr final access token: " + e.toString());
                }
            }
        });
    }

    private void storeFlickrLogin(String apiKey,
                                  String apiSecret,
                                  String nsid,
                                  String username,
                                  String accessToken,
                                  String tokenSecret) {
        String url = FlickrLoginProvider.URL;
        Uri flickrLogin = Uri.parse(url);

        ContentValues cv = new ContentValues();
        cv.put(FlickrLoginProvider.API_KEY, apiKey);
        cv.put(FlickrLoginProvider.API_KEY_SECRET, apiSecret);
        cv.put(FlickrLoginProvider.NSID, nsid);
        cv.put(FlickrLoginProvider.ACCESS_TOKEN, accessToken);
        cv.put(FlickrLoginProvider.TOKEN_SECRET, tokenSecret);
        cv.put(FlickrLoginProvider.USERNAME, username);

        Uri result = getContentResolver().insert(flickrLogin, cv);
        Log.d(StartPage.LOG_TYPE_FLICKR, "Stored Flickr login: " + result.toString());
    }

    private FlickrUser loginToFlickr(String apiKey,
                                     String apiSecret,
                                     String accessToken,
                                     String tokenSecret) {
        FlickrUser user = new FlickrUser();

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
            GenericUrl genericUrl = new GenericUrl(StartPage.FLICKR_REST_ENDPOINT);
            genericUrl.set("format", "json")
                    .set("nojsoncallback", 1)
                    .set("method", "flickr.test.login");
            HttpRequest request = requestFactory.buildGetRequest(genericUrl);
            Log.d(StartPage.LOG_TYPE_FLICKR, "HTTP Request: " + request.getHeaders().toString());

            HttpResponse response = request.execute();
            String textResponse = null;
            try (Reader reader = new InputStreamReader(response.getContent())) {
                textResponse = CharStreams.toString(reader);
            }

            JSONObject responseJson = new JSONObject(textResponse);
            if (responseJson.has("user")) {
                JSONObject userJson = responseJson.getJSONObject("user");
                if (userJson.has("id")) {
                    user.setNsid((String)userJson.get("id"));
                }

                if (userJson.has("username")) {
                    JSONObject usernameJson = userJson.getJSONObject("username");
                    if (usernameJson.has("_content")) {
                        user.setUsername((String)usernameJson.get("_content"));
                    }
                }
            }
            Log.d(StartPage.LOG_TYPE_FLICKR, "Login response: " + textResponse);
        } catch (Exception e) {
            Log.d(StartPage.LOG_TYPE_FLICKR, "Failed log into Flickr: " + e.toString());
        }

        Log.d(StartPage.LOG_TYPE_FLICKR, "User: " + user.getNsid());
        return user;
    }
}