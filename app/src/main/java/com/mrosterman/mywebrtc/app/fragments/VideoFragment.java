package com.mrosterman.mywebrtc.app.fragments;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.mrosterman.mywebrtc.app.MainActivity;
import com.mrosterman.mywebrtc.app.R;
import com.mrosterman.mywebrtc.app.WebRTCApp;
import org.webrtc.VideoRendererGui;

/**
 * Created by costerman on 1/26/15.
 */
public class VideoFragment extends Fragment {

    public static final String ARG_ROOM_ID = "room_id";
    private GLSurfaceView mGLSurfaceView;
    private Toast mToast;
    private static final String TAG = "VideoFragment";
    private static VideoFragment mInstance;

    public VideoFragment(){
    }

    public static VideoFragment getInstance(){
        if(mInstance == null){
            mInstance = new VideoFragment();
        }
        return mInstance;
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(getArguments().getInt(MainActivity.ARG_SECTION_NUMBER));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View rootView = inflater.inflate(R.layout.fragment_video_view, container, false);

        if(!WebRTCApp.getInstance().isConnected()) {
            String bitrateValue = getString(R.string.pref_startbitratevalue_default);
            WebRTCApp.getInstance().getPeerConnectionManager().setStartBitrate(Integer.parseInt(bitrateValue));
            mGLSurfaceView = (GLSurfaceView) rootView.findViewById(R.id.glview);

            //GET INTENT EXTRA ROOM NUMBER

            VideoRendererGui.setView(mGLSurfaceView, new Runnable() {
                @Override
                public void run() {
                    //do nothing here, too soon to create peer connection factory.
                }
            });

            WebRTCApp.getInstance()
                    .getPeerConnectionManager()
                    .setRemoteRenderer(VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false));

            WebRTCApp.getInstance()
                    .getPeerConnectionManager()
                    .setLocalRenderer(VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true));
        }

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final String roomId = this.getArguments().getString(ARG_ROOM_ID);
        final boolean connected = WebRTCApp.getInstance().isConnected();

        if(!connected && roomId != null && roomId.length() > 0) {
            WebRTCApp.getInstance().getPeerConnectionManager().setActivity(getActivity());
            WebRTCApp.getInstance().getPeerConnectionManager().joinRoom(roomId);
        } else {
            logAndToast(TAG, "No room number has been selected, please select a room.");
        }
    }



    public void logAndToast(String tag, String msg){
        Log.d(tag, msg);
        if(mToast != null){
            mToast.cancel();
        }
        mToast = Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT);
        mToast.show();
    }
}
