package com.mrosterman.mywebrtc.app.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by costerman on 2/18/15.
 */
public class DateUtil {

    public static long getDateMilliseconds(){
        Date date = new Date();
        return date.getTime();
    }
}
