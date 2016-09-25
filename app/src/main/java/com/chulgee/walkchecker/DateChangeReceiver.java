package com.chulgee.walkchecker;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.chulgee.walkchecker.util.Const;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateChangeReceiver extends BroadcastReceiver {

    private static final String TAG = "DateChangeReceiver";
    static int temp;
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
            ContentValues values = new ContentValues();
            values.put("count", WalkCheckerService.getCount());
            values.put("date", WalkCheckerService.getDATE());
            Uri uri = context.getContentResolver().insert(Uri.parse(Const.CONTENT_URI), values);
            Log.v(TAG, "insert uri="+uri);
            // set current date
            Date today = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String date = sdf.format(today);
            Log.v(TAG, "date="+date);
            WalkCheckerService.setDATE(date);
            Toast.makeText(context, "Date updated : "+date, Toast.LENGTH_SHORT).show();
        }
    }
}
