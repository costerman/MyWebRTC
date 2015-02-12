package com.mrosterman.mywebrtc.app;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
* Created by costerman on 2/5/15.
*/
public class SessionModel implements Parcelable {

    public String id;

    public LinkedHashMap<String, Object> sessionData;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(sessionData.size());
        for(Map.Entry<String, Object> entry : sessionData.entrySet()){
            dest.writeString(entry.getKey());
            dest.writeValue(entry.getValue());
        }
    }
}
