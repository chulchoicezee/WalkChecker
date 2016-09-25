package com.chulgee.walkchecker.http;

import android.os.Bundle;

/**
 * Created by chulchoice on 2016-05-21.
 */
public interface IHttp {

    public void setRequestHeader(Bundle bd);

    public void setConnTimeout(int timeout);

    public void setReadTimeout(int timeout);

    public void setResponseType(IHttp.ResponseType type);

    public void setConnType(IHttp.ConnType type);

    public void setRequestBody(String body);

    public enum ConnType{
        GET("GET"),
        POST("POST"),
        PUT("PUT"),
        DELETE("DELETE");

        private String name;
        ConnType(String name){
            this.name = name;
        }
        String getName(){
            return this.name;
        }
    }

    public enum ResponseType{
        HTMLL("text/html"),
        XML("application/xml"),
        JSON("application/json");

        private String name;
        ResponseType(String name){
            this.name = name;
        }
        String getName(){
            return this.name;
        }
    }
}
