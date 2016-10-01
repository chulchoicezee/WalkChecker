package com.chulgee.walkchecker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.chulgee.walkchecker.adapter.MyPagerAdapter;
import com.chulgee.walkchecker.util.Const;

public class WalkCheckerActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "WalkCheckerActivity";

    // view vars
    private ViewPager mPager;
    private Button btn_one;
    private Button btn_two;

    // info vars
    private boolean mRunning;
    private String mCount;
    private String mDistance;
    private String mDate;
    private String mAddr;
    public boolean getRunning(){    return mRunning;    }
    public void setRunning(boolean run){    mRunning = run;    }

    // service
    WalkCheckerService mService;
    boolean mBound;

    // permission for overlay
    private boolean canOverlay;
    private boolean canGPS;

    // local br and listener
    private LocalBroadcastReceiver mLocalReceiver = new LocalBroadcastReceiver();
    private LocationManager mLocationManager = null;

    /**
     * handle some display-related tasks
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            Log.v(TAG, "bundle=" + b);
            switch (msg.what) {
                case Const.PARAM_ACT_UPDATE_ALL:
                    updateAll(b);
                    break;
                case Const.PARAM_ACT_UPDATE_COUNT_DISTANCE:
                    updateCountandDistance(b);
                    break;
                case Const.PARAM_ACT_UPDATE_LOCATION:
                    updateLocation(b);
                    break;
                case Const.PARAM_ACT_CHECK_PERMISSION:
                    if(canOverlayWindow(WalkCheckerActivity.this)){
                        checkPermissionForGps();
                    }
                    break;
                case Const.PARAM_ACT_REMOVE_MINI_VIEW:
                    if(canOverlay) {
                        Intent i = new Intent(Const.ACTION_ACTIVITY_ONRESUME);
                        LocalBroadcastManager.getInstance(WalkCheckerActivity.this).sendBroadcast(i);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    // Service connection
    ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WalkCheckerService.LocalBinder binder = (WalkCheckerService.LocalBinder)service;
            Log.v(WalkCheckerActivity.TAG, "onServiceConnected canOverlay="+canOverlay);
            mService = binder.getService();
            mBound = true;
            // to display activity for the first time when service is created
            scheduleUpdateAll();
            // to handle mini view. just to notify onResume to service, for the first time when service is created
            mHandler.sendEmptyMessage(Const.PARAM_ACT_REMOVE_MINI_VIEW);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(WalkCheckerActivity.TAG, "onServiceDisconnected");
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.v(TAG, "onCreate");

        // init views and adapter
        initViews();

        // register local broadcast
        registerLocalBr();

        // start service to get service running in background
        Intent i = new Intent(WalkCheckerActivity.this, WalkCheckerService.class);
        startService(i);

        // bind service to get info from service
        bindService(i, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume canOverlay="+canOverlay);

        // to display activity for the first time when service is created
        if(mBound){
            scheduleUpdateAll();
        }

        // check permission
        if(!canOverlay || !canGPS)
            mHandler.sendEmptyMessage(Const.PARAM_ACT_CHECK_PERMISSION);

        // to handle mini view. just to notify onResume to service
        if(canOverlay) {
            Intent i = new Intent(Const.ACTION_ACTIVITY_ONRESUME);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");

        // to handle mini view. just to notify onStop to service
        if(canOverlay) {
            Intent i = new Intent(Const.ACTION_ACTIVITY_ONSTOP);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");

        // unregister receiver and binded service
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
        if(mBound)
            unbindService(mServiceConnection);
    }

    /**
     * receiver for system overlay
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.v(TAG, "requestCode="+requestCode+", resultCode="+resultCode);
        if(requestCode == 1){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {
                canOverlay = true;
                Toast.makeText(this, "Overlay available", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Overlay not available", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * receiver for gps permission
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode){
            case 1:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    canGPS = true;
                    Toast.makeText(WalkCheckerActivity.this, "GPS available", Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(WalkCheckerActivity.this, "Please turn on GPS next time.", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.btn_one:
                mPager.setCurrentItem(0);
                break;
            case R.id.btn_two:
                mPager.setCurrentItem(1);
                break;
            default:
                break;
        }
    }

    /**
     * local br receiver for communication between activity and service.
     */
    private class LocalBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "action="+action);

            Message message = new Message();
            Bundle bd = new Bundle();
            String addr = null;

            if(action.equals(Const.ACTION_COUNT_NOTIFY)){

                // update count and distance
                message.what = Const.PARAM_ACT_UPDATE_COUNT_DISTANCE;
                long count = intent.getLongExtra("count", 0);
                bd.putString("count", count+"");
                long distance = intent.getLongExtra("distance", 0);
                bd.putString("distance", distance+"m");
            }else if(action.equals(Const.ACTION_ADDR_NOTIFY)){

                // update location
                message.what = Const.PARAM_ACT_UPDATE_LOCATION;
                addr = intent.getStringExtra("addr");
                bd.putString("addr", addr);
            }
            message.setData(bd);
            mHandler.sendMessage(message);
        }
    }

    /***************************************** local apis ****************************************/

    private void initViews(){
        btn_one = (Button) findViewById(R.id.btn_one);
        btn_two = (Button) findViewById(R.id.btn_two);
        btn_one.setOnClickListener(this);
        btn_two.setOnClickListener(this);

        //bind adapter
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(new MyPagerAdapter(this));
    }

    private void registerLocalBr(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(Const.ACTION_COUNT_NOTIFY);
        filter.addAction(Const.ACTION_ADDR_NOTIFY);
        filter.addAction(Const.ACTION_INIT_ACTIVITY);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, filter);
    }

    public boolean canOverlayWindow(Context context) {
        boolean ret = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            canOverlay = ret = false;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1);
        } else {
            canOverlay = ret = true;
        }
        Log.v(TAG, "canOverlayWindow canOverlay="+canOverlay);
        return ret;
    }

    private boolean checkPermissionForGps(){
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean ret = false;
        boolean isGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        Log.v(TAG, "isGPSEnabled="+isGPSEnabled+", isNetworkEnabled="+isNetworkEnabled);
        // request sequence for getting gps permission
        if (isGPSEnabled && isNetworkEnabled) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)){
                    Log.v(TAG, "under explanation");
                    new AlertDialog.Builder(this)
                            .setMessage("To use this application, you should agree with getting permission on GPS. Please turn on GPS permission")
                            .setPositiveButton("Move", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    ActivityCompat.requestPermissions(WalkCheckerActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    ActivityCompat.requestPermissions(WalkCheckerActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                                }
                            }).show();
                }else{
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            }else
                canGPS = ret = true;
        }else{
            Toast.makeText(WalkCheckerActivity.this, "Please turn on GPS", Toast.LENGTH_SHORT).show();
        }

        return ret;
    }

    private void updateAll(Bundle b){
        String count = b.getString("count");
        String addr = b.getString("addr");
        String distance = b.getString("distance");
        if (mPager != null)
            ((MyPagerAdapter) (mPager.getAdapter())).initDisplay(count, addr, distance);
    }

    private void updateCountandDistance(Bundle b){
        String count = b.getString("count");
        String distance = b.getString("distance");
        if (mPager != null) {
            ((MyPagerAdapter) (mPager.getAdapter())).setCount(count);
            ((MyPagerAdapter) (mPager.getAdapter())).setDistance(distance);
        }
    }

    private void updateLocation(Bundle b){
        String addr = b.getString("addr");
        if (mPager != null)
            ((MyPagerAdapter) (mPager.getAdapter())).setAddr(addr);
    }

    private void scheduleUpdateAll(){
        mRunning = mService.getRunning();
        mCount = mService.getCount()+"";
        mDistance = mService.getDistance()+"m";
        mDate = mService.getDATE();
        mAddr = mService.getADDR();
        Message m = new Message();
        m.what = Const.PARAM_ACT_UPDATE_ALL;
        Bundle bd = new Bundle();
        bd.putString("count", mCount);
        bd.putString("distance", mDistance);
        bd.putBoolean("Running", mRunning);
        bd.putString("date", mDate);
        bd.putString("addr", mAddr);
        m.setData(bd);
        Log.v(WalkCheckerActivity.TAG, "onServiceConnected mCount="+mCount+", mDistance="+mDistance+", mRunning="+mRunning);

        mHandler.sendMessage(m);
    }
}
