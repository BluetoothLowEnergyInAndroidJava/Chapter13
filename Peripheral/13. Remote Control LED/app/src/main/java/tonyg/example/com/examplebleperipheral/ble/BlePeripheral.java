package tonyg.example.com.examplebleperipheral.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import tonyg.example.com.examplebleperipheral.ble.callbacks.BlePeripheralCallback;

import static android.content.Context.BATTERY_SERVICE;

/**
 * This class creates a local Bluetooth Peripheral
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2016-03-06
 */
public class BlePeripheral {

    /** Constants **/
    private static final String TAG = BlePeripheral.class.getSimpleName();

    private static final int BATTERY_STATUS_CHECK_TIME_MS = 5*50*1000; // 5 minutes

    /** Peripheral and GATT Profile **/
    private String mPeripheralAdvertisingName;

    public static final UUID NOTIFY_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Minimal GATT Profile UUIDs
    public static final UUID DEVICE_INFORMATION_SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");

    public static final UUID DEVICE_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static final UUID SERIAL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002a04-0000-1000-8000-00805f9b34fb");

    public static final UUID BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    public static final String CHARSET = "ASCII";
    public static final int MAX_ADVERTISING_NAME_BYTE_LENGTH = 20;

    /** Advertising settings **/

    // advertising mode
    int mAdvertisingMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;

    // transmission power mode
    int mTransmissionPower = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;

    /** Callback Handlers **/
    public BlePeripheralCallback mBlePeripheralCallback;

    /** Bluetooth Stuff **/
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothAdvertiser;
    private BluetoothManager mBluetoothManager;
    private BluetoothDevice mConnectedCentral;

    private BluetoothGattServer mGattServer;
    private BluetoothGattService mDeviceInformationService, mBatteryLevelService;
    private BluetoothGattCharacteristic mDeviceNameCharacteristic,
            mModelNumberCharacteristic,
            mSerialNumberCharacteristic,
            mBatteryLevelCharactersitic;

    private Context mContext;
    private String mModelNumber = "";
    private String mSerialNumber = "";

    /**
     * Construct a new Peripheral
     *
     * @param context The Application Context
     * @param blePeripheralCallback The callback handler that interfaces with this Peripheral
     * @throws Exception Exception thrown if Bluetooth is not supported
     */
    public BlePeripheral(final Context context, BlePeripheralCallback blePeripheralCallback) throws Exception {
        mBlePeripheralCallback = blePeripheralCallback;
        mContext = context;

        // make sure Android device supports Bluetooth Low Energy
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            throw new Exception("Bluetooth Not Supported");
        }

        // get a reference to the Bluetooth Manager class, which allows us to talk to talk to the BLE radio
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        mGattServer = mBluetoothManager.openGattServer(context, mGattServerCallback);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // Beware: this function doesn't work on some systems
        if(!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            throw new Exception ("Peripheral mode not supported");
        }

        mBluetoothAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

        // Use this method instead for better support
        if (mBluetoothAdvertiser == null) {
            throw new Exception ("Peripheral mode not supported");
        }
    }

    /**
     * Get the system Bluetooth Adapter
     *
     * @return BluetoothAdapter
     */
    public BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    /**
     * Get the GATT Server
     */
    public BluetoothGattServer getGattServer() {
        return mGattServer;
    }

    /**
     * Get the model number
     */
    public String getModelNumber() {
        return mModelNumber;
    }

    /** set the model number
     *
     */
    public void setModelNumber(String modelNumber) {
        mModelNumber = modelNumber;
    }

    /**
     * Get the serial number
     */
    public String getSerialNumber() {
        return mSerialNumber;
    }

    /**
     * set the serial number
     */
    public void setSerialNumber(String serialNumber) {
        mSerialNumber = serialNumber;
    }

    /**
     * Get the actual battery level
     */
    public int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager)mContext.getSystemService(BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * Set up the GATT profile
     */
    public void setupDevice() {
        // build Characteristics
        mDeviceInformationService = new BluetoothGattService(DEVICE_INFORMATION_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        mDeviceNameCharacteristic = new BluetoothGattCharacteristic(
                DEVICE_NAME_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        mModelNumberCharacteristic = new BluetoothGattCharacteristic(
                MODEL_NUMBER_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        mSerialNumberCharacteristic = new BluetoothGattCharacteristic(
                SERIAL_NUMBER_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        mBatteryLevelService = new BluetoothGattService(BATTERY_LEVEL_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        mBatteryLevelCharactersitic = new BluetoothGattCharacteristic(
                BATTERY_LEVEL_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

        // add Notification support to Characteristic
        BluetoothGattDescriptor notifyDescriptor = new BluetoothGattDescriptor(BlePeripheral.NOTIFY_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        mBatteryLevelCharactersitic.addDescriptor(notifyDescriptor);

        mDeviceInformationService.addCharacteristic(mDeviceNameCharacteristic);
        mDeviceInformationService.addCharacteristic(mModelNumberCharacteristic);
        mDeviceInformationService.addCharacteristic(mSerialNumberCharacteristic);

        mBatteryLevelService.addCharacteristic(mBatteryLevelCharactersitic);

        // put in fake values for the Characteristic.
        mModelNumberCharacteristic.setValue(mModelNumber);
        mSerialNumberCharacteristic.setValue(mSerialNumber);

        // add Services to Peripheral
        mGattServer.addService(mDeviceInformationService);
        mGattServer.addService(mBatteryLevelService);

        // update the battery level every BATTERY_STATUS_CHECK_TIME_MS milliseconds
        TimerTask updateBatteryTask = new TimerTask() {
            @Override
            public void run() {
                mBatteryLevelCharactersitic.setValue(getBatteryLevel(), BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                if (mConnectedCentral != null) {
                    mGattServer.notifyCharacteristicChanged(mConnectedCentral, mBatteryLevelCharactersitic, true);
                }
            }
        };
        Timer randomStringTimer = new Timer();
        // schedule the battery update and run it once immediately
        randomStringTimer.schedule(updateBatteryTask, 0, BATTERY_STATUS_CHECK_TIME_MS);

    }

    /**
     * Set the Advertising name of the Peripheral
     *
     * @param peripheralAdvertisingName
     */
    public void setPeripheralAdvertisingName(String peripheralAdvertisingName) throws Exception {
        mPeripheralAdvertisingName = peripheralAdvertisingName;
        mDeviceNameCharacteristic.setValue(mPeripheralAdvertisingName);
        int advertisingNameByteLength = mPeripheralAdvertisingName.getBytes(CHARSET).length;
        if (advertisingNameByteLength > MAX_ADVERTISING_NAME_BYTE_LENGTH) {
            throw new Exception("Advertising name too long.  Must be less than "+MAX_ADVERTISING_NAME_BYTE_LENGTH+" bytes");
        }
    }

    /**
     * Get the Advertising name of the Peripheral
     */
    public String getPeripheralAdvertisingName() {
        return mPeripheralAdvertisingName;
    }

    /**
     * Set the Transmission Power mode
     */
    public void setTransmissionPower(int transmissionPower) {
        mTransmissionPower = transmissionPower;
    }

    /**
     * Set the advertising mode
     */
    public void setAdvertisingMode(int advertisingMode) {
        mAdvertisingMode = advertisingMode;
    }

    /**
     * Add a Service to the GATT Profile
     *
     * @param service the Service to add
     */
    public void addService(BluetoothGattService service) {
        mGattServer.addService(service);
    }

    /**
     * Build the Advertising Data, including the transmission power, advertising name, and Services
     *
     * @return AdvertiseData
     */
    private AdvertiseData buildAdvertisingData() {
        AdvertiseData.Builder advertiseBuilder = new AdvertiseData.Builder();

        // set advertising name
        if (mPeripheralAdvertisingName != null) {
            advertiseBuilder.setIncludeDeviceName(true);
            mBluetoothAdapter.setName(mPeripheralAdvertisingName);
        }

        // add Services to Advertising Data
        List<BluetoothGattService> services = mGattServer.getServices();
        for (BluetoothGattService service: services) {
            advertiseBuilder.addServiceUuid(new ParcelUuid(service.getUuid()));
        }

        return advertiseBuilder.build();

    }

    /**
     * Build Advertise settings with transmission power and advertise speed
     *
     * @return AdvertiseSettings for Bluetooth Advertising
     */
    private AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(mAdvertisingMode);
        settingsBuilder.setTxPowerLevel(mTransmissionPower);

        // There's no need to connect to a Peripheral that doesn't host a Gatt profile
        if (mGattServer != null) {
            settingsBuilder.setConnectable(true);
        } else {
            settingsBuilder.setConnectable(false);
        }

        return settingsBuilder.build();

    }

    /**
     * Start Advertising
     *
     * @throws Exception Exception thrown if Bluetooth Peripheral mode is not supported
     */
    public void startAdvertising() {
        AdvertiseSettings advertiseSettings = buildAdvertiseSettings();
        AdvertiseData advertiseData = buildAdvertisingData();

        // begin advertising
        mBluetoothAdvertiser.startAdvertising( advertiseSettings, advertiseData, mAdvertiseCallback );
    }

    /**
     * Stop advertising
     */
    public void stopAdvertising() {
        if (mBluetoothAdvertiser != null) {
            mBluetoothAdvertiser.stopAdvertising(mAdvertiseCallback);
            mBlePeripheralCallback.onAdvertisingStopped();
        }
    }

    /**
     * Check if a Characetristic supports write permissions
     * @return Returns <b>true</b> if property is writable
     */
    public static boolean isCharacteristicWritable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    /**
     * Check if a Characetristic supports write wuthout response permissions
     * @return Returns <b>true</b> if property is writable
     */
    public static boolean isCharacteristicWritableWithoutResponse(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
    }

    /**
     * Check if a Characetristic supports write with permissions
     * @return Returns <b>true</b> if property is writable
     */
    public static boolean isCharacteristicWritableWithResponse(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0;
    }

    /**
     * Check if a Characteristic supports Notifications
     *
     * @return Returns <b>true</b> if property is supports notification
     */
    public static boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }

    /**
     * Check if a Descriptor can be read
     *
     * @param descriptor a descriptor to check
     * @return Returns <b>true</b> if descriptor is readable
     */
    public static boolean isDescriptorReadable(BluetoothGattDescriptor descriptor) {
        return (descriptor.getPermissions() & BluetoothGattCharacteristic.PERMISSION_WRITE) != 0;
    }


    /**
     * Check if a Descriptor can be written
     *
     * @param descriptor a descriptor to check
     * @return Returns <b>true</b> if descriptor is writeable
     */
    public static boolean isDescriptorWriteable(BluetoothGattDescriptor descriptor) {
        return (descriptor.getPermissions() & BluetoothGattCharacteristic.PERMISSION_WRITE) != 0;
    }

    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.v(TAG, "Connected");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    mConnectedCentral = device;
                    mBlePeripheralCallback.onCentralConnected(device);
                    stopAdvertising();


                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mConnectedCentral = null;
                    mBlePeripheralCallback.onCentralDisconnected(device);
                    try {
                        startAdvertising();
                    } catch (Exception e) {
                        Log.e(TAG, "error starting advertising");
                    }
                }
            }

        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.d(TAG, "Device tried to read characteristic: " + characteristic.getUuid());
            Log.d(TAG, "Value: " + Arrays.toString(characteristic.getValue()));
            if (offset != 0) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, characteristic.getValue());
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.getValue());
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.v(TAG, "Notification sent. Status: " + status);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            Log.v(TAG, "Characteristic Write request: " + Arrays.toString(value));

            mBlePeripheralCallback.onCharacteristicWritten(device, characteristic, value);

            if (isCharacteristicWritableWithResponse(characteristic)) {
                characteristic.setValue(value);
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }


            if (isCharacteristicNotifiable(characteristic)) {
                boolean isNotifiedOfSend = false;
                mGattServer.notifyCharacteristicChanged(device, characteristic, isNotifiedOfSend);
            }
        }

        // https://stackoverflow.com/questions/24865120/any-way-to-implement-ble-notifications-in-android-l-preview/25508053#25508053
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
            Log.v(TAG, "Descriptor Write Request " + descriptor.getUuid() + " " + Arrays.toString(value));
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded,
                    offset, value);

            // determine which Characteristic is being requested
            BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();


            // is the descriptor writeable?
            if (isDescriptorWriteable(descriptor)) {
                descriptor.setValue(value);

                // was this a subscription or an unsubscription?
                if (descriptor.getUuid().equals(NOTIFY_DESCRIPTOR_UUID)) {
                    if (value == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) {
                        mBlePeripheralCallback.onCharacteristicSubscribedTo(characteristic);
                    } else if (value == BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) {
                        mBlePeripheralCallback.onCharacteristicUnsubscribedFrom(characteristic);
                    }
                    // send a confirmation if necessary
                    if (isDescriptorReadable(descriptor)) {
                        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                }
            } else {
                // notify failure if necessary
                if (isDescriptorReadable(descriptor)) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, value);
                }
            }
        }
    };

    public AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);

            mBlePeripheralCallback.onAdvertisingStarted();
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            mBlePeripheralCallback.onAdvertisingFailed(errorCode);
        }
    };
}


