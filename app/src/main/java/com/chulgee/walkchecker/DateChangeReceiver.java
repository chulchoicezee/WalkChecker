package com.chulgee.walkchecker;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.chulgee.walkchecker.util.Const;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "DateChangeReceiver";
    private Context context;

    public DateChangeReceiver(Context $context) {
        context = $context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
        if (Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
            // save db
            Log.v(TAG, "ACTION_DATE_CHANGED intent.getAction()="+intent.getAction());
            SharedPreferences pref = context.getSharedPreferences("myPref", Context.MODE_PRIVATE);
            boolean running = pref.getBoolean("Running", false);
            long count = pref.getLong("count", 0);
            String date = pref.getString("date", "");
            Log.v(TAG, "onReceive running="+running+", count="+count+", date="+date);
            ContentValues values = new ContentValues();
            values.put("count", count+"");
            values.put("date", date+"");
            Uri uri = context.getContentResolver().insert(Uri.parse(Const.CONTENT_URI), values);
            Log.v(TAG, "insert uri="+uri);

            // set current date
            Date today = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String curDate = sdf.format(today);
            Log.v(TAG, "cur date="+curDate);
            SharedPreferences.Editor edit = pref.edit();
            edit.putString("date", curDate);
            edit.commit();

            Toast.makeText(context, "Date and count saved : "+date, Toast.LENGTH_SHORT).show();
        }
    }
}
