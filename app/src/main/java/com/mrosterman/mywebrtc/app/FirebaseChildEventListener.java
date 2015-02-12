package com.mrosterman.mywebrtc.app;

import android.util.Log;
import com.firebase.client.ChildEventListener;
import com.firebase.client.DataSnapshot;
import com.firebase.client.FirebaseError;

/**
 * Created by costerman on 2/5/15.
 */
public abstract class FirebaseChildEventListener implements ChildEventListener {
    @Override
    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
        //Send Offer
    }

    @Override
    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
        //Do we need to do anything here?
    }

    @Override
    public void onChildRemoved(DataSnapshot dataSnapshot) {
        //Do we need to do anything here?
    }

    @Override
    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
        //Do we need to do anything here?
    }

    @Override
    public void onCancelled(FirebaseError firebaseError) {
        //Do we need to do anything here?
    }
}
