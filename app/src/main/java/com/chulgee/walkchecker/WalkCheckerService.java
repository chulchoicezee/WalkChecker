package com.chulgee.walkchecker;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.chulgee.walkchecker.http.HttpAsyncTask;
import com.chulgee.walkchecker.http.IHttp;
import com.chulgee.walkchecker.util.Const;
import com.chulgee.walkchecker.util.JsonUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WalkCheckerService extends Service implements SensorEventListener, View.OnTouchListener {

    private static final String TAG = "WalkCheckerService";

    /**
     * display state
     *   none(no show) -> 0
     *   activity -> 1
     *   mini view -> 2
     */
    private int mDisplayState;
    // running or not
    private boolean mRunning;
    // static vars and getter/setter
    private static long COUNT;
    private static String ADDR;
    private static String DATE;

    // getter / setter
    public static long getCount() {
        return COUNT;
    }
    public static String getADDR() {
        return ADDR;
    }
    public static String getDATE() { return DATE; }
    public static void setDATE(String date) { DATE = date; }
    public void setCount(long count){
        SharedPreferences pref = getSharedPreferences("myPref", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putLong("count", count);
        edit.commit();
        COUNT = count;
    }
    public void setRunning(boolean run){
        SharedPreferences pref = getSharedPreferences("myPref", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putBoolean("Running", run);
        edit.commit();
        mRunning = run;
    }

    // sensor vars
    private long lastTime;
    private float speed;
    private float lastX;
    private float lastY;
    private float lastZ;
    private float x, y, z;
    private static final int SHAKE_THRESHOLD = 800;
    private SensorManager mSensor;
    private Sensor mAccelerometer;
    private Vibrator mVibe;
    // mini view's vars
    private WindowManager wm;
    private View mView;
    private float mTouchX, mTouchY;
    private int mViewX, mViewY;
    private WindowManager.LayoutParams mParams;
    // gps vars
    private LocationManager mLocationManager;
    private LocationListener mLocationListener = new MyLocationListener();
    // br listeners
    private LocalBroadcastReceiver mLocalReceiver = new LocalBroadcastReceiver();
    private DateChangeReceiver mDateChangedReceiver = new DateChangeReceiver(this);

    @Override
    public void onCreate() {
        super.onCreate();

        // get system services
        mSensor = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mVibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // regi br
        registerBroadcastReceivers();

        Toast.makeText(this, "WalkCheckerService onCreated", Toast.LENGTH_SHORT).show();
    }

    /**
     * just in case that android os kill this service, just store current data to pref in advance.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        setCount(COUNT);
        setRunning(mRunning);
        Log.v(TAG, "onLowMemory mRunning=" + mRunning + ", COUNT=" + COUNT);
    }

    /**
     *  return START_STICKY to keep it reconnected
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int res = super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "onStartCommand intent=" + intent);

        // this is the case system kills me for some reason. so, restore previous data
        if (intent == null) {
            Log.v(TAG, "COUNT=" + COUNT);
            restoreData();
            Toast.makeText(this, "restored!", Toast.LENGTH_SHORT).show();
        }

        return Service.START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.v(TAG, "onTaskRemoved COUNT=" + COUNT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    /**
     * judge that this is a step or not. it needs to be tunned depending on devices.
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            long curTime = System.currentTimeMillis();
            long gapTime = curTime - lastTime;
            //Log.v(TAG, "onSensorChanged gapTime=" + gapTime);

            if (gapTime > 100) {
                lastTime = curTime;
                x = event.values[SensorManager.DATA_X];
                y = event.values[SensorManager.DATA_Y];
                z = event.values[SensorManager.DATA_Z];

                speed = Math.abs(x + y + z - lastX - lastY - lastZ) / gapTime * 10000;
                //Log.v(TAG, "onSensorChanged speed=" + speed);
                if (speed > SHAKE_THRESHOLD) {
                    Intent i = new Intent(Const.ACTION_COUNT_NOTIFY);
                    COUNT++;
                    setCount(COUNT);
                    mVibe.vibrate(100);
                    Log.v(TAG, "onSensorChanged got a step! count=" + COUNT + ", mDisplayState=" + mDisplayState);

                    // deliver data to console for display. ex) activity or mini view
                    if (mDisplayState == 1) {
                        i.putExtra("count", COUNT + "");
                        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
                    } else if (mDisplayState == 2) {
                        TextView count = (TextView) mView.findViewById(R.id.mini_tv1);
                        count.setText(COUNT + "");
                        TextView addr = (TextView) mView.findViewById(R.id.mini_tv2);
                        addr.setText(COUNT * 58 / 100 + "" + "m");
                    }
                }
                lastX = event.values[SensorManager.DATA_X];
                lastY = event.values[SensorManager.DATA_Y];
                lastZ = event.values[SensorManager.DATA_Z];
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy mSensor=" + mSensor);

        setCount(COUNT);
        setRunning(mRunning);
        Log.v(TAG, "onDestroy mRunning=" + mRunning + ", COUNT=" + COUNT);

        // release listener and receiver
        if (mSensor != null)
            mSensor.unregisterListener(this);
        if (mDateChangedReceiver != null)
            unregisterReceiver(mDateChangedReceiver);
        if (mLocalReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
    }

    /**
     * for drag and drop of mini view
     * @param v
     * @param event
     * @return
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchX = event.getRawX();
                mTouchY = event.getRawY();
                mViewX = mParams.x;
                mViewY = mParams.y;
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                int x = (int) (event.getRawX() - mTouchX);
                int y = (int) (event.getRawY() - mTouchY);
                mParams.x = mViewX + x;
                mParams.y = mViewY + y;
                wm.updateViewLayout(mView, mParams);
                break;
        }
        return true;
    }

    /**
     * Location listener
     */
    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {
            Log.v(TAG, "Location changed : Lat" + loc.getLatitude() + "Lng: " + loc.getLongitude());
            /**
             * revert lat/lng to address through http
             * this performs Asynchronously with AsyncTask
             */
            executeHttp(loc.getLatitude() + "", loc.getLongitude() + "");
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    /**
     * local br receiver for comm among components
     */
    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "action=" + action);

            if (action.equals(Const.ACTION_ACTIVITY_ONRESUME)) { // activity launched
                // remove mini view if exists
                if (mDisplayState == 2) {
                    wm.removeView(mView);
                }
                // init display for activity
                initActivityDisplay();
                if(mRunning){
                    startListeningAccelerometer();
                    startListeningGps();
                }
                mDisplayState = 1;
            } else if (action.equals(Const.ACTION_ACTIVITY_ONSTOP)) { // activity stopped
                if (mRunning) {
                    // launch mini view
                    initMiniViewDisplay();
                    mDisplayState = 2;
                } else
                    mDisplayState = 0;
            } else if (action.equals(Const.ACTION_WALKING_START)) { // walking started
                // set current date
                Date today = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String date = sdf.format(today);
                setDATE(date);
                // start listening from accelerometer
                startListeningAccelerometer();
                // start listening from gps
                startListeningGps();
                setRunning(true);
                mDisplayState = 1;// for the first time, it means no service started yet
            } else if (action.equals(Const.ACTION_WALKING_STOP)) { // walking stopped
                // stop listening all sensors
                stopListeningAll();
                setRunning(false);
            }
        }
    }

    /************************************ apis ***************************************************/

    /**
     * Http api.
     * it can handle all HTTP tasks including ui access task.
     * @param lat
     * @param lng
     */
    void executeHttp(String lat, String lng) {

        // make url
        String latlng = lng + "," + lat;// ex) latlng = "127.1052133,37.3595316";
        Uri.Builder builder = new Uri.Builder();
        try {
            builder.scheme("https").encodedAuthority("openapi.naver.com").appendEncodedPath("v1").appendEncodedPath("map").appendEncodedPath("reversegeocode")
                    .appendQueryParameter("encoding", "utf-8").appendQueryParameter("coord", "latlng")
                    .appendQueryParameter("output", URLEncoder.encode("json", "utf-8")).appendQueryParameter("query", latlng);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String url = builder.build().toString();
        Log.v(TAG, "executeHttp url=" + url);

        // create http engine
        HttpAsyncTask httpEngine = new HttpAsyncTask(new HttpAsyncTask.OnDataLoadedListener() {
            @Override
            public void onDataLoaded(int resCode, String result) {
                Log.v(TAG, "resCode=" + resCode + ", result=" + result);
                if (result == null) {
                    Toast.makeText(WalkCheckerService.this, "result null", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (resCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    try {
                        JSONObject root = new JSONObject(result);
                        String message = root.getString("errorMessage");
                        ADDR = message;
                        Toast.makeText(WalkCheckerService.this, message, Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    JsonUtil json = new JsonUtil(WalkCheckerService.this, result);
                    String addr = json.getAddr();
                    Log.v(TAG, "addr=" + addr);
                    ADDR = addr;
                }
                Intent i = new Intent(Const.ACTION_ADDR_NOTIFY);
                i.putExtra("addr", ADDR);
                LocalBroadcastManager.getInstance(WalkCheckerService.this).sendBroadcast(i);
            }
        });
        // set header request
        Bundle requestHeader = new Bundle();
        requestHeader.putString("Host", "openapi.naver.com");
        requestHeader.putString("User-Agent", "curl/7.43.0");
        requestHeader.putString("Accept", "*/*");
        requestHeader.putString("Content-Type", "application/json");
        requestHeader.putString("X-Naver-Client-Id", "57XHJp961fzi1rB5PNNz");
        requestHeader.putString("X-Naver-Client-Secret", "YgHkibU7W2");
        httpEngine.setRequestHeader(requestHeader);
        // set conn type request
        httpEngine.setConnType(IHttp.ConnType.GET);
        // set timeout
        httpEngine.setConnTimeout(30000);
        httpEngine.setReadTimeout(10000);

        // make http connection
        httpEngine.execute(url);
    }

    private void restoreData() {
        SharedPreferences pref = getSharedPreferences("myPref", MODE_PRIVATE);
        mRunning = pref.getBoolean("Running", false);
        COUNT = pref.getLong("count", 0);
        Log.v(TAG, "restorePreviousData mRunning=" + mRunning + ", COUNT=" + COUNT);
    }

    public void registerBroadcastReceivers() {
        // local br
        IntentFilter localFilter = new IntentFilter();
        localFilter.addAction(Const.ACTION_ACTIVITY_ONRESUME);
        localFilter.addAction(Const.ACTION_ACTIVITY_ONSTOP);
        localFilter.addAction(Const.ACTION_WALKING_START);
        localFilter.addAction(Const.ACTION_WALKING_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, localFilter);
        // global br
        IntentFilter globalFilter = new IntentFilter();
        globalFilter.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(mDateChangedReceiver, globalFilter);
    }

    private void initActivityDisplay() {
        Intent i = new Intent(Const.ACTION_INIT_ACTIVITY);
        i.putExtra("Running", mRunning);
        i.putExtra("count", COUNT + "");
        i.putExtra("addr", ADDR);
        LocalBroadcastManager.getInstance(WalkCheckerService.this).sendBroadcast(i);
    }

    private void initMiniViewDisplay() {
        LayoutInflater mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mView = mInflater.inflate(R.layout.mini, null);
        TextView count = (TextView) mView.findViewById(R.id.mini_tv1);
        count.setText(COUNT + "");
        TextView addr = (TextView) mView.findViewById(R.id.mini_tv2);
        addr.setText(COUNT * 58 / 100 + "" + "m");
        mView.setOnTouchListener(WalkCheckerService.this);
        /*mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDisplayState == 2) {
                    Intent i = new Intent(getApplicationContext(), WalkCheckerActivity.class);
                    startActivity(i);
                }
            }
        });*/
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mParams = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                , WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                , PixelFormat.TRANSPARENT);
        wm.addView(mView, mParams);
    }

    private void startListeningAccelerometer() {
        if (mAccelerometer != null) {
            mSensor.registerListener(WalkCheckerService.this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void startListeningGps() {
        boolean isGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        Log.v(TAG, "isGPSEnabled=" + isGPSEnabled + ", isNetworkEnabled=" + isNetworkEnabled);
        if (isGPSEnabled && isNetworkEnabled) {
            if (ActivityCompat.checkSelfPermission(WalkCheckerService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(WalkCheckerService.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(WalkCheckerService.this, "please turn on gps", Toast.LENGTH_SHORT).show();
                return;
            }
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, mLocationListener);
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, mLocationListener);
        } else {
            Toast.makeText(WalkCheckerService.this, "please turn on gps", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListeningAll() {
        if (mSensor != null) {
            Log.v(TAG, "sensor unregi..");
            mSensor.unregisterListener(WalkCheckerService.this);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(WalkCheckerService.this, "gps permission is not acquired", Toast.LENGTH_SHORT).show();
            return;
        }
        mLocationManager.removeUpdates(mLocationListener);
    }
}
