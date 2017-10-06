

package tonyg.example.com.bleledremote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import tonyg.example.com.bleledremote.ble.BleCommManager;
import tonyg.example.com.bleledremote.ble.callbacks.BleRemoteLedCallback;
import tonyg.example.com.bleledremote.ble.callbacks.BleScanCallbackv21;
import tonyg.example.com.bleledremote.ble.BleRemoteLed;
import tonyg.example.com.bleledremote.ble.callbacks.BleScanCallbackv18;

/**
 * Connect to a BLE Device, list its GATT services
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2015-12-21
 */
public class MainActivity extends AppCompatActivity {
    /** Constants **/
    private static final String TAG = MainActivity.class.getSimpleName();
    private final static int REQUEST_ENABLE_BT = 1;

    /** Bluetooth Stuff **/
    private BleCommManager mBleCommManager;
    private BleRemoteLed mBleRemoteLed;

    /** UI Stuff **/
    private MenuItem mProgressSpinner;
    private TextView mDeviceNameTV, mDeviceAddressTV;
    private Switch mLedSwitch;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // notify when bluetooth is turned on or off
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        loadUI();

        mBleRemoteLed = new BleRemoteLed(this, mBleRemoteLedCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Unregister the bluetooth radio alerts on pause
     */
    @Override
    public void onPause() {
        super.onPause();
        stopScan();
        disconnect();
        unregisterReceiver(mReceiver);
    }



    /**
     * Load UI components
     */
    public void loadUI() {
        mDeviceNameTV = (TextView)findViewById(R.id.broadcast_name);
        mDeviceAddressTV = (TextView)findViewById(R.id.mac_address);
        mLedSwitch = (Switch)findViewById(R.id.led_switch);

        mLedSwitch.setVisibility(View.GONE);
    }


    /**
     * Create a menu
     * @param menu The menu
     * @return <b>true</b> if processed successfully
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        mProgressSpinner = menu.findItem(R.id.scan_progress_item);
        mProgressSpinner.setVisible(true);

        initializeBluetooth();

        return true;
    }



    /**
     * Initialize the Bluetooth Radio
     */
    public void initializeBluetooth() {
        try {
            mBleCommManager = new BleCommManager(this);
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize bluetooth");
            Log.e(TAG, e.getMessage());
            finish();
        }

        // should prompt user to open settings if Bluetooth is not enabled.
        if (!mBleCommManager.getBluetoothAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            startScan();
        }
    }



    /**
     * Start scanning for Peripherals
     */
    private void startScan() {
        mDeviceNameTV.setText(R.string.scanning);
        mProgressSpinner.setVisible(true);

        try {
            mBleCommManager.scanForPeripherals(mBleScanCallbackv18, mBleScanCallbackv21);
        } catch (Exception e) {
            Log.e(TAG, "Can't create Ble Device Scanner");
        }

    }


    /**
     * Event trigger when new Peripheral is discovered
     */
    public void onBlePeripheralDiscovered(BluetoothDevice bluetoothDevice) {
        // only add the device if
        // - it has a name, on
        // - doesn't already exist in our list, or
        // - is transmitting at a higher power (is closer) than an existing device
        Log.v(TAG, "discovered peripheral: " + bluetoothDevice.getName());
        boolean addDevice = false;
        if (bluetoothDevice.getName() != null) {
            if (bluetoothDevice.getName().equals(BleRemoteLed.ADVERTISED_NAME)) {
                Log.v(TAG, "found Remote Led!");
                addDevice = true;
            }
        }

        if (addDevice) {
            stopScan();
            connectToDevice(bluetoothDevice);
        }
    }


    /**
     * Stop scanning for Peripherals
     */
    public void stopScan() {
        mBleCommManager.stopScanning(mBleScanCallbackv18, mBleScanCallbackv21);
    }

    /**
     * Event trigger when BLE Scanning has stopped
     */
    public void onBleScanStopped() {
        mProgressSpinner.setVisible(false);
    }



    /**
     * Hand the Peripheral Mac Address over to the Connect Activity
     *
     * @param bluetoothDevice the MAC address of the selected Peripheral
     */
    public void connectToDevice(BluetoothDevice bluetoothDevice) {
        mDeviceNameTV.setText(R.string.connecting);
        mProgressSpinner.setVisible(true);
        try {
            mBleRemoteLed.connect(bluetoothDevice);
        } catch (Exception e) {
            mProgressSpinner.setVisible(false);
            Log.e(TAG, "Error connecting to device");
        }
    }


    /**
     * Disconnect from Peripheral
     */
    private void disconnect() {
        mBleRemoteLed.disconnect();
        // remove callbacks
        mLedSwitch.removeCallbacks(null);
        finish();
    }

    /**
     * Clear the input TextView when a Characteristic is successfully written to.
     */
    public void onBleCommandProcessed() {
        Log.v(TAG, "Remote reported success!");
        mLedSwitch.setEnabled(true);
    }

    /**
     * Problem sending the command to the Peripheral.  Show error
     */
    public void onBleCommandError() {
        Log.e(TAG, "Remote reported an error!");
        //mLedSwitch.setChecked(!mLedSwitch.isChecked());
        mLedSwitch.setEnabled(true);
        Toast.makeText(this, "", Toast.LENGTH_LONG).show();
    }

    /**
     * Bluetooth Peripheral connected.  Update UI
     */
    public void onBleConnected() {
        mProgressSpinner.setVisible(false);
        mDeviceNameTV.setText(BleRemoteLed.ADVERTISED_NAME);
        mDeviceAddressTV.setText(mBleRemoteLed.getBluetoothDevice().getAddress());
        mProgressSpinner.setVisible(false);

        // attach callbacks to the buttons and stuff
        mLedSwitch.setVisibility(View.VISIBLE);
        mLedSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //mLedSwitch.setEnabled(false);

                if (isChecked) {
                    mBleRemoteLed.turnLedOn();
                } else {
                    mBleRemoteLed.turnLedOff();
                }
            }
        });
    }

    public void onBleDisconnected() {
        mDeviceNameTV.setText("");
        mDeviceAddressTV.setText("");
        mProgressSpinner.setVisible(false);
    }

    /**
     * When the Bluetooth radio turns on, initialize the Bluetooth connection
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.v(TAG, "Bluetooth turned on");
                        initializeBluetooth();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.v(TAG, "Bluetooth turned on");
                        initializeBluetooth();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };




    /**
     * Use this callback for Android API 21 (Lollipop) or greater
     */
    private final BleScanCallbackv21 mBleScanCallbackv21 = new BleScanCallbackv21() {
        /**
         * New Peripheral discovered
         *
         * @param callbackType int: Determines how this callback was triggered. Could be one of CALLBACK_TYPE_ALL_MATCHES, CALLBACK_TYPE_FIRST_MATCH or CALLBACK_TYPE_MATCH_LOST
         * @param result a Bluetooth Low Energy Scan Result, containing the Bluetooth Device, RSSI, and other information
         */
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice bluetoothDevice = result.getDevice();
            int rssi = result.getRssi();

            onBlePeripheralDiscovered(bluetoothDevice);
        }

        /**
         * Several peripherals discovered when scanning in low power mode
         *
         * @param results List: List of scan results that are previously scanned.
         */
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice bluetoothDevice = result.getDevice();
                int rssi = result.getRssi();

                onBlePeripheralDiscovered(bluetoothDevice);
            }
        }

        /**
         * Scan failed to initialize
         *
         * @param errorCode	int: Error code (one of SCAN_FAILED_*) for scan failure.
         */
        @Override
        public void onScanFailed(int errorCode) {
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    Log.e(TAG, "Fails to start scan as BLE scan with the same settings is already started by the app.");
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    Log.e(TAG, "Fails to start scan as app cannot be registered.");
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG, "Fails to start power optimized scan as this feature is not supported.");
                    break;
                default: // SCAN_FAILED_INTERNAL_ERROR
                    Log.e(TAG, "Fails to start scan due an internal error");

            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleScanStopped();
                }
            });
        }

        /**
         * Scan completed
         */
        public void onScanComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleScanStopped();
                }
            });

        }
    };

    /**
     * Use this callback for Android API 18, 19, and 20 (before Lollipop)
     */
    public final BleScanCallbackv18 mBleScanCallbackv18 = new BleScanCallbackv18() {
        /**
         * New Peripheral discovered
         * @param bluetoothDevice The Peripheral Device
         * @param rssi The Peripheral's RSSI indicating how strong the radio signal is
         * @param scanRecord Other information about the scan result
         */
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
            onBlePeripheralDiscovered(bluetoothDevice);
        }

        /**
         * Scan completed
         */
        @Override
        public void onScanComplete() {
            runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBleScanStopped();
                    }
                });
        }

    };


    public final BleRemoteLedCallback mBleRemoteLedCallback = new BleRemoteLedCallback() {

        @Override
        public void connected() {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleConnected();
                }
            });
        }

        @Override
        public void disconnected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleDisconnected();
                }
            });
        }

        @Override
        public void commandWritten() {
        }

        @Override
        public void ledStateChanged(final int ledState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleCommandProcessed();
                }
            });

        }

        @Override
        public void ledError() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleCommandError();
                }
            });

        }
    };
}
