package com.alexkang.btchatroom;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.alexkang.btchatroom.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class HostActivity extends Activity {

    public static final int REQUEST_DISCOVERABLE = 1;

    private EditText mMessage;
    private Button mSendButton;

    private String mChatRoomName;
    private BluetoothAdapter mBluetoothAdapter;

    private ChatManager mChatManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);

        mMessage = (EditText) findViewById(R.id.message);
        mSendButton = (Button) findViewById(R.id.send);
        mChatManager = new ChatManager(this, true);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = mMessage.getText().toString();
                mChatManager.write(message, ChatManager.MESSAGE_RECEIVE);

                mMessage.setText("");
            }
        });

        initializeBluetooth();
    }

    public void initializeBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final EditText nameInput = new EditText(this);
        nameInput.setSingleLine();
        nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter your ChatRoom name");
        builder.setView(nameInput);
        builder.setPositiveButton("Okay!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                mChatRoomName = nameInput.getText().toString();

                if (getActionBar() != null) {
                    getActionBar().setTitle(mChatRoomName);
                }

                Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivityForResult(i, REQUEST_DISCOVERABLE);
            }
        });
        builder.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_CANCELED && requestCode == REQUEST_DISCOVERABLE) {
            new AcceptThread().start();
            Toast.makeText(this, "Searching for users...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Something went wrong, now exiting.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void manageSocket(BluetoothSocket socket) {
        Toast.makeText(this, socket.getRemoteDevice().getName(), Toast.LENGTH_SHORT).show();
        mChatManager.startConnection(socket);
        mChatManager.write(mChatRoomName, ChatManager.MESSAGE_NAME);
    }

    private class AcceptThread extends Thread {

        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.
                        listenUsingRfcommWithServiceRecord(
                                mChatRoomName, java.util.UUID.fromString(MainActivity.UUID)
                        );
            } catch (IOException e) {}

            mmServerSocket = tmp;
        }

        public void run() {
            while (true) {
                final BluetoothSocket socket;

                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }

                if (socket != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            manageSocket(socket);
                        }
                    });
                }
            }
        }

    }

}
