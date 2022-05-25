package com.example.bluetoothleheartrate;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.util.Date;
import java.util.UUID;

public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // Scale time is in seconds since 2010-01-01
    private static final long SCALE_UNIX_TIMESTAMP_OFFSET = 1262304000;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private int count = 0;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                enableWeightCollection();
            } else if (BluetoothLeService.ACTION_NOTIFICATION_SET.equals(action)) {
                if (count < 3) {
                    enableWeightCollection();
                } else {
                    writeEnableCommand();
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            } else if (BluetoothLeService.WRITE_COMPLETE.equals(action)) {
                //readWeight();
            }
        }
    };

    private void readWeight() {
        mBluetoothLeService.readCharacteristic(getCharacteristic(GattAttributeUUIDs.WEIGHT_SERVICE, GattAttributeUUIDs.WEIGHT_CHARACTERISTIC));
    }

    private void writeEnableCommand() {
        BluetoothGattCharacteristic commandCharacteristic = getCharacteristic(GattAttributeUUIDs.WEIGHT_SERVICE, GattAttributeUUIDs.CMD_MEASUREMENT_CHARACTERISTIC);

        // magic number <- https://github.com/oliexdev/openScale/wiki/Medisana-BS444
        // send magic number to receive weight data
        long timestamp = new Date().getTime() / 1000;
        timestamp -= SCALE_UNIX_TIMESTAMP_OFFSET;
        byte[] date = Converters.toInt32Le(timestamp);

        byte[] magicBytes = new byte[] {(byte)0x02, date[0], date[1], date[2], date[3]};
        mBluetoothLeService.writeCharacteristic(commandCharacteristic, magicBytes);
    }

    private void enableWeightCollection() {
        UUID characteristic;
        if (count == 0) {
            characteristic = GattAttributeUUIDs.FEATURE_MEASUREMENT_CHARACTERISTIC;
        } else if (count == 1) {
            characteristic = GattAttributeUUIDs.WEIGHT_CHARACTERISTIC;
        } else if (count == 2) {
            characteristic = GattAttributeUUIDs.CUSTOM5_MEASUREMENT_CHARACTERISTIC;
        } else {
            return;
        }
        mBluetoothLeService.setIndicationOn(GattAttributeUUIDs.WEIGHT_SERVICE,  characteristic);
        count++;
    }

    private void clearUI() {
        //mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bpm_display);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID service, UUID characteristic) {
        BluetoothGattService gattService = mBluetoothLeService.getGattService(service);
        if (gattService == null) return null;

        BluetoothGattCharacteristic bluetoothCharacteristic = gattService.getCharacteristic(characteristic);
        if (bluetoothCharacteristic == null) {
            Log.e(TAG, "Something is wrong with the characteristics");
        }
        return bluetoothCharacteristic;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_NOTIFICATION_SET);
        intentFilter.addAction(BluetoothLeService.WRITE_COMPLETE);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
