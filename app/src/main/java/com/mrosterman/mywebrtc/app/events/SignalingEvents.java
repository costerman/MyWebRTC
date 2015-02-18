package com.mrosterman.mywebrtc.app.events;

import com.mrosterman.mywebrtc.app.SignalingParameters;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by costerman on 1/26/15.
 */
public interface SignalingEvents {
    /**
     * Callback fired once the room's signaling parameters
     * SignalingParameters are extracted.
     */
    public void onConnectedToRoom(final SignalingParameters params);

    /**
     * Callback fired once remote SDP is received.
     */
    public void onRemoteDescription(final SessionDescription sdp);

    /**
     * Callback fired once remote Ice candidate is received.
     */
    public void onRemoteIceCandidate(final IceCandidate candidate);

    /**
     * Callback fired once channel is closed.
     */
    public void onChannelClose();

    /**
     * Callback fired once channel error happened.
     */
    public void onChannelError(final String description);
}
