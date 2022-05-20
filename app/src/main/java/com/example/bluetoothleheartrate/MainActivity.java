package com.example.bluetoothleheartrate;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback;

import java.util.ArrayList;

//TODO: fix depreciated parts of UI with fragments and new design
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_CODE_BLUETOOTH = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final String TAG = "Main_Activity";
    private static final long SCAN_PERIOD = 30000; // 30 seconds

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private DeviceListAdapter leDeviceListAdapter;
    private Handler handler;
    private boolean scanning;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.ble_device_list);
        leDeviceListAdapter = new DeviceListAdapter();
        recyclerView.setAdapter(leDeviceListAdapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);


        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        handler = new Handler(getMainLooper());

        checkPermissionLocation();
        checkPermissionBluetooth();
        //TODO: handle permissions accepted
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //TODO: is this right?
        scanLeDevice();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice();
        leDeviceListAdapter.clear();
    }

    private void checkPermissionLocation() {
        int check = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION);

        if (check != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    PERMISSION_REQUEST_CODE_LOCATION);
        }
    }

    private void checkPermissionBluetooth() {
        int checkConnect = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT);

        int checkScan = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN);

        if (checkConnect != PackageManager.PERMISSION_GRANTED || checkScan != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    PERMISSION_REQUEST_CODE_BLUETOOTH);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void scanLeDevice() {
        if (!scanning) {

            //stop scanning after SCAN_PERIOD time
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(leScanCallback); //can filter this by device -> TODO: get only heart rate
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    leDeviceListAdapter.addDevice(result.getDevice());
                    leDeviceListAdapter.notifyDataSetChanged();
                }
            };

    private class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {

        // Adapter for holding devices found through scanning.
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public DeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        @NonNull
        @Override
        public DeviceListAdapter.DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = mInflator.inflate(R.layout.listitem_device, parent, false);
            DeviceViewHolder holder = new DeviceViewHolder(view);

            holder.deviceAddress = view.findViewById(R.id.device_address);
            holder.deviceName = view.findViewById(R.id.device_name);
            view.setTag(holder);

            return holder;
        }

        @SuppressLint("MissingPermission")
        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device) && device.getName() != null) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceListAdapter.DeviceViewHolder holder, int position) {
            BluetoothDevice device = mLeDevices.get(position); //TODO check this int

            @SuppressLint("MissingPermission")
            final String deviceName = device.getName();

            if (deviceName != null && deviceName.length() > 0)
                holder.deviceName.setText(deviceName);
            else
                holder.deviceName.setText(R.string.unknown_device);
            holder.deviceAddress.setText(device.getAddress());

            holder.setListener();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getItemCount() {
            return mLeDevices.size();
        }

        class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            TextView deviceName;
            TextView deviceAddress;
            LinearLayout layout;

            public DeviceViewHolder(@NonNull View itemView) {
                super(itemView);
                layout = itemView.findViewById(R.id.device_list_item);
            }

            public void setListener() {
                layout.setOnClickListener(DeviceViewHolder.this);
            }
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View v) {
                final BluetoothDevice device = getDevice(getAdapterPosition());
                if (device == null) {
                    return;
                }
                final Intent intent = new Intent(getApplicationContext(), DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                if (scanning) {
                    bluetoothLeScanner.stopScan(leScanCallback);
                    scanning = false;
                }
                startActivity(intent);
            }
        }
    }
}