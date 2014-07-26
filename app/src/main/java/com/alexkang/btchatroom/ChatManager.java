package com.alexkang.btchatroom;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

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

    private ArrayList<MessageBox> mMessageList;
    private MessageFeedAdapter mFeedAdapter;

    private Activity mActivity;
    private ListView mMessageFeed;

    private BluetoothSocket mSocket;
    private ConnectedThread mConnectedThread;

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_NAME:
                    if (!isHost) {
                        byte[] nameBuffer = (byte[]) msg.obj;
                        String name = new String(nameBuffer);

                        if (mActivity.getActionBar() != null) {
                            mActivity.getActionBar().setTitle(name);
                        }

                        Toast.makeText(mActivity, "Connected to " + name, Toast.LENGTH_SHORT).show();
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
                    int nameLength = msg.arg1;
                    String wholeMessage = new String(receiveBuffer);
                    String name = wholeMessage.substring(0, nameLength);
                    String receiveMessage;

                    if (nameLength == 0) {
                        receiveMessage = wholeMessage;
                    } else {
                        receiveMessage = wholeMessage.substring(nameLength);
                    }

                    boolean isSelf = BluetoothAdapter.getDefaultAdapter().getName().equals(name);
                    MessageBox messageBox = new MessageBox(name, receiveMessage, new Date(), isSelf);
                    addMessage(messageBox);

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
        mMessageFeed = (ListView) mActivity.findViewById(R.id.message_feed);
        this.isHost = isHost;

        if (isHost) {
            connections = new ArrayList<ConnectedThread>();
        }

        mMessageList = new ArrayList<MessageBox>();
        mFeedAdapter = new MessageFeedAdapter(mActivity, mMessageList);
        mMessageFeed.setAdapter(mFeedAdapter);
    }

    public void startConnection(BluetoothSocket socket) {
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        mSocket = socket;

        if (isHost) {
            connections.add(mConnectedThread);
        }
    }

    public void restartConnection() {
        if (!isHost && mSocket != null) {
            try {
                mSocket.close();
                mConnectedThread = new ConnectedThread(mSocket);
                mConnectedThread.start();
            } catch (IOException e) {}
        }
    }

    public void write(byte[] byteArray) {
        if (isHost) {
            for (ConnectedThread connection : connections) {
                connection.write(byteArray);
            }

            mHandler.obtainMessage(byteArray[0], byteArray[1], -1, Arrays.copyOfRange(byteArray, 2, byteArray.length))
                    .sendToTarget();
        } else {
            mConnectedThread.write(byteArray);
        }
    }

    public void writeName(byte[] byteArray) {
        for (ConnectedThread connection : connections) {
            connection.write(byteArray);
        }
    }

    private void addMessage(MessageBox message) {
        mMessageList.add(message);
        mMessageFeed.invalidateViews();
        mFeedAdapter.notifyDataSetChanged();
        mMessageFeed.setSelection(mFeedAdapter.getCount()-1);
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
                    int nameLength = buffer[1];

                    if (type == MESSAGE_SEND || type == MESSAGE_SEND_IMAGE) {
                        mHandler.obtainMessage(type, -1, -1, buffer)
                                .sendToTarget();
                    } else if (type == MESSAGE_RECEIVE || type == MESSAGE_RECEIVE_IMAGE) {
                        mHandler.obtainMessage(type, nameLength, -1, Arrays.copyOfRange(buffer, 2, buffer.length))
                                .sendToTarget();
                    } else {
                        mHandler.obtainMessage(type, -1, -1, Arrays.copyOfRange(buffer, 1, buffer.length))
                                .sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
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
