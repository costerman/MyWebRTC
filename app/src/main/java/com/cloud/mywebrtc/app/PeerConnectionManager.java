package com.cloud.mywebrtc.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.Log;

import com.cloud.mywebrtc.app.events.PeerConnectionEvents;
import com.cloud.mywebrtc.app.events.SignalingEvents;
import com.cloud.mywebrtc.app.util.IdGenerator;
import com.firebase.client.*;

import org.webrtc.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by costerman on 2/10/15.
 */
public class PeerConnectionManager implements
        SignalingEvents,
        PeerConnectionEvents {

    private static final String TAG = "PeerConnectionManager";
    private Firebase mFirebaseRef;
    private Firebase mPeerNodeRef;
    private Firebase mSignalingNodeRef;
    private List<String> mPeers;
    private List<PeerConnection.IceServer> mIceServers;
    private ConcurrentLinkedQueue<IceCandidate> mLocalIceCandidates;
    private SessionDescription mLocalSessionDescription = null;
    private PeerNodeEventListener mPeerNodeEventListener;
    private SignalingNodeEventListener mSignalingNodeEventListener;
    private PeerConnectionClient mPeerConnectionClient;
    private WebRTCAudioManager mAudioManager;
    private Context mAppContext;
    private SignalingParameters mSignalingParameters;
    private boolean mHwCodec = true;
    private int mStartBitrate;
    private VideoRenderer.Callbacks mLocalRenderer;
    private VideoRenderer.Callbacks mRemoteRenderer;
    private Activity mActivity;
    private String mClientId;
    private boolean mIsInitiator;
    private long mPeerNotificationCount = 0;

    //Firebase Field Definitions
    private static final String CREATED_AT = "createdAt";
    private static final String CREATED_BY = "createdBy";
    private static final String PEERS = "peers";
    private static final String SIGNALING_CLIENTS = "signalingClients";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String TYPE = "type";


    public PeerConnectionManager(Context context, Firebase ref){
        mFirebaseRef = ref;
        mSignalingNodeEventListener = new SignalingNodeEventListener();
        mPeerNodeEventListener = new PeerNodeEventListener();
        mPeers = new ArrayList<>();
        mLocalIceCandidates = new ConcurrentLinkedQueue<>();
        mAppContext = context;

        if(mClientId == null){
            mClientId = UUID.randomUUID().toString();
        }

        //TODO: Need to move this to a configuration setting
        //Tims TURN -> PeerConnection.IceServer iceServer = new PeerConnection.IceServer("https://130.211.147.65:1352", "", "");
        mIceServers = new LinkedList<>();
        mIceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        mIceServers.add(new PeerConnection.IceServer("turn:192.158.29.39:3478?transport=udp", "28224511:1379330808", "JZEOEt2V3Qb0y27GRntt2u2PAYA="));
        mIceServers.add(new PeerConnection.IceServer("turn:192.158.29.39:3478?transport=tcp", "28224511:1379330808", "JZEOEt2V3Qb0y27GRntt2u2PAYA="));
    }

    //region - Public Setters
    public void setActivity(Activity activity){
        mActivity = activity;
    }

    public void setStartBitrate(int bitrate){
        mStartBitrate = bitrate;
    }

    public void setLocalRenderer(VideoRenderer.Callbacks localRenderer){
        mLocalRenderer = localRenderer;
    }

    public void setRemoteRenderer(VideoRenderer.Callbacks remoteRenderer){
        mRemoteRenderer = remoteRenderer;
    }
    //endregion

    public String createRoom(String email){
        String roomId = IdGenerator.generateId();
        HashMap<String, Object> signalSession = new HashMap<>();
        mIsInitiator = true;
        signalSession.put(CREATED_AT, new Date().getTime());
        signalSession.put(CREATED_BY, email);

        HashMap<String, Object> session = new HashMap<>();
        session.put(roomId, signalSession);

        try{
            mFirebaseRef.updateChildren(session);
        } catch (Exception ex){
            ex.printStackTrace();
            Log.e(TAG, ex.getMessage());
        }

        return roomId;
    }

    public void joinRoom(String roomId, String email){

        HashMap<String, Object> peers = new HashMap<>();
        peers.put(mClientId, email);

        HashMap<String, Object> peerSignaling = new HashMap<>();
        HashMap<String, Object> listeningOn = new HashMap<>();
        peerSignaling.put(mClientId, listeningOn);

        try{

            mSignalingNodeRef = mFirebaseRef.getRoot().child(roomId).child("signaling");
            mSignalingNodeRef.updateChildren(peerSignaling);
            mSignalingNodeRef.addChildEventListener(mSignalingNodeEventListener);

            mPeerNodeRef = mFirebaseRef.getRoot().child(roomId).child("peers");
            mPeerNodeRef.updateChildren(peers);

            //Retrieve the number of peers currently under the peers node
            mPeerNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mPeerNotificationCount = dataSnapshot.getChildrenCount();
                    Log.d(TAG, String.format("Number of Peers already connected: %d", mPeerNotificationCount));

                    //Start listening to notifications on Peers node
                    mPeerNodeRef.addChildEventListener(mPeerNodeEventListener);
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {

                }
            });



        } catch (Exception ex){
            Log.d(TAG, String.format("endAt Exception: %s", ex.getMessage()));
        }


        //Configure Constraints
        MediaConstraints pcConstraints = new MediaConstraints();
        MediaConstraints videoConstraints = new MediaConstraints();
        MediaConstraints audioConstraints = new MediaConstraints();

        mIsInitiator = true;

        mSignalingParameters = new SignalingParameters(
                mIceServers,
                pcConstraints,
                videoConstraints,
                audioConstraints,
                roomId,
                mClientId,
                mIsInitiator,
                null,
                null);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connect(mSignalingParameters);
            }
        });
    }

    private void connect(final SignalingParameters params){
        mAudioManager = WebRTCAudioManager.create(mAppContext, new Runnable() {
            @Override
            public void run() {
                //Do something here...
            }
        });
        mAudioManager.init();

        final PeerConnectionEvents peerConnectionEvents = this;
        final SignalingEvents signalingEvents = this;
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mPeerConnectionClient == null) {
                    mPeerConnectionClient = new PeerConnectionClient();
                    mPeerConnectionClient.createPeerConnectionFactory(
                            mActivity.getApplicationContext(), mHwCodec, VideoRendererGui.getEGLContext(), peerConnectionEvents);
                }
                if (params != null) {
                    Log.w(TAG, "EGL context is ready after room connection.");

                    mPeerConnectionClient.createPeerConnection(mLocalRenderer, mRemoteRenderer, mSignalingParameters, mStartBitrate);

                    if (mPeerConnectionClient.isHDVideo()) {
                        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }

                    signalingEvents.onChannelOpen();
                    signalingEvents.onConnectedToRoom(params);
                }
            }
        });
    }

    private void sendOffer(){

    }

    private void sendIce(){

    }

    private void onAudioManagerChangedState() {
        // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
        // is active.
    }

    private void logAndToast(String tag, String msg){
        Log.d(tag, msg);
    }

    //region - Signaling Event Callbacks

    @Override
    public void onConnectedToRoom(final SignalingParameters params) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebRTCApp.getInstance().setConnected(true);
            }
        });
    }

    @Override
    public void onChannelOpen() {
        logAndToast(TAG, "VideoFragment:onChannelOpen()");
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {
        logAndToast(TAG, "VideoFragment:onRemoteDescription()");
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        logAndToast(TAG, "VideoFragment:onRemoteIceCandidate()");
    }

    @Override
    public void onChannelClose() {
        logAndToast(TAG, "VideoFragment:onChannelClose");
    }

    @Override
    public void onChannelError(String description) {
        logAndToast(TAG, "VideoFragment:onChannelError");
    }

    //endregion

    //region - Peer Connection Event Callbacks

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        logAndToast(TAG, "VideoFragment:onLocalDescription");
        mLocalSessionDescription = sdp;
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        logAndToast(TAG, "VideoFragment:onIceCandidate");
        //Add this ice candidate to connected peer
        mLocalIceCandidates.add(candidate);
    }

    @Override
    public void onIceConnected() {
        logAndToast(TAG, "VideoFragment:onIceConnected");
    }

    @Override
    public void onIceDisconnected() {
        logAndToast(TAG, "VideoFragment:onIceDisconnected");
    }

    @Override
    public void onPeerConnectionClosed() {
        logAndToast(TAG, "VideoFragment:onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionError(String description) {
        logAndToast(TAG, "VideoFragment:onPeerConnectionError");
    }

    //endregion

    //region - Firebase Child Event Listener for Peer Node

    /**
     * The PeerNodeEventListener extends the implementation of FirebaseChildEventListener base class.
     * We use this for listening to events that happen on the peer node within our firebase structure.
     */
    private class PeerNodeEventListener extends FirebaseChildEventListener{

        /**
         * The onChildAdded callback is raised for each child that has been added to the Peers node.
         * If there are 3 records under peers, the first time we start listening to the node we will receive 3
         * onChildAdded events.
         * @param dataSnapshot
         * @param s
         */
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            super.onChildAdded(dataSnapshot, s);

            if(mPeerNotificationCount > 0){
                //Do nothing, we are getting notifications for peers that were already connected
                Log.d(TAG, String.format("Existing Peer: %s", dataSnapshot.getKey()));
                mPeerNotificationCount--; //Decrement the peer counter, because we got a notification.
            } else {
                //Create offer for new peer that just connected
                Log.d(TAG, String.format("New Peer Added: %s", dataSnapshot.getKey()));
                mPeers.add(dataSnapshot.getKey());
            }
        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {
            super.onChildRemoved(dataSnapshot);

            Log.d(TAG, String.format("Peer was removed: %s", dataSnapshot.getKey()));
        }
    }
    //endregion

    private class SignalingNodeEventListener extends FirebaseChildEventListener{
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s){
            super.onChildAdded(dataSnapshot, s);

            Log.d(TAG, String.format("Signaling Added: %s", dataSnapshot.getKey()));
        }
    }
}
