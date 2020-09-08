package com.example.simpledashcam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.api.client.auth.oauth.OAuthCallbackUrl;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth.OAuthHmacSigner;
import com.google.api.client.http.javanet.NetHttpTransport;

public class FlickrLogin extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent startIntent = getIntent();
        Log.d(StartPage.LOG_TYPE_FLICKR, startIntent.getData().toString());
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);


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
                } catch (Exception e) {
                    Log.d(StartPage.LOG_TYPE_FLICKR, "Failed get Flickr final access token: " + e.toString());
                }
            }
        });
    }
}