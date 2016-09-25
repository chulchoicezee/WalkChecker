package com.chulgee.walkchecker.util;

import android.content.Context;
import android.location.LocationManager;

/**
 * Created by chulchoice on 2016-09-24.
 */
public class Location {

    public Location(Context context){
        LocationManager lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

    }
}
