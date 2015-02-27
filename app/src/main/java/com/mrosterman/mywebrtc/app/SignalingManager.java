package com.mrosterman.mywebrtc.app;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.util.Log;

import com.mrosterman.mywebrtc.app.util.DateUtil;
import com.firebase.client.*;

import org.webrtc.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by costerman on 2/10/15.
 * NOTE: Logic is not currently in place to handle more than one peer.
 */
public class SignalingManager implements
        PeerConnectionClient.PeerConnectionEvents {

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
    private Firebase mRoomRef;
    private Firebase mPeersRef;
    private Firebase mReceiverRef;
    private Activity mActivity;
    private String mRoomId;
    private String mClientId;
    private String mAgentId;
    private SDPType mSDPType = SDPType.Offer;
    private boolean mIsConnectionEstablished = false;
    private long mPeerInitializationCount = 0;


    public SignalingManager(Context context, Firebase ref){
        mRootRef = ref.getRoot();
        mPeers = new ArrayList<>();
        mLocalIceCandidates = new ConcurrentLinkedQueue<>();
        mAppContext = context;
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
        final PeerConnectionClient.PeerConnectionEvents peerConnectionEvents = this;

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

    /**
     * Creates a new room with a firebase generated id.
     * @return
     */
    public String createRoom(){
        Firebase ref = mRootRef.child("rooms").push();
        String roomId = ref.getKey();
        HashMap<String, Object> room = new HashMap<>();
        HashMap<String, Long> roomCreatedAt = new HashMap<>();
        roomCreatedAt.put("startedAt", DateUtil.getDateMilliseconds());
        room.put(roomId, roomCreatedAt);

        try{
            ref.updateChildren(room);
        } catch (Exception ex){
            ex.printStackTrace();
            Log.e(TAG, ex.getMessage());
        }

        return roomId;
    }

    /**
     * Joins a room and
     * @param roomId
     */
    public void joinRoom(String roomId){
        mRoomId = roomId;
        mRoomRef = mRootRef.child("rooms").child(mRoomId);

        try{
            mPeersRef = mRoomRef.child("peers").push();
            mClientId = mPeersRef.getKey();

            mPeersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    //Since peers could have been added already, and we are not the first to join the room, we need
                    //to set a counter and decrement for each child added event
                    mPeerInitializationCount = dataSnapshot.getChildrenCount();

                    //Start listening to notifications on Peers node
                    mPeersRef.addChildEventListener(new FirebaseChildEventListener() {
                        @Override
                        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                            super.onChildAdded(dataSnapshot, s);
                            if(mPeerInitializationCount == 0) {
                                //A new peer was added after we joined, time to send an offer.
                                handlePeerAdded(dataSnapshot);
                            } else {
                                //There were other peers that already joined, they will be sending us an offer.
                                mPeerInitializationCount--;
                            }
                        }

                        @Override
                        public void onChildRemoved(DataSnapshot dataSnapshot) {
                            super.onChildRemoved(dataSnapshot);
                            //TODO: Time to do some cleanup?
                            Log.d(TAG, String.format("Peer was removed: %s", dataSnapshot.getKey()));
                        }
                    });
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {
                    //TODO: Handle error
                    Log.e(TAG, "We had an error when listening for a single value event on the Peers node.");
                }
            });


            //The SignalingNodeRef is used for all signaling to this Client from a peer
            mReceiverRef = mRoomRef.child("connections").child(mClientId);
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

    /**
     * Handle Peers added will send an offer to the connected peer.
     * @param dataSnapshot
     */
    private void handlePeerAdded(DataSnapshot dataSnapshot){

        Log.d(TAG, String.format("New Peer Added: %s", dataSnapshot.getKey()));
        mAgentId = dataSnapshot.getKey();
        mPeers.add(mAgentId);
        Firebase agentPeerRef = mRoomRef.child("peers").child(mAgentId);
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

    /**
     * This function is used to handle when we receive an offer/answer
     * @param dataSnapshot
     */
    private void handleSenderAdded(DataSnapshot dataSnapshot){
        Log.d(TAG, "Remote SDP Added");
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

    /**
     * This function is used to handle when ice candidates are received from a sender.
     * @param dataSnapshot
     */
    private void handleIceCandidateAdded(DataSnapshot dataSnapshot){
        HashMap<String, Object> ice = null;
        try{
             ice = (HashMap<String, Object>)dataSnapshot.getValue();
        } catch (Exception ex){
            Log.d(TAG, "Blew up when we tried to cast ice to a HashMap<String, Object>");
            ex.printStackTrace();
            return;
        }

        int sdpMLineIndex = Integer.parseInt(ice.get("sdpMLineIndex").toString());
        String candiate = ice.get("candidate").toString();
        String sdpMid = ice.get("sdpMid").toString();
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candiate);
        mPeerConnectionClient.addRemoteIceCandidate(iceCandidate);
    }

    //region - Peer Connection Event Callbacks

    /**
     * On Local Description we want to update the receiver node with sender SDP information.
     * This can be either an offer or an answer depending on the mSDPType.
     * @param sdp
     */
    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.d(TAG, "VideoFragment:onLocalDescription");
        HashMap<String, Object> sender = new HashMap<>();
        HashMap<String, Object> signalData = new HashMap<>();

        mLocalSessionDescription = sdp;
        signalData.put("sdp", mLocalSessionDescription);
        signalData.put("type", mSDPType.toString());
        sender.put(mClientId, signalData);

        mRoomRef.child("connections").child(mAgentId).updateChildren(sender);
    }

    /**
     * Each time a local ice candidate is created from PeerConnectionClient
     * @param candidate
     */
    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.d(TAG, "VideoFragment:onIceCandidate");
        //Add this ice candidate to connected peer

        HashMap<String, Object> iceCandidate = new HashMap<>();
        iceCandidate.put("candidate", candidate.sdp);
        iceCandidate.put("sdpMid", candidate.sdpMid);
        iceCandidate.put("sdpMLineIndex", candidate.sdpMLineIndex);

        mReceiverRef.child(mAgentId).child("ice").push().updateChildren(iceCandidate);
    }

    @Override
    public void onIceConnected() {
        Log.d(TAG, "VideoFragment:onIceConnected");
        mIsConnectionEstablished = true;
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebRTCApp.getInstance().setConnected(true);
            }
        });
    }

    @Override
    public void onIceDisconnected() {
        Log.d(TAG, "VideoFragment:onIceDisconnected");
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.d(TAG, "VideoFragment:onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.d(TAG, "VideoFragment:onPeerConnectionError");
    }

    //endregion
}
