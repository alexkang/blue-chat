package com.alexkang.btchatroom;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Created by Alex on 7/24/2014.
 */
public class ChatManager {

    public static final int MESSAGE_NAME = 2;
    public static final int MESSAGE_SEND = 3;
    public static final int MESSAGE_RECEIVE = 4;

    private boolean isHost;
    private ArrayList<ConnectedThread> connections;

    private Activity mActivity;
    private LinearLayout mMessageFeed;

    private ConnectedThread mConnectedThread;

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_NAME:
                    byte[] nameBuffer = (byte[]) msg.obj;
                    String name = new String(nameBuffer);

                    if (mActivity.getActionBar() != null) {
                        mActivity.getActionBar().setTitle(name);
                    }
                    break;
                case MESSAGE_SEND:
                    if (isHost) {
                        byte[] sendBuffer = (byte[]) msg.obj;
                        String sendMessage = new String(sendBuffer);

                        write(sendMessage, MESSAGE_RECEIVE);
                    }
                    break;
                case MESSAGE_RECEIVE:
                    byte[] receiveBuffer = (byte[]) msg.obj;
                    String receiveMessage = new String(receiveBuffer);
                    TextView receiveView = new TextView(mActivity);

                    receiveView.setText(receiveMessage);
                    mMessageFeed.addView(receiveView, 0);
            }
        }

    };

    public ChatManager(Activity activity, boolean isHost) {
        mActivity = activity;
        mMessageFeed = (LinearLayout) activity.findViewById(R.id.message_feed);
        this.isHost = isHost;

        if (isHost) {
            connections = new ArrayList<ConnectedThread>();
        }
    }

    public void startConnection(BluetoothSocket socket) {
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        if (isHost) {
            connections.add(mConnectedThread);
        }
    }

    public void write(String message, int type) {
        if (isHost) {
            for (ConnectedThread connection : connections) {
                connection.write(message, type);
            }

            mHandler.obtainMessage(type, -1, -1, message.getBytes())
                    .sendToTarget();
        } else {
            mConnectedThread.write(message, type);
        }
    }

    private class ConnectedThread extends Thread {

        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {}

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    mmInStream.read(buffer);
                    String message = new String(buffer);
                    int type = Character.getNumericValue(message.charAt(0));

                    mHandler.obtainMessage(type, -1, -1, message.substring(1).getBytes())
                            .sendToTarget();
                } catch (IOException e) {}
            }
        }

        public void write(String message, int type) {
            try {
                String formattedMessage = type + message;
                mmOutStream.write(formattedMessage.getBytes());
            } catch (IOException e) {}
        }

    }

}
