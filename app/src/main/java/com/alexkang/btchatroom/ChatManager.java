package com.alexkang.btchatroom;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Alex on 7/24/2014.
 */
public class ChatManager {

    public static final int MESSAGE_NAME = 3;
    public static final int MESSAGE_SEND = 4;
    public static final int MESSAGE_RECEIVE = 5;
    public static final int MESSAGE_SEND_IMAGE = 6;
    public static final int MESSAGE_RECEIVE_IMAGE = 7;

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
                        sendBuffer[0] = MESSAGE_RECEIVE;

                        write(sendBuffer);
                    }
                    break;
                case MESSAGE_RECEIVE:
                    byte[] receiveBuffer = (byte[]) msg.obj;
                    String receiveMessage = new String(receiveBuffer);
                    TextView receiveView = new TextView(mActivity);

                    receiveView.setText(receiveMessage);
                    mMessageFeed.addView(receiveView, 0);
                    break;
                case MESSAGE_SEND_IMAGE:
                    if (isHost) {
                        byte[] sendImageBuffer = (byte[]) msg.obj;
                        sendImageBuffer[0] = MESSAGE_RECEIVE_IMAGE;

                        write(sendImageBuffer);
                    }
                    break;
                case MESSAGE_RECEIVE_IMAGE:
                    byte[] imageBuffer = (byte[]) msg.obj;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.length);

                    ImageView imageView = new ImageView(mActivity);
                    imageView.setImageBitmap(bitmap);
                    mMessageFeed.addView(imageView, 0);
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

    public void write(byte[] byteArray) {
        if (isHost) {
            for (ConnectedThread connection : connections) {
                connection.write(byteArray);
            }

            mHandler.obtainMessage(byteArray[0], -1, -1, Arrays.copyOfRange(byteArray, 1, byteArray.length))
                    .sendToTarget();
        } else {
            mConnectedThread.write(byteArray);
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
                    int type = buffer[0];

                    if (type == MESSAGE_SEND || type == MESSAGE_SEND_IMAGE) {
                        mHandler.obtainMessage(type, -1, -1, buffer)
                                .sendToTarget();
                    } else {
                        mHandler.obtainMessage(type, -1, -1, Arrays.copyOfRange(buffer, 1, buffer.length))
                                .sendToTarget();
                    }
                } catch (IOException e) {}
            }
        }

        public void write(byte[] byteArray) {
            try {
                mmOutStream.write(byteArray);
                mmOutStream.flush();
            } catch (IOException e) {}
        }

    }

}
