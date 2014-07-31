package com.alexkang.bluechat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


public class MainActivity extends Activity {

    public static final int REQUEST_ENABLE_BT = 0;
    public static final String UUID = "28286a80-137b-11e4-bbe8-0002a5d5c51b";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button mHostButton = (Button) findViewById(R.id.host_button);
        Button mJoinButton = (Button) findViewById(R.id.join_button);

        mHostButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hostRoom();
            }
        });

        mJoinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                joinRoom();
            }
        });
    }

    private void hostRoom() {
        Intent i = new Intent(this, HostActivity.class);
        startActivity(i);
    }

    private void joinRoom() {
        Intent i = new Intent(this, ClientActivity.class);
        startActivity(i);
    }

    @Override
    public void onResume() {
        super.onResume();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device, now exiting.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

}
