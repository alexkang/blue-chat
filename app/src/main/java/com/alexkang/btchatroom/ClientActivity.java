package com.alexkang.btchatroom;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.alexkang.btchatroom.R;

import java.io.IOException;

public class ClientActivity extends Activity {

    private EditText mMessage;
    private Button mSendButton;

    private BluetoothAdapter mBluetoothAdapter;

    private ChatManager mChatManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice mHost = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                new ConnectThread(mHost).start();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        mMessage = (EditText) findViewById(R.id.message);
        mSendButton = (Button) findViewById(R.id.send);
        mChatManager = new ChatManager(this, false);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = mMessage.getText().toString();
                mChatManager.write(message, ChatManager.MESSAGE_SEND);

                mMessage.setText("");
            }
        });
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
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    private void manageSocket(BluetoothSocket socket) {
        Toast.makeText(this, socket.getRemoteDevice().getName(), Toast.LENGTH_SHORT).show();
        mChatManager.startConnection(socket);
    }

    private class ConnectThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;

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
