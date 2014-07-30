package com.alexkang.bluechat;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

public class ClientActivity extends Activity {

    public static final int PICK_IMAGE = 2;

    private EditText mMessage;
    private Button mAttachButton;
    private Button mSendButton;
    private ProgressDialog mProgressDialog;

    private BluetoothAdapter mBluetoothAdapter;

    private ChatManager mChatManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                boolean validHost = false;
                BluetoothDevice mHost = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                ParcelUuid[] uuids = mHost.getUuids();

                for (ParcelUuid uuid : uuids) {
                    if (uuid.getUuid().equals(UUID.fromString(MainActivity.UUID))) {
                        validHost = true;
                    }
                }

                if (validHost || true) {
                    new ConnectThread(mHost).start();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        mMessage = (EditText) findViewById(R.id.message);
        mAttachButton = (Button) findViewById(R.id.attach);
        mSendButton = (Button) findViewById(R.id.send);
        mChatManager = new ChatManager(this, false);
        mProgressDialog = new ProgressDialog(this);

        mAttachButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadAttachment();
            }
        });

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        mProgressDialog.setMessage("Looking for ChatRoom...");
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                finish();
            }
        });
        mProgressDialog.show();
    }

    private void sendMessage() {
        byte[] byteArray;

        if (mMessage.getText().toString().length() == 0) {
            return;
        }

        try {
            byte[] messageBytes = mMessage.getText().toString().getBytes();
            byte[] senderBytes = mBluetoothAdapter.getName().getBytes();

            ByteArrayOutputStream output = new ByteArrayOutputStream(senderBytes.length + messageBytes.length + 3);
            output.write(ChatManager.MESSAGE_SEND);
            output.write(senderBytes.length + messageBytes.length);
            output.write(senderBytes.length);
            output.write(senderBytes);
            output.write(messageBytes);

            byteArray = output.toByteArray();
        } catch (Exception e) {
            return;
        }

        mChatManager.write(byteArray);
        mMessage.setText("");
    }

    private void uploadAttachment() {
        Intent i = new Intent();
        i.setType("image/*");
        i.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(i, "Select Picture"), PICK_IMAGE);
    }

    private void sendImage(Bitmap bitmap) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] senderBytes = mBluetoothAdapter.getName().getBytes();

            output.write(ChatManager.MESSAGE_SEND_IMAGE);
            output.write(senderBytes.length);
            output.write(senderBytes.length);
            output.write(senderBytes);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 15, output);

            byte[] byteArray = output.toByteArray();
            byteArray[1] = (byte) (byteArray.length - 3);

            mChatManager.write(byteArray);
        } catch (Exception e) {}
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == MainActivity.REQUEST_ENABLE_BT) {
            Toast.makeText(this, "Bluetooth successfully enabled!", Toast.LENGTH_SHORT).show();
            mChatManager.restartConnection();
        } else if (resultCode == RESULT_OK && requestCode == PICK_IMAGE) {
            Uri image = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };
            Cursor cursor = getContentResolver().query(image, filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            sendImage(BitmapFactory.decodeFile(picturePath));
        } else {
            Toast.makeText(this, "Something went wrong, now exiting.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);

        registerReceiver(mReceiver, filter);
        mBluetoothAdapter.startDiscovery();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(i, MainActivity.REQUEST_ENABLE_BT);
        } else {
            mChatManager.restartConnection();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        mBluetoothAdapter.disable();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    private void manageSocket(BluetoothSocket socket) {
        mChatManager.startConnection(socket);
        mProgressDialog.dismiss();
    }

    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(
                        java.util.UUID.fromString(MainActivity.UUID));
            } catch (IOException e) {}

            mmSocket = tmp;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    return;
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    manageSocket(mmSocket);
                }
            });
        }

    }

}
