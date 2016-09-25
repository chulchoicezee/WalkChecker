package com.chulgee.walkchecker.util;

/**
 * Created by chulchoice on 2016-09-24.
 */
public class Const {

    public static final String CONTENT_URI = "content://com.chulgee.walkchecker.provider";
    public static final String ACTION_ACTIVITY_ONSTOP = "com.chulgee.walkchecker.activity.onstop";
    public static final String ACTION_ACTIVITY_ONRESUME = "com.chulgee.walkchecker.activity.onresume";
    public static final String ACTION_COUNT_NOTIFY = "com.chulgee.walkchecker.count.notify";
    public static final String ACTION_ADDR_NOTIFY = "com.chulgee.walkchecker.address.notify";
    public static final String ACTION_WALKING_START = "com.chulgee.walkchecker.checking.start";
    public static final String ACTION_WALKING_STOP = "com.chulgee.walkchecker.checking.stop";
    public static final String ACTION_INIT_ACTIVITY = "com.chulgee.walkchecker.activity.init";

    public static final int PARAM_DISPLAY_NONE = 0;
    public static final int PARAM_DISPLAY_ACTIVITY = 1;
    public static final int PARAM_DISPLAY_MINIVIEW = 2;

    public static final int PARAM_ACT_UPDATE_ALL = 0;
    public static final int PARAM_ACT_UPDATE_COUNT_DISTANCE = 1;
    public static final int PARAM_ACT_UPDATE_LOCATION = 2;

}
