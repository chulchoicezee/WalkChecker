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
     *   none -> 0
     *   activity -> 1
     *   mini window -> 2
     */
    private int mDisplayState;
    private Intent mIntent;

    private boolean mRunning;
    private static long COUNT;
    private static String ADDR;
    private static String DATE;

    public static long getCount() {
        return COUNT;
    }

    public static String getADDR() {
        return ADDR;
    }

    public static String getDATE() {
        return ADDR;
    }

    public static void setDATE(String date){
        DATE = date;
    }
    // sensor
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

    // mini window
    private WindowManager wm;
    private float mTouchX, mTouchY;
    private int mViewX, mViewY;
    private WindowManager.LayoutParams mParams;
    private View mView;

    // gps
    private LocationManager mLocationManager = null; // 위치 정보 프로바이더
    private LocationListener mLocationListener = null; //위치 정보가 업데이트시 동작

    //local br
    private LocalBroadcastReceiver mLocalReceiver = new LocalBroadcastReceiver();

    @Override
    public void onCreate() {
        super.onCreate();
        mSensor = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mVibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // regi local br for comm
        IntentFilter filter = new IntentFilter();
        filter.addAction(Const.ACTION_ACTIVITY_ONRESUME);
        filter.addAction(Const.ACTION_ACTIVITY_ONSTOP);
        filter.addAction(Const.ACTION_CHECKING_START);
        filter.addAction(Const.ACTION_CHECKING_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, filter);

        // gps
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new MyLocationListener();
        boolean isGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        Log.v(TAG, "isGPSEnabled=" + isGPSEnabled + ", isNetworkEnabled=" + isNetworkEnabled);
        if (isGPSEnabled && isNetworkEnabled) {

            //선택된 프로바이더를 사용해 위치정보를 업데이트
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }

        } else {
            Toast.makeText(WalkCheckerService.this, "turn on gps", Toast.LENGTH_SHORT).show();
        }
        Toast.makeText(this, "service oncreated!", Toast.LENGTH_SHORT).show();
    }

    /**
     * just in case that android os kill this service, just store current data to pref in advance.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        saveCurrentData();
        Log.v(TAG, "onLowMemory mRunning=" + mRunning + ", COUNT=" + COUNT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int res = super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "onStartCommand intent=" + intent+", mIntent="+mIntent);
        mIntent = intent;
        if (intent == null) {
            Log.v(TAG, "COUNT="+COUNT);
            restorePreviousData();
            Toast.makeText(this, "restored!", Toast.LENGTH_SHORT).show();
        }
        return Service.START_STICKY;
    }

    private void restorePreviousData() {
        SharedPreferences prefdefault = PreferenceManager.getDefaultSharedPreferences(WalkCheckerService.this);
        mRunning = prefdefault.getBoolean("Running", false);
        Log.v(TAG, "restorePreviousData mRunning=" + mRunning + ", COUNT=" + COUNT);
        if (mRunning) {
            COUNT = prefdefault.getInt("count", 0);
            Log.v(TAG, "restorePreviousData COUNT=" + COUNT);
            SharedPreferences pref = getSharedPreferences("myPref", 0);
            SharedPreferences.Editor edit = pref.edit();
            edit.putBoolean("Running", false);
            edit.commit();
        }
    }

    private void saveCurrentData() {
        SharedPreferences pref = getSharedPreferences("myPref", 0);
        SharedPreferences.Editor edit = pref.edit();
        edit.putLong("count", COUNT);
        edit.putBoolean("Running", mRunning);
        edit.commit();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.v(TAG, "onTaskRemoved COUNT="+COUNT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }

    /**
     * judge if this is a step or not
     * @param event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            long curTime = System.currentTimeMillis();
            long gapTime = curTime - lastTime;
            Log.v(TAG, "onSensorChanged gapTime=" + gapTime);

            if (gapTime > 100) {
                lastTime = curTime;
                x = event.values[SensorManager.DATA_X];
                y = event.values[SensorManager.DATA_Y];
                z = event.values[SensorManager.DATA_Z];

                speed = Math.abs(x + y + z - lastX - lastY - lastZ) / gapTime * 10000;
                Log.v(TAG, "onSensorChanged speed=" + speed);
                if (speed > SHAKE_THRESHOLD) {
                    Intent walk = new Intent(Const.ACTION_COUNT_NOTIFY);
                    COUNT++;
                    mIntent.putExtra("count", COUNT);
                    mVibe.vibrate(100);
                    Log.v(TAG, "onSensorChanged notify count=" + COUNT + ", mDisplayState=" + mDisplayState);

                    // deliver data to display clients
                    if (mDisplayState == 1) {
                        walk.putExtra("count", COUNT + "");
                        LocalBroadcastManager.getInstance(this).sendBroadcast(walk);
                    } else if (mDisplayState == 2) {
                        TextView count = (TextView) mView.findViewById(R.id.mini_tv1);
                        count.setText(COUNT+"");
                        TextView addr = (TextView) mView.findViewById(R.id.mini_tv2);
                        addr.setText(COUNT*58/100+""+"m");
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
        if (mRunning) {
            saveCurrentData();
            Log.v(TAG, "onDestroy mRunning=" + mRunning + ", COUNT=" + COUNT);
        }
        if (mSensor != null)
            mSensor.unregisterListener(this);
    }

    /**
     * for drag and drop
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
        //LocationListener을 이용해서 위치정보가 업데이트 되었을때 동작 구현
        public void onLocationChanged(Location loc) {
            Log.v(TAG, "Location changed : Lat" + loc.getLatitude() + "Lng: " + loc.getLongitude());
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
     * Http api
     * @param lat
     * @param lng
     */
    void executeHttp(String lat, String lng){

        // make url
        String latlng = lat+","+lng;//latlng = "127.1052133,37.3595316";
        Uri.Builder builder = new Uri.Builder();
        try {
            builder.scheme("https").encodedAuthority("openapi.naver.com").appendEncodedPath("v1").appendEncodedPath("map").appendEncodedPath("reversegeocode")
                    .appendQueryParameter("encoding", "utf-8").appendQueryParameter("coord", "latlng")
                    .appendQueryParameter("output", URLEncoder.encode("json", "utf-8")).appendQueryParameter("query", latlng);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String url = builder.build().toString();
        Log.v(TAG, "excuteHttp url="+url);

        // create http engine
        HttpAsyncTask httpEngine = new HttpAsyncTask(new HttpAsyncTask.OnDataLoadedListener() {
            @Override
            public void onDataLoaded(int resCode, String result) {
                Log.v(TAG, "resCode=" + resCode + ", result=" + result);
                if(result == null) {
                    Toast.makeText(WalkCheckerService.this, "result null", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(resCode >= HttpURLConnection.HTTP_BAD_REQUEST){
                    try {
                        JSONObject root = new JSONObject(result);
                        String message = root.getString("errorMessage");
                        ADDR = message;
                        Toast.makeText(WalkCheckerService.this, message, Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else{
                    JsonUtil json = new JsonUtil(WalkCheckerService.this, result);
                    String addr = json.getAddr();
                    Log.v(TAG, "addr="+addr);
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

    /**
     * local br receiver for comm among components
     */
    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "action=" + action);

            if (action.equals(Const.ACTION_ACTIVITY_ONRESUME)) {

                if (mDisplayState == 2) {
                    wm.removeView(mView);
                }

                //다른루틴으로..restorePreviousData();
                Log.v(TAG, "mRunning=" + mRunning + ", COUNT=" + COUNT);

                Intent i = new Intent(Const.ACTION_INIT_ACTIVITY);
                i.putExtra("Running", mRunning);
                i.putExtra("count", COUNT + "");
                i.putExtra("addr", ADDR);
                LocalBroadcastManager.getInstance(WalkCheckerService.this).sendBroadcast(i);
                mDisplayState = 1;
            } else if (action.equals(Const.ACTION_ACTIVITY_ONSTOP)) {
                if (mRunning) {
                    mDisplayState = 2;
                    LayoutInflater mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    mView = mInflater.inflate(R.layout.mini, null);
                    TextView count = (TextView) mView.findViewById(R.id.mini_tv1);
                    count.setText(COUNT + "");
                    TextView addr = (TextView) mView.findViewById(R.id.mini_tv2);
                    addr.setText(COUNT*58/100+""+"m");
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
                            , PixelFormat.OPAQUE);
                    wm.addView(mView, mParams);
                }
            } else if (action.equals(Const.ACTION_CHECKING_START)) {
                mDisplayState = 1;// for the first time, it means no service started yet
                Date today = new Date();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String date = sdf.format(today);
                Log.v(TAG, "date="+date);
                DATE = date;
                if (mAccelerometer != null) {
                    mSensor.registerListener(WalkCheckerService.this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
                    mRunning = true;
                }
                boolean isGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                Log.v(TAG, "isGPSEnabled=" + isGPSEnabled + ", isNetworkEnabled=" + isNetworkEnabled);
                if (isGPSEnabled && isNetworkEnabled) {

                    //선택된 프로바이더를 사용해 위치정보를 업데이트
                    if (ActivityCompat.checkSelfPermission(WalkCheckerService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(WalkCheckerService.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 10, mLocationListener);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 500, 10, mLocationListener);
                } else {
                    Toast.makeText(WalkCheckerService.this, "turn on gps", Toast.LENGTH_SHORT).show();
                }

            }else if(action.equals(Const.ACTION_CHECKING_STOP)){
                if(mSensor != null) {
                    Log.v(TAG, "sensor unregi..");
                    mSensor.unregisterListener(WalkCheckerService.this);
                    mRunning = false;
                }
                mLocationManager.removeUpdates(mLocationListener);
            }
        }
    }
}
