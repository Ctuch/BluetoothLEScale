package com.example.bluetoothleheartrate;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.welie.blessed.BluetoothCentralManager;
import com.welie.blessed.BluetoothCentralManagerCallback;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.BluetoothPeripheralCallback;
import com.welie.blessed.GattStatus;
import com.welie.blessed.HciStatus;
import com.welie.blessed.WriteType;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = "Bluetooth_LE_Service";

    private BluetoothCentralManager mCentralManager;
    private BluetoothPeripheral mBtPeripheral;
    private Handler callbackBtHandler;
    private Context mContext;
    private String mBluetoothDeviceAddress;


    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String WRITE_COMPLETE =
            "com.example.bluetooth.le.WRITE_COMPLETE";
    public final static String ACTION_NOTIFICATION_SET =
            "com.example.bluetooth.le.ACTION_NOTIFICATION_SET";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public BluetoothLeService(Context context) {
        this.mContext = context;
        this.mCentralManager = new BluetoothCentralManager(context, mBluetoothCentralCallback, new Handler(Looper.getMainLooper()));
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothPeripheralCallback mPeripheralCallback = new BluetoothPeripheralCallback() {

        @Override
        public void onServicesDiscovered(BluetoothPeripheral peripheral) {
            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
        }

        @Override
        public void onNotificationStateUpdate(BluetoothPeripheral peripheral, BluetoothGattCharacteristic characteristic, GattStatus status) {
            //TODO: check for CCCD
            if (status.value == GATT_SUCCESS) {
                if (peripheral.isNotifying(characteristic)) {
                    broadcastUpdate(ACTION_NOTIFICATION_SET);
                }
            } else {
                Log.e(TAG, "ERROR: Changing notification state failed for " + characteristic.getUuid());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothPeripheral peripheral, byte[] value, BluetoothGattCharacteristic characteristic, GattStatus status) {
            Log.i(TAG, "Characteristic has been written, status: " + (status.value == GATT_SUCCESS ? "success" : "failure"));
        }

        @Override
        public void onCharacteristicUpdate(final BluetoothPeripheral peripheral, byte[] value, final BluetoothGattCharacteristic characteristic, GattStatus status) {
            if (status.value == GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, value);
            } else {
                Log.e(TAG, "failed to get the update from the characteristic " + status.value);
            }

        }
    };

    private final BluetoothCentralManagerCallback mBluetoothCentralCallback = new BluetoothCentralManagerCallback() {
        @Override
        public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
            String intentAction = ACTION_GATT_CONNECTED;
            mConnectionState = STATE_CONNECTED;
            broadcastUpdate(intentAction);
            Log.i(TAG, "Connected to GATT server.");
            // Attempts to discover services after successful connection.
            Log.i(TAG, "Attempting to start service discovery:" +
                    mCentralManager.discoverServices());
            mBtPeripheral = peripheral;
        }

        @Override
        public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
            Log.e(TAG, "connection " + peripheral.getName() + " failed with status " + status.value);
            broadcastUpdate(ACTION_GATT_DISCONNECTED);
        }

        @Override
        public void onDisconnectedPeripheral(final BluetoothPeripheral peripheral, HciStatus status) {
            String intentAction = ACTION_GATT_DISCONNECTED;
            mConnectionState = STATE_DISCONNECTED;
            Log.i(TAG, "Disconnected from GATT server.");
            if (status.value == 133) {
                mCentralManager.connectPeripheral(peripheral, mPeripheralCallback);
            }
            broadcastUpdate(intentAction);
        }
    };


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic, byte[] value) {
        final Intent intent = new Intent(action);

        Log.d(TAG, "broadcasting an update with action: " + action);
        //TODO: format weight characteristic data
        if (GattAttributeUUIDs.WEIGHT_CHARACTERISTIC.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Log.d(TAG, String.format("Received value: %s", stringBuilder));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }

        sendBroadcast(intent);

    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    public boolean initialize() {
        if (mCentralManager == null) {
            mCentralManager = new BluetoothCentralManager(mContext, mBluetoothCentralCallback, new Handler(Looper.getMainLooper()));
        }
        return true;
    }

    public boolean connect(final String address) {
        BluetoothPeripheral peripheral = mCentralManager.getPeripheral(address);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Try to connect to BLE device " + peripheral.getAddress());

                mCentralManager.connectPeripheral(peripheral, mPeripheralCallback);
                mBluetoothDeviceAddress = address;
                mConnectionState = STATE_CONNECTING;
            }
        }, 1000);

        return true;
    }

    @SuppressLint("MissingPermission")
    public void close() {
        if (mCentralManager == null) {
            return;
        }
        mCentralManager.close();
        mCentralManager = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    @SuppressLint("MissingPermission")
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        boolean success = mBtPeripheral.readCharacteristic(characteristic);
        if (!success) {
            Log.e(TAG, "read was not successful");
        }
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] bytes) {
        mBtPeripheral.writeCharacteristic(characteristic, bytes, WriteType.WITH_RESPONSE);
    }

    public void setIndicationOn(UUID service, UUID characteristic) {
        if(mBtPeripheral.getService(service) != null) {
            BluetoothGattCharacteristic gattCharacteristic = mBtPeripheral.getCharacteristic(service, characteristic);
            mBtPeripheral.setNotify(gattCharacteristic, true);
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBtPeripheral == null) return null;

        return mBtPeripheral.getServices();
    }

    public BluetoothGattService getGattService(UUID uuid) {
        if (mBtPeripheral == null) return null;

        return mBtPeripheral.getService(uuid);
    }

    protected boolean haveCharacteristic(UUID service, UUID characteristic) {
        return mBtPeripheral.getCharacteristic(service, characteristic) != null;
    }
}
