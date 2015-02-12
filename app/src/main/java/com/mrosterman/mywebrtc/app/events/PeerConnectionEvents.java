package com.mrosterman.mywebrtc.app.events;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by costerman on 1/26/15.
 */
public interface PeerConnectionEvents {
    /**
     * Callback fired once offer is created and local SDP is set.
     */
    public void onLocalDescription(final SessionDescription sdp);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    public void onIceCandidate(final IceCandidate candidate);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    public void onIceConnected();

    /**
     * Callback fired once connection is closed (IceConnectionState is
     * DISCONNECTED).
     */
    public void onIceDisconnected();

    /**
     * Callback fired once peer connection is closed.
     */
    public void onPeerConnectionClosed();

    /**
     * Callback fired once peer connection error happened.
     */
    public void onPeerConnectionError(String description);
}