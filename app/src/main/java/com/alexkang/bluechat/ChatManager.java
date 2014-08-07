package com.alexkang.bluechat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class ChatManager {

    public static final int BODY_LENGTH_END = 255;
    public static final int BODY_LENGTH_END_SIGNED = -1;

    public static final int MESSAGE_NAME = 1;
    public static final int MESSAGE_SEND = 2;
    public static final int MESSAGE_RECEIVE = 3;
    public static final int MESSAGE_SEND_IMAGE = 4;
    public static final int MESSAGE_RECEIVE_IMAGE = 5;

    private SharedPreferences sharedPref;

    private boolean isHost;
    private ArrayList<ConnectedThread> connections;

    private ArrayList<MessageBox> mMessageList;
    private MessageFeedAdapter mFeedAdapter;
    private ListView mMessageFeed;

    private Activity mActivity;
    private ProgressDialog mProgressDialog;

    private BluetoothSocket mSocket;
    private ConnectedThread mConnectedThread;

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            byte[] packet = (byte[]) msg.obj;
            int senderLength = msg.arg1;
            int senderIndex = msg.arg2;

            String sender = new String(Arrays.copyOfRange(packet, 0, senderLength));
            byte[] body = Arrays.copyOfRange(packet, senderLength, packet.length);

            String username = sharedPref.getString("username", BluetoothAdapter.getDefaultAdapter().getName());
            boolean isSelf = username.equals(sender);

            switch (msg.what) {
                case MESSAGE_NAME:
                    if (!isHost) {
                        String chatRoomName = new String(body);

                        if (mActivity.getActionBar() != null) {
                            mActivity.getActionBar().setTitle(chatRoomName);
                        }

                        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                        mProgressDialog.dismiss();
                        Toast.makeText(mActivity, "Connected to " + chatRoomName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case MESSAGE_SEND:
                    if (isHost) {
                        byte[] sendPacket = buildPacket(MESSAGE_SEND, sender, body);
                        writeMessage(sendPacket, senderIndex);
                    }
                    break;
                case MESSAGE_RECEIVE:
                    MessageBox messageBox = new MessageBox(sender, new String(body), new Date(), isSelf);
                    addMessage(messageBox);
                    break;
                case MESSAGE_SEND_IMAGE:
                    if (isHost) {
                        byte[] sendImagePacket = buildPacket(MESSAGE_SEND_IMAGE, sender, body);
                        writeMessage(sendImagePacket, senderIndex);
                    }
                    break;
                case MESSAGE_RECEIVE_IMAGE:
                    Bitmap bitmap = BitmapFactory.decodeByteArray(body, 0, body.length);
                    MessageBox imageBox = new MessageBox(sender, bitmap, new Date(), isSelf);
                    addMessage(imageBox);
            }
        }

    };

    public ChatManager(Activity activity, boolean isHost) {
        mActivity = activity;
        mMessageFeed = (ListView) mActivity.findViewById(R.id.message_feed);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(mActivity);
        this.isHost = isHost;

        if (isHost) {
            connections = new ArrayList<ConnectedThread>();
        }

        mMessageList = new ArrayList<MessageBox>();
        mFeedAdapter = new MessageFeedAdapter(mActivity, mMessageList);
        mMessageFeed.setAdapter(mFeedAdapter);

        View footer = new View(mActivity);
        footer.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250));
        footer.setBackgroundColor(mActivity.getResources().getColor(android.R.color.transparent));

        mMessageFeed.addFooterView(footer, null, false);
    }

    public void startConnection(BluetoothSocket socket) {
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        mSocket = socket;

        if (isHost) {
            connections.add(mConnectedThread);
        }
    }

    public void startConnection(BluetoothSocket socket, ProgressDialog progressDialog) {
        startConnection(socket);
        mProgressDialog = progressDialog;
    }

    public void restartConnection() {
        if (!isHost && mSocket != null) {
            try {
                mSocket.close();
                mConnectedThread = new ConnectedThread(mSocket);
                mConnectedThread.start();
            } catch (IOException e) {
                Toast.makeText(mActivity, "Failed to reconnect", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(mActivity, MainActivity.class);
                mActivity.startActivity(i);
                mActivity.finish();
            }
        }
    }

    public static byte[] buildPacket(int type, String name, byte[] body) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(type);
        output.write(name.length());

        int bodyLength = body.length;
        do {
           output.write(bodyLength % 10);
           bodyLength = bodyLength / 10;
        } while (bodyLength > 0);

        try {
            output.write(BODY_LENGTH_END);
            output.write(name.getBytes());
            output.write(body);
        } catch (IOException e) {
            System.err.println("Error in building packet.");
            return null;
        }

        return output.toByteArray();
    }

    public void writeChatRoomName(byte[] byteArray) {
        if (isHost) {
            for (ConnectedThread connection : connections) {
                connection.write(byteArray);
            }
        }
    }

    public void writeMessage(byte[] byteArray, int senderIndex) {
        // senderIndex of -1 indicates that the sender called this function themselves.

        int type = byteArray[0];
        int receiveType = 0;
        if (type == MESSAGE_SEND) {
            receiveType = MESSAGE_RECEIVE;
        } else if (type == MESSAGE_SEND_IMAGE) {
            receiveType = MESSAGE_RECEIVE_IMAGE;
        }

        int senderLength = byteArray[1];

        int currIndex = 2;
        do {
            currIndex++;
        } while (byteArray[currIndex] != BODY_LENGTH_END_SIGNED);

        mHandler.obtainMessage(receiveType, senderLength, senderIndex, Arrays.copyOfRange(byteArray, currIndex + 1, byteArray.length))
                .sendToTarget();

        if (isHost) {
            byteArray[0] = (byte) receiveType;
            for (int i = 0; i < connections.size(); i++) {
                if (i != senderIndex) {
                    connections.get(i).write(byteArray);
                }
            }
        } else {
            mConnectedThread.write(byteArray);
        }
    }

    public void writeMessage(byte[] byteArray) {
        writeMessage(byteArray, -1);
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
            } catch (IOException e) {
                Toast.makeText(mActivity, "Could not connect to ChatRoom, now exiting", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(mActivity, MainActivity.class);
                mActivity.startActivity(i);
                mActivity.finish();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            while (true) {
                try {
                    int type = mmInStream.read();
                    int senderLength = mmInStream.read();
                    int senderIndex = -1;

                    if (isHost) {
                        senderIndex = connections.indexOf(this);
                    }

                    /*
                     * Calculate the length of the body in bytes. Each byte read is a digit
                     * in the body length in order of least to most significant digit.
                     *
                     * i.e. Body length of 247 would be read in the form {7, 4, 2}.
                     */
                    int bodyLength = 0;
                    int currPlace = 1;
                    int currDigit = mmInStream.read();
                    do {
                        bodyLength += (currDigit * currPlace);
                        currPlace *= 10;
                        currDigit = mmInStream.read();
                    } while (currDigit != BODY_LENGTH_END);

                    ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
                    for (int i = 0; i < senderLength + bodyLength; i++) {
                        packetStream.write(mmInStream.read());
                    }
                    byte[] packet = packetStream.toByteArray();

                    mHandler.obtainMessage(type, senderLength, senderIndex, packet)
                            .sendToTarget();
                } catch (IOException e) {
                    System.err.println("Error in receiving packets");
                    e.printStackTrace();
                    break;
                }
            }
        }

        public void write(byte[] byteArray) {
            try {
                mmOutStream.write(byteArray);
                mmOutStream.flush();
            } catch (IOException e) {
                String byteArrayString = "";
                for (byte b : byteArray) {
                    byteArrayString += b + ", ";
                }
                System.err.println("Failed to write bytes: " + byteArrayString);
                System.err.println(e.toString());
            }
        }

    }

}
