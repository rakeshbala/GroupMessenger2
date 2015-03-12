package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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
        try {
            FileOutputStream outputStream = getContext().openFileOutput( values.getAsString("key"), Context.MODE_PRIVATE);
            outputStream.write(values.getAsString("value").getBytes());
            outputStream.close();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        String [] columns =  new String[2];
        columns[0] = "key";
        columns[1] = "value";
        MatrixCursor cursor = new MatrixCursor(columns);

        FileInputStream inputStream;
        try {
            inputStream = getContext().openFileInput(selection);
            byte[] buffer = new byte[inputStream.available()];
            int length = inputStream.read(buffer);
            inputStream.close();
            String value = new String(buffer, 0, length, StandardCharsets.UTF_8);
            String columnsArray[] = {selection,value};
            cursor.addRow(columnsArray);


        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        if (selection != null){
            Log.v("query", selection);
        }
        return cursor;
    }
}
