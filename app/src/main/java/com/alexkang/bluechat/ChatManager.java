package com.alexkang.bluechat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.widget.ListView;
import android.widget.Toast;

import java.io.FilterInputStream;
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
                    int imageSenderLength = msg.arg1;

                    String imageWholeMessage = new String(imageBuffer);
                    String imageSenderName = imageWholeMessage.substring(0, imageSenderLength);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBuffer, imageSenderLength, imageBuffer.length - imageSenderLength);

                    MessageBox imageBox = new MessageBox(imageSenderName, bitmap, new Date(), true);
                    addMessage(imageBox);
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
            } catch (IOException e) {
                Toast.makeText(mActivity, "Failed to reconnect, now exiting", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(mActivity, MainActivity.class);
                mActivity.startActivity(i);
                mActivity.finish();
            }
        }
    }

    public void write(byte[] byteArray) {
        if (isHost) {
            for (ConnectedThread connection : connections) {
                connection.write(byteArray);
            }

            mHandler.obtainMessage(byteArray[0], byteArray[2], -1, Arrays.copyOfRange(byteArray, 3, byteArray.length))
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
                    int packetLength = mmInStream.read();
                    int nameLength = mmInStream.read();

                    if (type == MESSAGE_SEND || type == MESSAGE_SEND_IMAGE) {
                        // We add 3 bytes to reinsert the type, packet length, and name length.
                        byte[] buffer = new byte[packetLength + 3];
                        buffer[0] = (byte) type;
                        buffer[1] = (byte) packetLength;
                        buffer[2] = (byte) nameLength;

                        mmInStream.read(buffer, 3, packetLength);
                        mHandler.obtainMessage(type, -1, -1, buffer)
                                .sendToTarget();
                    } else if (type == MESSAGE_RECEIVE || type == MESSAGE_NAME) {
                        byte[] buffer = new byte[packetLength];

                        mmInStream.read(buffer);
                        mHandler.obtainMessage(type, nameLength, -1, buffer)
                                .sendToTarget();
                    } else if (type == MESSAGE_RECEIVE_IMAGE) {
                        byte[] buffer = new byte[packetLength];

                        mmInStream.read(buffer);
                        mHandler.obtainMessage(type, nameLength, -1, buffer)
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

    static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break;  // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }

}
