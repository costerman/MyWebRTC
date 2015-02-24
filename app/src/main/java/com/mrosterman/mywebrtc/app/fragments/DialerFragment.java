package com.mrosterman.mywebrtc.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.mrosterman.mywebrtc.app.*;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;

import java.util.*;

/**
 * Created by costerman on 1/30/15.
 */
public class DialerFragment extends ListFragment {

    public static final String EXTRA_ROOM_ID = "extra_room_id";

    private static final String TAG = "DialerFragment";

    private ProgressBar mProgressBar;
    private Toast mToast;
    private SessionAdapter mSessionAdapter;
    private int mSectionNumber;

    public static DialerFragment newInstance(int sectionNumber){
        DialerFragment fragment = new DialerFragment();
        fragment.mSectionNumber = sectionNumber;
        Bundle args = new Bundle();
        args.putInt(MainActivity.ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public void logAndToast(String tag, String msg){
        Log.d(tag, msg);
        if(mToast != null){
            mToast.cancel();
        }
        mToast = Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT);
        mToast.show();
    }

//    protected void raiseIntent(Session session){
//        Intent intent = new Intent(this, DialerFragment.class);
//        Bundle bundle = new Bundle();
//
//        bundle.putParcelable(WebRTCApp.SESSION_DATA_PARCEL, session);
//        intent.putExtras(bundle);
//    }

    private void updateAdapter(LinkedHashMap<String, Object> sessions){
        ArrayList<SessionModel> list = new ArrayList<>();
        if(sessions != null) {
            for (Map.Entry<String, Object> entry : sessions.entrySet()) {
                SessionModel sessionModel = new SessionModel();
                sessionModel.id = entry.getKey();
                sessionModel.sessionData = (LinkedHashMap<String, Object>)entry.getValue();
                list.add(sessionModel);
            }
        }

        if(mSessionAdapter == null){
            mSessionAdapter = new SessionAdapter(getActivity(), list);
            setListAdapter(mSessionAdapter);
        } else {
            mSessionAdapter.clear();
            mSessionAdapter.addAll(list);
        }
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(getArguments().getInt(MainActivity.ARG_SECTION_NUMBER));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Firebase ref = WebRTCApp.getInstance().getFirebaseRef().getRoot();
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                LinkedHashMap<String, Object> session =  dataSnapshot.getValue(LinkedHashMap.class);
                updateAdapter(session);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                logAndToast(TAG, firebaseError.getMessage());
            }
        });


    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

//        ViewGroup root = (ViewGroup) getActivity().findViewById(android.R.id.content);
//        mProgressBar = new ProgressBar(getActivity());
//        mProgressBar.setLayoutParams(new LinearLayout.LayoutParams(300, 10));
//        mProgressBar.setIndeterminate(true);
//        getListView().setEmptyView(mProgressBar);
//        root.addView(mProgressBar);

        LayoutInflater inflater = getActivity().getLayoutInflater();
        getListView().addFooterView(inflater.inflate(R.layout.list_view_footer, null));
        final Button createRoomButton = (Button) getActivity().findViewById(R.id.create_room_button);
        createRoomButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                WebRTCApp.getInstance().getPeerConnectionManager().createRoom();
            }
        });

        ArrayList<SessionModel> list = new ArrayList<>();
        mSessionAdapter = new SessionAdapter(getActivity(), list);
        setListAdapter(mSessionAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        SessionModel model = (SessionModel)l.getItemAtPosition(position);
        WebRTCApp.getInstance().setRoomId(model.id);
        Fragment fragment = VideoFragment.getInstance();
        Bundle args = new Bundle();
        args.putInt(MainActivity.ARG_SECTION_NUMBER, mSectionNumber);
        args.putString(VideoFragment.ARG_ROOM_ID, model.id);
        fragment.setArguments(args);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.container, fragment);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(mProgressBar != null) {
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }
}
