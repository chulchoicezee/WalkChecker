package com.chulgee.walkchecker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.chulgee.walkchecker.adapter.AbstractCurAdapter;
import com.chulgee.walkchecker.util.Const;

public class WalkCheckerActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "WalkCheckerActivity";
    // view vars
    private ViewPager mPager;
    private Button btn_one;
    private Button btn_two;
    // walking stared or not
    private boolean mRunning;
    // permission for overlay
    private boolean canOverlay;
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
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init views and adapter
        initViews();

        // register local broadcast
        registerLocalBr();

        // check permission
        checkPermissionForGps();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode){
            case 1:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(WalkCheckerActivity.this, "GPS available", Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(WalkCheckerActivity.this, "Please turn on GPS next time", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.v(TAG, "requestCode="+requestCode+", resultCode="+resultCode);
        if(requestCode == 1){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {
                canOverlay = true;
                Toast.makeText(this, "Overlay available", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(WalkCheckerActivity.this, WalkCheckerService.class);
                startService(i);
            } else {
                Toast.makeText(this, "Overlay not available", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(canOverlayWindow(this)) {
            Intent i = new Intent(Const.ACTION_ACTIVITY_ONRESUME);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(canOverlay) {
            Intent i = new Intent(Const.ACTION_ACTIVITY_ONSTOP);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
    }

    /**
     * view pager adapter
     */
    class MyAdapter extends PagerAdapter{
        LayoutInflater inflater;
        // for view cache
        View pane1 = null;
        View pane2 = null;

        public MyAdapter(Context c){
            super();
            inflater = LayoutInflater.from(c);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View v = null;
            ViewCache vc = new ViewCache();

            if(position == 0){
                if(pane1 == null){
                    v = inflater.inflate(R.layout.inflate_one, null);
                    vc.count = (TextView)v.findViewById(R.id.tv_count);
                    vc.distance = (TextView)v.findViewById(R.id.tv_distance);
                    vc.location = (TextView)v.findViewById(R.id.tv_location);
                    vc.btn_start = (Button)v.findViewById(R.id.btn_start);
                    vc.btn_start.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Button btn = (Button)v;
                            String action;
                            if(mRunning){
                                action = Const.ACTION_WALKING_STOP;
                                mRunning = false;
                            }else{
                                action = Const.ACTION_WALKING_START;
                                mRunning = true;
                            }
                            btn.setText(mRunning?"STOP":"START");
                            // deliver start or stop to service
                            Intent i = new Intent(action);
                            LocalBroadcastManager.getInstance(WalkCheckerActivity.this).sendBroadcast(i);
                        }
                    });
                    v.setTag(vc);
                    pane1 = v;
                }else // view cache exists so use it.
                    v = pane1;
            }else if(position == 1){
                if(pane2 == null){
                    v = inflater.inflate(R.layout.inflate_two, null);
                    vc.lv = (ListView)v.findViewById(R.id.list);
                    String[] from = new String[]{WalkCheckerProvider.DbHelper.COLUMN_DATE, WalkCheckerProvider.DbHelper.COLUMN_COUNT};
                    Cursor c = getContentResolver().query(Uri.parse(Const.CONTENT_URI), from, null, null, null);
                    try{
                        if(c != null && c.moveToFirst()){
                            int[] to = new int[]{R.id.list_item_tv1,R.id.list_item_tv2};
                            ListAdapterAbstract listAdapter = new ListAdapterAbstract(c);
                            vc.lv.setAdapter(listAdapter);
                        }
                    }catch (Exception e){e.printStackTrace();}
                    pane2 = v;
                }else // view cache exists so use it.
                    v = pane2;
            }

            mPager.addView(v,0);
            return v;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            mPager.removeView((View)object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        public void setCount(String str){
            ViewCache vc = (ViewCache)pane1.getTag();
            vc.count.setText(str);
        }

        public void setDistance(String str){
            ViewCache vc = (ViewCache)pane1.getTag();
            vc.distance.setText(str);
        }

        public void setAddr(String str){
            ViewCache vc = (ViewCache)pane1.getTag();
            if(str == null || str.isEmpty()) str = "??? ??? ????.";
            vc.location.setText(str);
        }

        public void initDisplay(String count, String addr, String distance){
            ViewCache vc = (ViewCache)pane1.getTag();
            String str = null;
            if(mRunning)
                str = "STOP";
            else
                str = "START";
            vc.btn_start.setText(str);
            vc.count.setText(count);
            vc.location.setText(addr);
            vc.distance.setText(distance);
        }

        private class ViewCache{
            TextView count;
            TextView distance;
            TextView location;
            TextView date;
            Button btn_start;
            ListView lv;
        }

        /**
         * implemented my own AbstractCurAdapter for easy use
         * cursor adapter class for history
         */
        public class ListAdapterAbstract extends AbstractCurAdapter {

            public ListAdapterAbstract(Cursor $c){
                super($c);
            }

            // called just one time, applied cache mechanism
            @Override
            public View getRowView() {
                View v = getLayoutInflater().inflate(R.layout.list_item, null);
                ViewCache vc = new ViewCache();
                vc.date = (TextView)v.findViewById(R.id.list_item_tv1);
                vc.count = (TextView)v.findViewById(R.id.list_item_tv2);
                v.setTag(vc);
                return v;
            }

            @Override
            public void setRow(final Cursor c, int idx, View v, ViewGroup viewGroup) {
                ViewCache vc = (ViewCache) v.getTag();
                vc.date.setText(c.getString(c.getColumnIndex(WalkCheckerProvider.DbHelper.COLUMN_DATE)));
                vc.count.setText(c.getString(c.getColumnIndex(WalkCheckerProvider.DbHelper.COLUMN_COUNT)));
            }
        }
    }

    /**
     * local br receiver for communication between activity and service
     */
    private class LocalBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "action="+action);

            Message message = new Message();
            Bundle bd = new Bundle();
            String count = null;
            String addr = null;
            String distance = null;

            if(action.equals(Const.ACTION_INIT_ACTIVITY)){
                // update all views in activity
                message.what = Const.PARAM_ACT_UPDATE_ALL;
                mRunning = intent.getBooleanExtra("Running", false);
                count = intent.getStringExtra("count");
                bd.putString("count", count);
                addr = intent.getStringExtra("addr");
                bd.putString("addr", addr);
                distance = intent.getStringExtra("distance");
                bd.putString("distance", Long.valueOf(count)*58/100+""+"m");
            }else if(action.equals(Const.ACTION_COUNT_NOTIFY)){
                // update count and distance
                message.what = Const.PARAM_ACT_UPDATE_COUNT_DISTANCE;
                count = intent.getStringExtra("count");
                bd.putString("count", count);
                distance = intent.getStringExtra("distance");
                bd.putString("distance", Long.valueOf(count)*58/100+""+"m");
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
        mPager.setAdapter(new MyAdapter(getApplicationContext()));
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
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1);
        } else {
            //start service
            Intent i = new Intent(WalkCheckerActivity.this, WalkCheckerService.class);
            startService(i);
            canOverlay = ret = true;
        }
        return ret;
    }

    private void checkPermissionForGps(){
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

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
                return;
            }
        }else{
            Toast.makeText(WalkCheckerActivity.this, "Please turn on GPS", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAll(Bundle b){
        String count = b.getString("count");
        String addr = b.getString("addr");
        String distance = b.getString("distance");
        if (mPager != null)
            ((MyAdapter) (mPager.getAdapter())).initDisplay(count, addr, distance);
    }

    private void updateCountandDistance(Bundle b){
        String count = b.getString("count");
        String distance = b.getString("distance");
        if (mPager != null) {
            ((MyAdapter) (mPager.getAdapter())).setCount(count);
            ((MyAdapter) (mPager.getAdapter())).setDistance(distance);
        }
    }

    private void updateLocation(Bundle b){
        String addr = b.getString("addr");
        if (mPager != null)
            ((MyAdapter) (mPager.getAdapter())).setAddr(addr);
    }

}
