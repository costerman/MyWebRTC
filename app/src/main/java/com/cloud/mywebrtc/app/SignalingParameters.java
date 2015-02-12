package com.cloud.mywebrtc.app;

import android.content.pm.PackageInstaller;
import org.webrtc.*;
import org.webrtc.PeerConnection;

import java.util.List;

/**
 * Created by costerman on 1/26/15.
 */
public class SignalingParameters {

    public List<PeerConnection.IceServer> iceServers;
    public MediaConstraints pcConstraints;
    public MediaConstraints videoConstraints;
    public MediaConstraints audioConstraints;
    public String roomId;
    public String clientId;
    public SessionDescription offerSDP;
    List<IceCandidate> iceCandidates;
    public boolean initiator;

    public SignalingParameters(List<PeerConnection.IceServer> iceServers,
                               MediaConstraints pcConstraints,
                               MediaConstraints videoConstraints,
                               MediaConstraints audioConstraints,
                               String roomId,
                               String clientId,
                               boolean initiator,
                               SessionDescription offerSDP,
                               List<IceCandidate> iceCandidates){
        this.iceServers = iceServers;
        this.pcConstraints = pcConstraints;
        this.audioConstraints = audioConstraints;
        this.videoConstraints = videoConstraints;
        this.roomId = roomId;
        this.clientId = clientId;
        this.initiator = initiator;
        this.offerSDP = offerSDP;
        this.iceCandidates = iceCandidates;
    }
}
