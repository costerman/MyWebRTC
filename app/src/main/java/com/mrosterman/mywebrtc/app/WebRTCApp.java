package com.mrosterman.mywebrtc.app;

import android.app.Application;
import android.content.Context;
import android.widget.Toast;
import com.firebase.client.Firebase;

/**
 * Created by costerman on 1/26/15.
 */
public class WebRTCApp extends Application {

    private SignalingManager mSignalingManager;
    private Firebase mBaseDelFuego;
    private Toast mToast;
    public final static String SESSION_DATA_PARCEL = "com.mrosterman.mywebrtc.app.parcel.sessiondata";
    private static Context mAppContext;
    private boolean mConnectedToRoom = false;
    private String mRoomId;


    //region - Singleton Instance
    private static WebRTCApp mInstance;
    public WebRTCApp(){
    }
    public static WebRTCApp getInstance(){
        return mInstance;
    }
    //endregion


    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        mInstance.initInstance();
    }

    protected void initInstance(){
        //do initialization logic here
        mAppContext = this.getApplicationContext();
        Firebase.setAndroidContext(mAppContext);
        mBaseDelFuego = new Firebase("https://webrtc-cloud.firebaseio.com");
        mSignalingManager = new SignalingManager(mAppContext, mBaseDelFuego);
    }

    public Firebase getFirebaseRef(){
        return mBaseDelFuego;
    }

    public SignalingManager getPeerConnectionManager(){
        return mSignalingManager;
    }

    public void setRoomId(String roomId){
        mRoomId = roomId;
    }

    public String getRoomId(){
        return mRoomId;
    }

    public void setConnected(boolean connected){
        mConnectedToRoom = connected;
    }

    public boolean isConnected(){
        return mConnectedToRoom;
    }
}
