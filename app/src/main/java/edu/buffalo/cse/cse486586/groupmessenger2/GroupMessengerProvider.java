package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    ///*************************** begin
    private static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private static final String DB_NAME = "my";
    //private static final String DB_TABLE = "keyvalue";
    private static final int DB_VERSION = 1;


    private SQLiteDatabase db;
    private MySQLiteOpenHelper mySQLiteOpenHelper;


    private static class MySQLiteOpenHelper extends SQLiteOpenHelper {
        public MySQLiteOpenHelper(Context context, String name,
                                  SQLiteDatabase.CursorFactory factory, int version){
            super(context, name, factory, version);
        }

        private static final String DB_CREATE =
                "create table " + DB_NAME + " (key primary key, value);" ;

        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(DB_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
            db.execSQL("drop table if exists " + DB_NAME);
            onCreate(db);
        }
    }
    ///******************************** end

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        /////*************** begin
        db = mySQLiteOpenHelper.getWritableDatabase();
        try{

            //long id = db.insert(DB_NAME, null, values);
            long id = db.insertWithOnConflict(DB_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);

            uri = ContentUris.withAppendedId(uri, id);

        } catch (Exception e){
            Log.e(TAG, "Error in qb inserting");
        }

        /////*************** end

        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        ///************ begin
        Context context = getContext();
        mySQLiteOpenHelper = new MySQLiteOpenHelper(context, DB_NAME, null, DB_VERSION);

        //db = mySQLiteOpenHelper.getWritableDatabase();

        return (db != null);
        ///************ end
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        ///**************** begin

        db = mySQLiteOpenHelper.getWritableDatabase();
        final String sql = "select * from " + DB_NAME + " where " + "key=\"" + selection +"\"";
        Cursor cursor = db.rawQuery(sql, selectionArgs);

        ///**************** end
        Log.v("query", selection);
        return null;
    }
}
