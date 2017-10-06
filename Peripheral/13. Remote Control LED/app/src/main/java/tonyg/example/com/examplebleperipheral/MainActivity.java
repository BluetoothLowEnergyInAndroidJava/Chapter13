package tonyg.example.com.examplebleperipheral;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import tonyg.example.com.examplebleperipheral.ble.BleRemoteLed;
import tonyg.example.com.examplebleperipheral.ble.callbacks.BleRemoteLedCallback;


/**
 * Create a Bluetooth Peripheral.  Android 5 required
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2015-12-21
 */
public class MainActivity extends AppCompatActivity {
    /** Constants **/
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;

    /** Bluetooth Stuff **/
    private BleRemoteLed mBleRemoteLed;

    /** Camera Stuff **/
    private boolean mIsFlashAvailable;
    private CameraManager mCameraManager;
    private String mCameraId;

    /** UI Stuff **/
    private TextView mAdvertisingNameTV;
    private Switch mBluetoothOnSwitch,
            mCentralConnectedSwitch,
            mLedStateSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // notify when bluetooth is turned on or off
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBleBroadcastReceiver, filter);

        mIsFlashAvailable = getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        if (mIsFlashAvailable) {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            try {
                mCameraId = mCameraManager.getCameraIdList()[0];
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        loadUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        // stop advertising when the activity pauses
        mBleRemoteLed.stopAdvertising();
        onLedOffCommand();
    }

    @Override
    public void onResume() {
        super.onResume();
        initializeBluetooth();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBleBroadcastReceiver);
    }

    /**
     * Load UI components
     */
    public void loadUI() {
        mAdvertisingNameTV = (TextView)findViewById(R.id.advertising_name);
        mBluetoothOnSwitch = (Switch)findViewById(R.id.bluetooth_on);
        mCentralConnectedSwitch = (Switch)findViewById(R.id.central_connected);
        mLedStateSwitch = (Switch)findViewById(R.id.led_state);

        mAdvertisingNameTV.setText(BleRemoteLed.ADVERTISING_NAME);
    }

    /**
     * Initialize the Bluetooth Radio
     */
    public void initializeBluetooth() {
        // reset connection variables
        try {
            mBleRemoteLed = new BleRemoteLed(this, mBlePeripheralCallback);
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize bluetooth");
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            finish();
        }

        Log.v(TAG, "bluetooth switch: "+mBluetoothOnSwitch);
        Log.v(TAG, "mBleRemoteLed: "+mBleRemoteLed);
        Log.v(TAG, "peripheral: "+mBleRemoteLed.getBlePeripheral());

        mBluetoothOnSwitch.setChecked(mBleRemoteLed.getBlePeripheral().getBluetoothAdapter().isEnabled());

        // should prompt user to open settings if Bluetooth is not enabled.
        if (!mBleRemoteLed.getBlePeripheral().getBluetoothAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            startAdvertising();
        }

    }

    /**
     * Start advertising Peripheral
     */
    public void startAdvertising() {
        try {
            mBleRemoteLed.startAdvertising();
        } catch (Exception e) {
            Log.e(TAG, "error starting advertising: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Event trigger when Central has connected
     *
     * @param bluetoothDevice
     */
    public void onBleCentralConnected(final BluetoothDevice bluetoothDevice) {
        mCentralConnectedSwitch.setChecked(true);
    }

    /**
     * Event trigger when Central has disconnected
     * @param bluetoothDevice
     */
    public void onBleCentralDisconnected(final BluetoothDevice bluetoothDevice) {
        mCentralConnectedSwitch.setChecked(false);
    }

    /**
     * Led turned on remotely
     */
    public void onLedOnCommand() {
        mLedStateSwitch.setChecked(true);

        if (mIsFlashAvailable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mCameraManager.setTorchMode(mCameraId, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Led turn off remotely
     */
    public void onLedOffCommand() {
        mLedStateSwitch.setChecked(false);
        if (mIsFlashAvailable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mCameraManager.setTorchMode(mCameraId, false);
                }
            } catch (Exception e) {
                Log.d(TAG, "could not open camera flash: " + e.getMessage());
                e.printStackTrace();
            }
        }

    }

    /**
     * When the Bluetooth radio turns on, initialize the Bluetooth connection
     */
    private final BroadcastReceiver mBleBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.v(TAG, "Bluetooth turned off");
                        initializeBluetooth();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.v(TAG, "Bluetooth turned on");
                        startAdvertising();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };


    /**
     * Respond to changes to the Bluetooth Peripheral state
     */
    private final BleRemoteLedCallback mBlePeripheralCallback = new BleRemoteLedCallback() {

        public void onCentralConnected(final BluetoothDevice bluetoothDevice) {
            Log.v(TAG, "Central connected");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleCentralConnected(bluetoothDevice);
                }
            });

        }
        public void onCentralDisconnected(final BluetoothDevice bluetoothDevice) {
            Log.v(TAG, "Central disconnected");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleCentralDisconnected(bluetoothDevice);
                }
            });
        }

        @Override
        public void onLedTurnedOn() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onLedOnCommand();
                }
            });
        }

        @Override
        public void onLedTurnedOff() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onLedOffCommand();
                }
            });

        }
    };
}
