package com.example.simpledashcam;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class FlickrLoginProvider extends ContentProvider {
    static final String _ID = "_id";
    static final String NSID = "nsid";
    static final String API_KEY = "api_key";
    static final String API_KEY_SECRET = "api_key_secret";
    static final String ACCESS_TOKEN = "access_token";
    static final String TOKEN_SECRET = "token_secret";
    static final String USERNAME = "username";

    static final String DATABASE_NAME = "SimpleDashCam";
    static final String TABLE_NAME_FLICKR_LOGIN = "FlickrLogin";
    static final int DATABASE_VERSION = 1;
    static final String CREATE_DB_TABLE =
            " CREATE TABLE " + TABLE_NAME_FLICKR_LOGIN +
                    " (" + _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " " + NSID + " TEXT NOT NULL, " +
                    " " + API_KEY + " TEXT NOT NULL, " +
                    " " + API_KEY_SECRET + " TEXT NOT NULL, " +
                    " " + ACCESS_TOKEN + " TEXT NOT NULL, " +
                    " " + TOKEN_SECRET + " TEXT NOT NULL, " +
                    " " + USERNAME + " TEXT NOT NULL);";

    static final String PROVIDER_NAME = "com.example.simpledashcam.provider";
    static final String URL = "content://" + PROVIDER_NAME + "/" + TABLE_NAME_FLICKR_LOGIN;
    static final Uri CONTENT_URI = Uri.parse(URL);

    private SQLiteDatabase db;

    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_DB_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_FLICKR_LOGIN);
            onCreate(db);
        }
    }

    public FlickrLoginProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = db.delete(TABLE_NAME_FLICKR_LOGIN, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.example.FlickrLoginProvider";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long rowID = db.insert(TABLE_NAME_FLICKR_LOGIN, "", values);
        if (rowID > 0) {
            Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);
            return _uri;
        }

        throw new SQLException("Failed to add a record into " + uri);
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
        return (db != null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME_FLICKR_LOGIN);

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int count = db.update(TABLE_NAME_FLICKR_LOGIN, values, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
