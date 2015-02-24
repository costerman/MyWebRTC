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

    private enum SDPType{
        Offer,
        Answer,
        Ice
    }

    private static final String TAG = "PeerConnectionManager";

    private List<String> mPeers;
    private List<PeerConnection.IceServer> mIceServers;
    private ConcurrentLinkedQueue<IceCandidate> mLocalIceCandidates;
    private SessionDescription mLocalSessionDescription = null;
    private PeerConnectionClient mPeerConnectionClient;
    private WebRTCAudioManager mAudioManager;
    private Context mAppContext;
    private SignalingParameters mSignalingParameters;
    private boolean mHwCodec = true;
    private int mStartBitrate;
    private VideoRenderer.Callbacks mLocalRenderer;
    private VideoRenderer.Callbacks mRemoteRenderer;
    private Firebase mRootRef;
    private Firebase mSessionRef;
    private Firebase mPeersRef;
    private Firebase mReceiverRef;
    private Activity mActivity;
    private String mSessionId;
    private String mClientId;
    private String mAgentId;
    private SDPType mSDPType = SDPType.Offer;
    private boolean mIsConnectionEstablished = false;


    public PeerConnectionManager(Context context, Firebase ref){
        mRootRef = ref.getRoot();
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

    //TEMPORARY CODE UNTIL SERVER IS IMPLEMENTED
    public String createRoom(){
        String roomId = IdGenerator.generateId();
        HashMap<String, Object> session = new HashMap<>();
        HashMap<String, Long> sessionStartedAt = new HashMap<>();
        sessionStartedAt.put("startedAt", DateUtil.getDateMilliseconds());
        session.put(roomId, sessionStartedAt);

        try{
            mRootRef.child("sessions").updateChildren(session);
        } catch (Exception ex){
            ex.printStackTrace();
            Log.e(TAG, ex.getMessage());
        }

        return roomId;
    }
    //END TEMPORARY CODE

    public void joinRoom(String sessionId){
        mSessionId = sessionId;
        mSessionRef = mRootRef.child("sessions").child(mSessionId);

        try{
            //TEMPORARY CODE UNTIL SERVER IS IMPLEMENTED
            mPeersRef = mSessionRef.child("peers").push();
            //END TEMPORARY CODE

            //Persist our ID which comes from the Peers node when we join...this will come from the server once that piece is implemented
            mClientId = mPeersRef.getKey();

            //Start listening to notifications on Peers node
            mPeersRef.addChildEventListener(new FirebaseChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    super.onChildAdded(dataSnapshot, s);
                    handlePeerAdded(dataSnapshot);
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                    super.onChildRemoved(dataSnapshot);
                    //TODO: Time to do some cleanup?
                    Log.d(TAG, String.format("Peer was removed: %s", dataSnapshot.getKey()));
                }
            });

            //The SignalingNodeRef is used for all signaling to this Client from a peer
            mReceiverRef = mSessionRef.child("connections").child(mClientId);
            mReceiverRef.addChildEventListener(new FirebaseChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    super.onChildAdded(dataSnapshot, s);
                    handleSenderAdded(dataSnapshot);
                }
            });

            //We are listening on all required nodes, time to set connected to true
            mPeersRef.child(mClientId).child("connected").setValue(true);

        } catch (Exception ex){
            Log.d(TAG, String.format("endAt Exception: %s", ex.getMessage()));
        }
    }

    private void handlePeerAdded(DataSnapshot dataSnapshot){
        //Mobile client should be the first peer added to the peers node, so any child added will be either an agent, shadow, or recorder
        //Listen to agent's peer connection node
        Log.d(TAG, String.format("New Peer Added: %s", dataSnapshot.getKey()));
        mAgentId = dataSnapshot.getKey();
        mPeers.add(mAgentId);
        Firebase agentPeerRef = mSessionRef.child("peers").child(mAgentId);
        agentPeerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean connected = (boolean) dataSnapshot.getValue();
                if (connected == true) {
                    //Agent is connected add listener for ice candidates under sender
                    mReceiverRef.child(mAgentId).child("ice").addChildEventListener(new FirebaseChildEventListener() {
                        @Override
                        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                            super.onChildAdded(dataSnapshot, s);
                            handleIceCandidateAdded(dataSnapshot);
                        }
                    });
                    //Create the offer for the sender
                    mPeerConnectionClient.createOffer();
                    mSDPType = SDPType.Offer;
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                //TODO: Handle Error case
            }
        });
    }

    private void handleSenderAdded(DataSnapshot dataSnapshot){
        logAndToast(TAG, "Remote SDP Added");
        HashMap<String, Object> sender = null;

        //Get the Sender data
        try {
            sender = (HashMap<String, Object>) dataSnapshot.getValue();
        } catch(Exception ex){
            Log.e(TAG, "Blew up when we tried to cast sender to a HashMap<String, Object>");
            ex.printStackTrace();
            return;
        }

        //Set the remote session description based on the type
        SDPType type = (SDPType)sender.get("type");
        String sdp = sender.get("sdp").toString();
        if(type != SDPType.Ice){
            //Set remote description
            SessionDescription sessionDescription = null;
            if(type == SDPType.Answer)
                sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            else
                sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp);
            mPeerConnectionClient.setRemoteDescription(sessionDescription);
        }
    }

    private void handleIceCandidateAdded(DataSnapshot dataSnapshot){
        HashMap<String, Object> ice = null;
        try{
             ice = (HashMap<String, Object>)dataSnapshot.getValue();
        } catch (Exception ex){
            logAndToast(TAG, "Blew up when we tried to cast ice to a HashMap<String, Object>");
            ex.printStackTrace();
            return;
        }

        int sdpMLineIndex = Integer.parseInt(ice.get("sdpMLineIndex").toString());
        String candiate = ice.get("candidate").toString();
        String sdpMid = ice.get("sdpMid").toString();
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candiate);
        mPeerConnectionClient.addRemoteIceCandidate(iceCandidate);
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

    /**
     * On Local Description we want to update the receiver node with sender SDP information.
     * This can be either an offer or an answer depending on the mSDPType.
     * @param sdp
     */
    @Override
    public void onLocalDescription(SessionDescription sdp) {
        logAndToast(TAG, "VideoFragment:onLocalDescription");
        HashMap<String, Object> sender = new HashMap<>();
        HashMap<String, Object> signalData = new HashMap<>();

        mLocalSessionDescription = sdp;
        signalData.put("sdp", mLocalSessionDescription);
        signalData.put("type", mSDPType.toString());
        sender.put(mClientId, signalData);

        mSessionRef.child("connections").child(mAgentId).updateChildren(sender);
    }

    /**
     * Each time an ice candidate is
     * @param candidate
     */
    @Override
    public void onIceCandidate(IceCandidate candidate) {
        logAndToast(TAG, "VideoFragment:onIceCandidate");
        //Add this ice candidate to connected peer

        HashMap<String, Object> iceCandidate = new HashMap<>();
        iceCandidate.put("candidate", candidate.sdp);
        iceCandidate.put("sdpMid", candidate.sdpMid);
        iceCandidate.put("sdpMLineIndex", candidate.sdpMLineIndex);

        mReceiverRef.child(mAgentId).child("ice").push().updateChildren(iceCandidate);
    }

    @Override
    public void onIceConnected() {
        logAndToast(TAG, "VideoFragment:onIceConnected");
        mIsConnectionEstablished = true;
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
}
