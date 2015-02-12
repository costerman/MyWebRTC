package com.mrosterman.mywebrtc.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by costerman on 2/5/15.
 */
public class SessionAdapter extends ArrayAdapter<SessionModel> {

    List<SessionModel> mSessionModels;

    public SessionAdapter(Context context, ArrayList<SessionModel> sessionModels){
        super(context, android.R.layout.simple_list_item_1, sessionModels);
        if(sessionModels == null){
            sessionModels = new ArrayList<>();
        }
        mSessionModels = sessionModels;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // Get the data item for this position
        SessionModel sessionModel = getSession(position);

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.listitem_view, parent, false);
        }

        // Lookup view for data population
        TextView content = (TextView) convertView.findViewById(R.id.content);

        // Populate the data into the template view using the data object
        String contentText = String.format("Room #: %s", sessionModel.id);
        content.setText(contentText);

        // Return the completed view to render on screen
        return convertView;
    }

    private SessionModel getSession(int index){
        return mSessionModels.get(index);
    }
}
