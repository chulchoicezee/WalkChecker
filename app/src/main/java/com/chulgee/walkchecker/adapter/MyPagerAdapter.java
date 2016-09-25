package com.chulgee.walkchecker.adapter;

/**
 * Created by chulchoice on 2016-09-25.
 */

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.chulgee.walkchecker.R;
import com.chulgee.walkchecker.WalkCheckerActivity;
import com.chulgee.walkchecker.WalkCheckerProvider;
import com.chulgee.walkchecker.WalkCheckerService;
import com.chulgee.walkchecker.util.Const;

import java.util.zip.Inflater;

/**
 * view pager adapter
 */
public class MyPagerAdapter extends PagerAdapter {
    Context context;
    WalkCheckerActivity act;
    LayoutInflater inflater;

    // for view cache
    View pane1 = null;
    View pane2 = null;

    public MyPagerAdapter(Context c){
        super();
        context = c;
        act = (WalkCheckerActivity)c;
        inflater = LayoutInflater.from(c);
    }

    @Override
    public int getCount() {
        return 2;
    }

    /**
     * applied cache algorism
     * @param container
     * @param position
     * @return
     */
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
                        if(act.getRunning()){
                            action = Const.ACTION_WALKING_STOP;
                            act.setRunning(false);
                        }else{
                            action = Const.ACTION_WALKING_START;
                            act.setRunning(true);
                        }
                        btn.setText(act.getRunning()?"STOP":"START");
                        // deliver start or stop to service
                        Intent i = new Intent(action);
                        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
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
                Cursor c = context.getContentResolver().query(Uri.parse(Const.CONTENT_URI), from, null, null, null);
                try{
                    if(c != null && c.moveToFirst()){
                        int[] to = new int[]{R.id.list_item_tv1,R.id.list_item_tv2};
                        ListAdapter listAdapter = new ListAdapter(c);
                        vc.lv.setAdapter(listAdapter);
                    }
                }catch (Exception e){e.printStackTrace();}
                pane2 = v;
            }else // view cache exists so use it.
                v = pane2;
        }

        container.addView(v,0);
        return v;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View)object);
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
        if(str == null || str.isEmpty()) str = "확인된 주소가 없습니다.";
        vc.location.setText(str);
    }

    public void initDisplay(String count, String addr, String distance){
        ViewCache vc = (ViewCache)pane1.getTag();
        String str = null;
        if(act.getRunning())
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
    public class ListAdapter extends AbstractCurAdapter {
        LayoutInflater inflater;

        public ListAdapter(Cursor $c){
            super($c);
            inflater = LayoutInflater.from(context);
        }

        // called just one time, applied cache mechanism
        @Override
        public View getRowView() {
            View v = inflater.inflate(R.layout.list_item, null);
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
