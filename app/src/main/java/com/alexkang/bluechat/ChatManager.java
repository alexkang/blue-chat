package com.alexkang.bluechat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

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
    private int currItem = 0;

    private Activity mActivity;
    private ListView mMessageFeed;
    private LinearLayout mSendBar;
    private EditText mMessageText;

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
        mSendBar = (LinearLayout) mActivity.findViewById(R.id.send_bar);
        mMessageText = (EditText) mActivity.findViewById(R.id.message);
        this.isHost = isHost;

        if (isHost) {
            connections = new ArrayList<ConnectedThread>();
        }

        mMessageText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                currItem = 0;
                InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mMessageText.getWindowToken(), 0);
                mMessageText.clearFocus();
                return true;
            }
        });

        mMessageList = new ArrayList<MessageBox>();
        mFeedAdapter = new MessageFeedAdapter(mActivity, mMessageList);
        mMessageFeed.setAdapter(mFeedAdapter);

        View footer = new View(mActivity);
        footer.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 225));
        footer.setBackgroundColor(mActivity.getResources().getColor(android.R.color.transparent));

        mMessageFeed.addFooterView(footer, null, false);
        mMessageFeed.setOnScrollListener(new AbsListView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {

            }

            @Override
            public void onScroll(AbsListView absListView, int firstItem, int i1, int i2) {
                if (!mMessageText.isFocused() && Math.abs(currItem - firstItem) >= 2) {
                    if (firstItem > currItem ||
                            mMessageFeed.getLastVisiblePosition() == mMessageFeed.getCount() - 1) {
                        mSendBar.setVisibility(View.VISIBLE);
                    } else if (firstItem < currItem) {
                        mSendBar.setVisibility(View.INVISIBLE);
                    }

                    currItem = firstItem;
                }
            }
        });
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
                    // TODO: Handle large data transfers. Bluetooth packets have a 1024 bytes maximum.

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
            } catch (IOException e) {
                System.err.println("Failed to write bytes");
                System.err.println(e.toString());
            }
        }

    }

}
