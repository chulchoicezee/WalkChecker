package com.chulgee.walkchecker.http;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by chulchoice on 2016-05-21.
 */
public class HttpAsyncTask extends AsyncTask<String, Void, String> implements IHttp {

    private static final String TAG = "HttpAsyncTask";
    private ConnType mConnType;
    private ResponseType mResponseType;
    private int mTimeout, mReadTimeout;
    private OnDataLoadedListener mListener;
    private String mRequestBody;
    private int mResponseCode;
    private Bundle mRequestHeader;

    public interface OnDataLoadedListener{
        void onDataLoaded(int responseCode, String result);
    }

    public HttpAsyncTask(OnDataLoadedListener listener){
        mTimeout = 30000;
        mListener = listener;
    }

    public void setRequestHeader(Bundle $bd){
        mRequestHeader = $bd;
    }

    public void setConnTimeout(int timeout){
        mTimeout = timeout;
    }

    public void setReadTimeout(int timeout){
        mReadTimeout = timeout;
    }

    public void setResponseType(ResponseType type){
        mResponseType = type;
    }

    public void setConnType(ConnType type){
        mConnType = type;
    }

    public void setRequestBody(String body){
        mRequestBody = body;
    }

    @Override
    protected String doInBackground(String... params) {

        String response = null;

        if(params[0].startsWith("http")){
            Log.v(TAG, "http: start");
            response = handleHttp(params[0]);
        }else if(params[0].startsWith("https")){
            Log.v(TAG, "https: start");
            response = handleHttps(params[0]);
        }

        return response;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        mListener.onDataLoaded(mResponseCode, result);
    }

    private void putStreamRequestBody(HttpURLConnection conn){

        OutputStream os = null;
        BufferedWriter bw = null;
        conn.setDoOutput(true);
        if(mRequestBody != null){
            try {
                os = conn.getOutputStream();
                bw = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                bw.write(mRequestBody);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String handleHttp(String $url){

        URL url;
        String response = null;
        BufferedReader br = null;

        try {
            url = new URL($url);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            // set timeouts
            conn.setReadTimeout(mReadTimeout);
            conn.setConnectTimeout(mTimeout);
            //set requested header info
            for(String key : mRequestHeader.keySet()){
                String value = mRequestHeader.getString(key);
                Log.v(TAG, "key=" + key + ", value=" + value);
                conn.setRequestProperty(key, value);
            }
            conn.setDoInput(true);
            switch(mConnType){
                case GET:
                    conn.setRequestMethod(ConnType.GET.getName());
                    if(mRequestBody != null){
                        setConnType(IHttp.ConnType.POST);
                        putStreamRequestBody(conn);
                    }
                    break;
                case POST:
                    conn.setRequestMethod(ConnType.POST.getName());
                    conn.setDoOutput(true);
                    putStreamRequestBody(conn);
                    break;
                case PUT:
                    conn.setRequestMethod(ConnType.PUT.getName());
                    conn.setDoOutput(true);
                    putStreamRequestBody(conn);
                    break;
                case DELETE:
                    conn.setRequestMethod(ConnType.DELETE.getName());
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded" );
                    break;
            }

            // get response and data
            mResponseCode = conn.getResponseCode();
            Log.v(TAG, "mResponseCode=" + mResponseCode);
            InputStream is;
            if(mResponseCode >= HttpURLConnection.HTTP_BAD_REQUEST){
                is = conn.getErrorStream();
            }else{
                is= conn.getInputStream();
            }
            br = new BufferedReader(new InputStreamReader(is));
            String line=null;
            StringBuffer lines = new StringBuffer();
            while((line = br.readLine()) != null){
                lines.append(line);
            }
            response = lines.toString();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(br!=null) br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return response;
    }

    private String handleHttps(String $url){
        URL url;
        String response = null;
        BufferedReader br = null;

        try{
            url = new URL($url);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            // set timeouts
            conn.setReadTimeout(mReadTimeout);
            conn.setConnectTimeout(mTimeout);
            //set requested header info
            for(String key : mRequestHeader.keySet()){
                String value = mRequestHeader.getString(key);
                Log.v(TAG, "key=" + key + ", value=" + value);
                conn.setRequestProperty(key, value);
            }
            conn.setDoOutput(true);
            conn.setRequestMethod(ConnType.POST.getName());
            putStreamRequestBody(conn);
            // get response and data
            mResponseCode = conn.getResponseCode();
            Log.v(TAG, "mResponseCode=" + mResponseCode);
            InputStream is;
            if(mResponseCode >= HttpURLConnection.HTTP_BAD_REQUEST){
                is = conn.getErrorStream();
            }else{
                is= conn.getInputStream();
            }
            br = new BufferedReader(new InputStreamReader(is));
            String line=null;
            StringBuffer lines = new StringBuffer();
            while((line = br.readLine()) != null){
                lines.append(line);
            }
            response = lines.toString();
        } catch (IOException x) {
            x.printStackTrace();
        }
        return response;
    }
}
