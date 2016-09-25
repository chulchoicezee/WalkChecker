package com.chulgee.walkchecker.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.chulgee.walkchecker.WalkCheckerActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by chulchoice on 2016-09-24.
 */
public class JsonUtil {

    private static final String TAG = "JsonUtil";
    Context context;
    JSONObject root;
    JSONObject o1;
    int count;
    JSONArray items;
    String addr;

    public JsonUtil(Context $context, String data){
        context = $context;
        handleJson(data);
    }

    public void handleJson(String result){
        try {
            root = new JSONObject(result);
            Log.v(TAG, "root="+root);
            o1 = root.getJSONObject("result");
            String strCount = o1.getString("total");
            count = Integer.valueOf(strCount);
            if( count > 0){
                items = o1.getJSONArray("items");
                if(items != null){
                    for(int i=0; i<items.length(); i++){
                        JSONObject item = items.getJSONObject(i);
                        addr = item.getString("address");
                        Log.v(TAG, "handleJson addr="+addr);
                        if(addr != null && !addr.isEmpty())
                            break;
                    }
                }
            }else{
                Toast.makeText(context, "No result", Toast.LENGTH_SHORT).show();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getAddr(){
        return addr;
    }
}
