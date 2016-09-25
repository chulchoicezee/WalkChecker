package com.chulgee.walkchecker;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import com.chulgee.walkchecker.util.Const;

public class WalkCheckerProvider extends ContentProvider {

    private DbHelper mDbHelper;
    private static final String DB_NAME = "mydb";
    private static final int DB_VER = 1;
    private SQLiteDatabase mDb;

    static final Uri CONTENT_URI = Uri.parse(Const.CONTENT_URI);

    @Override
    public boolean onCreate() {

        // create only open helper, not real database to avoid unnecessary load at this time
        mDbHelper = new DbHelper(getContext(), DB_NAME, null, DB_VER);

        return true;
    }

    @Override
    public String getType(Uri uri) {
        // at the given URI.
        return Const.CONTENT_URI;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        mDb = mDbHelper.getWritableDatabase();
        Cursor c = mDb.query(mDbHelper.TABLE, new String[]{mDbHelper.COLUMN_COUNT, mDbHelper.COLUMN_DATE}, null, null, null, null, "_id desc");
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        mDb = mDbHelper.getWritableDatabase();
        long row = mDb.insert(DbHelper.TABLE, null, values);
        if(row > 0){
            Uri notiUri = ContentUris.withAppendedId(CONTENT_URI, row);
            getContext().getContentResolver().notifyChange(notiUri, null);
            return notiUri;
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        mDb = mDbHelper.getWritableDatabase();
        int count = 0;
        count = mDb.delete(DbHelper.TABLE, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        mDb = mDbHelper.getWritableDatabase();
        int count = 0;
        count = mDb.update(DbHelper.TABLE, values, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    /**
     * data base helper
     */
    public static final class DbHelper extends SQLiteOpenHelper{
        private static final String TAG = "DbHelper";
        public static final String TABLE = "walk_checker";
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_DATE = "date";
        public static final String COLUMN_COUNT = "count";

        public DbHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("create table " + TABLE + "("
                    + COLUMN_ID + " integer primary key autoincrement, "
                    + COLUMN_DATE + " text not null, "
                    + COLUMN_COUNT + " text not null, "
                    + "UNIQUE(" + COLUMN_DATE + ") ON CONFLICT REPLACE"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }
}
