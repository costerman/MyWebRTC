package com.mrosterman.mywebrtc.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.Log;

import com.mrosterman.mywebrtc.app.events.PeerConnectionEvents;
import com.mrosterman.mywebrtc.app.events.SignalingEvents;
import com.mrosterman.mywebrtc.app.util.DateUtil;
import com.mrosterman.mywebrtc.app.util.IdGenerator;
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
    private Firebase mRootFirebaseRef;
    private Firebase mPeerNodeRef;
    private Firebase mSignalingNodeRef;
    private List<String> mPeers;
    private List<PeerConnection.IceServer> mIceServers;
    private ConcurrentLinkedQueue<IceCandidate> mLocalIceCandidates;
    private SessionDescription mLocalSessionDescription = null;
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
    private String mAgentId;
    private boolean mIsConnectionEstablished = false;
    private long mPeerNotificationCount = 0;

    /*
    * Root
    *   - sessions
    *       - [SessionId]
    *           - connections
    *               - [ConnectionId]
    *                   - answerer
    *                       - ice
    *                           - [CandidateId]
    *                               - candidate
    *                               - sdpMLineIndex
    *                               - sdpMid
    *                       - peerId
    *                       - sdp
    *                           - sdp
    *                           - type
    *                   - offerer
    *                       - ice
    *                           - [CandidateId]
    *                               - candidate
    *                               - sdpMLineIndex
    *                               - sdpMid
    *                       - peerId
    *                       - sdp
    *                           - sdp
    *                           - type
    *           - peers
    *               - [PeerConnectionId] (created by Firebase Push)
    *                   - connected (boolean)
    *                   - username (string)
    *
    * */

    //Firebase Field Definitions
    private static final String TYPE_ANSWERER = "answerer";
    private static final String TYPE_OFFERER = "offerer";

    //Static Firebase Paths
    private static final String SESSION_NODE = "/sessions/%s";
    private static final String CONNECTIONS_NODE = SESSION_NODE + "/connections";                   //Listen for Child Added
    private static final String CONNECTIONS_ANSWERER_ICE = CONNECTIONS_NODE + "/%s/answerer/ice";   //Listen for Child Added
    private static final String CONNECTIONS_ANSWERER_SDP = CONNECTIONS_NODE + "/%s/answerer/sdp";   //Listen for Value Changed
    private static final String CONNECTIONS_ANSWERER_ICE_CANDIDATE = CONNECTIONS_ANSWERER_ICE + "/%s";
    private static final String PEERS_NODE = SESSION_NODE + "/peers";                               //Listen for Child Added
    private static final String PEERS_CLIENT_NODE = PEERS_NODE + "/%s";
    private static final String PEERS_CONNECTED_NODE = PEERS_CLIENT_NODE + "/connected";                //Listen for Value Changed


    public PeerConnectionManager(Context context, Firebase ref){
        mRootFirebaseRef = ref;
        mSignalingNodeEventListener = new SignalingNodeEventListener();
        mPeers = new ArrayList<>();
        mLocalIceCandidates = new ConcurrentLinkedQueue<>();
        mAppContext = context;

        //TODO: The STUN/TURN server information will come from the server when we POST to /sessions route.
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

    public void initialize(){
        MediaConstraints pcConstraints = new MediaConstraints();
        MediaConstraints videoConstraints = new MediaConstraints();
        MediaConstraints audioConstraints = new MediaConstraints();
        final PeerConnectionEvents peerConnectionEvents = this;

        mSignalingParameters = new SignalingParameters(
                mIceServers,
                pcConstraints,
                videoConstraints,
                audioConstraints,
                true,
                null,
                null);

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAudioManager = WebRTCAudioManager.create(mAppContext, new Runnable() {
                    @Override
                    public void run() {
                        //Do something here...
                    }
                });
                mAudioManager.init();

                if (mPeerConnectionClient == null) {
                    mPeerConnectionClient = new PeerConnectionClient();
                    mPeerConnectionClient.createPeerConnectionFactory(
                            mActivity.getApplicationContext(), mHwCodec, VideoRendererGui.getEGLContext(), peerConnectionEvents);
                }

                mPeerConnectionClient.createPeerConnection(mLocalRenderer, mRemoteRenderer, mSignalingParameters, mStartBitrate);

                if (mPeerConnectionClient.isHDVideo()) {
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        });
    }

    //This code is actually performed by the Fire Birds Service.
    public String createRoom(){
        String roomId = IdGenerator.generateId();
        HashMap<String, Object> session = new HashMap<>();
        HashMap<String, Long> sessionStartedAt = new HashMap<>();
        sessionStartedAt.put("startedAt", DateUtil.getDateMilliseconds());
        session.put(roomId, sessionStartedAt);

        try{
            mRootFirebaseRef.child("sessions").updateChildren(session);
        } catch (Exception ex){
            ex.printStackTrace();
            Log.e(TAG, ex.getMessage());
        }

        return roomId;
    }

    public void joinRoom(String sessionId){

        HashMap<String, Object> peer = new HashMap<>();
        peer.put("connected", true);
        peer.put("username", "AndroidCarl");

        try{
            mPeerNodeRef = mRootFirebaseRef.child(String.format(PEERS_NODE, sessionId)).push();
            mClientId = mPeerNodeRef.getKey();
            mPeerNodeRef.updateChildren(peer);

            mSignalingNodeRef = mRootFirebaseRef.getRoot().child("sessions").child(sessionId).child("connections").push();


            //Retrieve the number of peers currently under the peers node
            mPeerNodeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    mPeerNotificationCount = dataSnapshot.getChildrenCount();
                    Log.d(TAG, String.format("Number of Peers already connected: %d", mPeerNotificationCount));

                    //Start listening to notifications on Peers node
                    mPeerNodeRef.addChildEventListener(new FirebaseChildEventListener() {
                        @Override
                        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                            super.onChildAdded(dataSnapshot, s);
                            handlePeerAdded(dataSnapshot);
                        }

                        @Override
                        public void onChildRemoved(DataSnapshot dataSnapshot) {
                            super.onChildRemoved(dataSnapshot);

                            Log.d(TAG, String.format("Peer was removed: %s", dataSnapshot.getKey()));
                        }
                    });
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {

                }
            });
        } catch (Exception ex){
            Log.d(TAG, String.format("endAt Exception: %s", ex.getMessage()));
        }
    }

    private void handlePeerAdded(DataSnapshot dataSnapshot){
        if(mPeerNotificationCount > 0){
            //Do nothing, we are getting notifications for peers that were already connected
            Log.d(TAG, String.format("Existing Peer: %s", dataSnapshot.getKey()));
            mPeerNotificationCount--; //Decrement the peer counter, because we got a notification.
        } else {
            //Listen to agent's peer connection node
            String agentId = dataSnapshot.getKey();
            Firebase ref = mRootFirebaseRef.child(String.format(PEERS_CONNECTED_NODE, agentId));
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if(dataSnapshot.getValue() == true){
                        //Agent is connected, time to create a new record under the connection node.
                    }
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {
                    //TODO: Handle Error case
                }
            });


            //Create offer for new peer that just connected
            Log.d(TAG, String.format("New Peer Added: %s", dataSnapshot.getKey()));
            mPeers.add(dataSnapshot.getKey());
            mPeerConnectionClient.createOffer();
        }
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


    private class SignalingNodeEventListener extends FirebaseChildEventListener{
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s){
            super.onChildAdded(dataSnapshot, s);

            Log.d(TAG, String.format("Signaling Added: %s", dataSnapshot.getKey()));
        }
    }
}
