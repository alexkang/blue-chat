package com.alexkang.bluechat;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Alex on 7/18/2014.
 */
public class MessageFeedAdapter extends ArrayAdapter<MessageBox> {

    Context mContext;

    public MessageFeedAdapter(Context context, ArrayList<MessageBox> messages) {
        super(context, R.layout.message_row, messages);

        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MessageBox message = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.message_row, parent, false);
        }

        TextView senderView = (TextView) convertView.findViewById(R.id.name);
        TextView messageView = (TextView) convertView.findViewById(R.id.message);
        TextView timeView = (TextView) convertView.findViewById(R.id.time);
        ImageView imageView = (ImageView) convertView.findViewById(R.id.image);

        if (message.isSelf()) {
            senderView.setGravity(Gravity.RIGHT);
            messageView.setGravity(Gravity.RIGHT);
        } else {
            senderView.setGravity(Gravity.LEFT);
            messageView.setGravity(Gravity.LEFT);
        }

        if (!message.isImage()) {
            messageView.setText(message.getMessage());
            imageView.setImageDrawable(null);
        } else {
            AbsListView.LayoutParams imageParams =
                    new AbsListView.LayoutParams(
                            AbsListView.LayoutParams.MATCH_PARENT,
                            AbsListView.LayoutParams.WRAP_CONTENT
                    );
            convertView.setLayoutParams(imageParams);
            imageView.setImageBitmap(message.getImage());
        }

        senderView.setText(message.getSender());
        timeView.setText(message.getTime());

        return convertView;
    }

}