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

    public static final int MESSAGE_NAME = 0;
    public static final int MESSAGE_SEND = 1;
    public static final int MESSAGE_RECEIVE = 2;
    public static final int MESSAGE_SEND_IMAGE = 3;
    public static final int MESSAGE_RECEIVE_IMAGE = 4;

    private SharedPreferences sharedPref;

    private boolean isHost;
    private ArrayList<ConnectedThread> connections;

    private ArrayList<MessageBox> mMessageList;
    private MessageFeedAdapter mFeedAdapter;

    private Activity mActivity;
    private ListView mMessageFeed;

    private BluetoothSocket mSocket;
    private ConnectedThread mConnectedThread;

    private ProgressDialog mProgressDialog;

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

                        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                        mProgressDialog.dismiss();
                        Toast.makeText(mActivity, "Connected to " + name, Toast.LENGTH_SHORT).show();
                    }

                    break;
                case MESSAGE_SEND:
                    if (isHost) {
                        byte[] sendBuffer = (byte[]) msg.obj;
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

                    String username = sharedPref.getString("username", BluetoothAdapter.getDefaultAdapter().getName());
                    boolean isSelf = username.equals(name);
                    MessageBox messageBox = new MessageBox(name, receiveMessage, new Date(), isSelf);
                    addMessage(messageBox);

                    break;
                case MESSAGE_SEND_IMAGE:
                    if (isHost) {
                        byte[] sendImageBuffer = (byte[]) msg.obj;
                        writeImage(sendImageBuffer, msg.arg1);
                    }

                    break;
                case MESSAGE_RECEIVE_IMAGE:
                    byte[] imageBuffer = (byte[]) msg.obj;
                    int imageSenderLength = msg.arg1;
                    String imageSenderName = new String(Arrays.copyOfRange(imageBuffer, 0, imageSenderLength));
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBuffer, imageSenderLength, imageBuffer.length - imageSenderLength);
                    MessageBox imageBox = new MessageBox(imageSenderName, bitmap, new Date(), true);
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

    public byte[] buildPacket(int type, String name, byte[] body) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(type);
        output.write(name.length());

        int bodyLength = body.length;
        do {
           output.write(bodyLength % 10);
           bodyLength = bodyLength / 10;
        } while (bodyLength > 0);
        output.write(BODY_LENGTH_END);
        output.write(name.getBytes());
        output.write(body);

        return output.toByteArray();
    }

    public void write(byte[] byteArray) {
        if (isHost) {
            for (ConnectedThread connection : connections) {
                connection.write(byteArray);
            }

            int currIndex = 2;
            do {
                currIndex++;
            } while (byteArray[currIndex] != BODY_LENGTH_END_SIGNED);

            mHandler.obtainMessage(byteArray[0], byteArray[1], -1, Arrays.copyOfRange(byteArray, currIndex + 1, byteArray.length))
                    .sendToTarget();
        } else {
            mConnectedThread.write(byteArray);
        }
    }

    public void writeImage(byte[] byteArray, int senderIndex) {
        if (isHost) {
            for (int i = 0; i < connections.size(); i++) {
                if (i != senderIndex) {
                    connections.get(i).write(byteArray);
                }
            }
        } else {
            mConnectedThread.write(byteArray);
        }

        int currIndex = 2;
        do {
            currIndex++;
        } while (byteArray[currIndex] != BODY_LENGTH_END_SIGNED);

        mHandler.obtainMessage(MESSAGE_RECEIVE_IMAGE, byteArray[1], -1, Arrays.copyOfRange(byteArray, currIndex + 1, byteArray.length))
                .sendToTarget();
    }

    public void writeChatRoomName(byte[] byteArray) {
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
                    int nameLength = mmInStream.read();
                    int packetLength = 0;

                    int currPlace = 1;
                    int currDigit = mmInStream.read();
                    do {
                        packetLength += (currDigit * currPlace);
                        currPlace *= 10;
                        currDigit = mmInStream.read();
                    } while (currDigit != BODY_LENGTH_END);

                    byte[] nameBuffer = new byte[nameLength];
                    mmInStream.read(nameBuffer);
                    String name = new String(nameBuffer);

                    ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
                    for (int i = 0; i < packetLength; i++) {
                        bodyStream.write(mmInStream.read());
                    }
                    byte[] body = bodyStream.toByteArray();

                    ByteArrayOutputStream packetStream = new ByteArrayOutputStream();
                    packetStream.write(nameBuffer);
                    packetStream.write(body);
                    byte[] packet = packetStream.toByteArray();

                    switch (type) {
                        case MESSAGE_NAME:
                            mHandler.obtainMessage(MESSAGE_NAME, -1, -1, body)
                                    .sendToTarget();
                            break;
                        case MESSAGE_SEND:
                            byte[] receiveMessagePacket = buildPacket(MESSAGE_RECEIVE, name, body);
                            mHandler.obtainMessage(MESSAGE_SEND, -1, -1, receiveMessagePacket)
                                    .sendToTarget();
                            break;
                        case MESSAGE_SEND_IMAGE:
                            byte[] receiveImagePacket = buildPacket(MESSAGE_RECEIVE_IMAGE, name, body);
                            int sender = connections.indexOf(this);
                            mHandler.obtainMessage(MESSAGE_SEND_IMAGE, sender, -1, receiveImagePacket)
                                    .sendToTarget();
                            break;
                        case MESSAGE_RECEIVE:
                            mHandler.obtainMessage(MESSAGE_RECEIVE, nameLength, -1, packet)
                                    .sendToTarget();
                            break;
                        case MESSAGE_RECEIVE_IMAGE:
                            mHandler.obtainMessage(MESSAGE_RECEIVE_IMAGE, nameLength, -1, packet)
                                    .sendToTarget();
                        }
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
