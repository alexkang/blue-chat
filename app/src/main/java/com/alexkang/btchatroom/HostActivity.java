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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import com.alexkang.btchatroom.R;

import java.io.IOException;
import java.util.UUID;

public class HostActivity extends Activity {

    public static final int REQUEST_DISCOVERABLE = 1;
    public static final String UUID = "28286a80-137b-11e4-bbe8-0002a5d5c51b";

    private String name;
    private BluetoothAdapter mBluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);
    }

    @Override
    public void onStart() {
        super.onStart();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        final EditText nameInput = new EditText(this);
        nameInput.setSingleLine();
        nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter your Chatroom name");
        builder.setView(nameInput);
        builder.setPositiveButton("Okay!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                name = nameInput.getText().toString();

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

    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(name, java.util.UUID.fromString(UUID));
            } catch (IOException e) {}

            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket;

            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }

                if (socket != null) {
                    manageSocket(socket);
                }
            }
        }
    }

}
