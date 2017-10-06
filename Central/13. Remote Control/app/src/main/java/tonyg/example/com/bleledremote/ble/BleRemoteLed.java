package tonyg.example.com.bleledremote.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import tonyg.example.com.bleledremote.ble.callbacks.BleRemoteLedCallback;

/**
 * This class allows us to share Bluetooth resources
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2016-03-06
 */
public class BleRemoteLed {
    private static final String TAG = BleRemoteLed.class.getSimpleName();

    public static final String CHARACTER_ENCODING = "ASCII";

    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private BleRemoteLedCallback mBleRemoteLedCallback;
    private BluetoothGattCharacteristic mCommandCharacteristic, mResponseCharacteristic;
    private Context mContext;

    /** Bluetooth Device stuff **/
    public static final String ADVERTISED_NAME = "LedRemote";
    public static final UUID SERVICE_UUID = UUID.fromString("00001815-0000-1000-8000-00805f9b34fb");
    public static final UUID COMMAND_CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
    public static final UUID RESPONSE_CHARACTERISTIC_UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb");

    public static final UUID NOTIFY_DISCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /** Data packet **/
    private static final int TRANSMISSION_LENGTH = 2;

    /** Sending commands **/
    public static final int COMMAND_FOOTER_POSITION = 1;
    public static final int COMMAND_DATA_POSITION = 0;

    public static final byte COMMAND_FOOTER = 1;

    public static final byte COMMAND_LED_OFF = 2;
    public static final byte COMMAND_LED_ON = 1;

    /** Receiving responses **/
    public static final int RESPONSE_FOOTER_POSITION = 1;
    public static final int RESPONSE_DATA_POSITION = 0;

    public static final byte RESPONSE_TYPE_ERROR = 0;
    public static final byte RESPONSE_TYPE_CONFIRMATION = 1;

    public static final int RESPONSE_LED_STATE_ERROR = 1;
    public static final int LED_STATE_ON = 1;
    public static final int LED_STATE_OFF = 2;

    public BleRemoteLed(Context context, BleRemoteLedCallback peripheralCallback) {
        mContext = context;
        mBleRemoteLedCallback = peripheralCallback;
    }

    /**
     * Determine if the incoming value is a confirmation, or error
     *
     * @param value the incoming data value
     * @return integer message type.  See @MESSAGE_TYPE_ERROR, @MESSAGE_TYPE_CONFIRMATION, and @MESSAGE_TYPE_COMMAND
     * @throws Exception
     */
    public int getResponseType(byte[] value)  throws Exception {
        byte dataFooter = value[RESPONSE_FOOTER_POSITION];
        int returnValue = RESPONSE_LED_STATE_ERROR;
        if (dataFooter == RESPONSE_TYPE_CONFIRMATION) {
            returnValue = value[RESPONSE_DATA_POSITION];
        }
        return returnValue;
    }

    /**
     * Connect to a Peripheral
     *
     * @param bluetoothDevice the Bluetooth Device
     * @return a connection to the BluetoothGatt
     * @throws Exception if no device is given
     */
    public BluetoothGatt connect(BluetoothDevice bluetoothDevice) throws Exception {
        if (bluetoothDevice == null) {
            throw new Exception("No bluetooth device provided");
        }
        mBluetoothDevice = bluetoothDevice;
        mBluetoothGatt = bluetoothDevice.connectGatt(mContext, false, mGattCallback);
        refreshDeviceCache();
        return mBluetoothGatt;
    }

    /**
     * Disconnect from a Peripheral
     */
    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    /**
     * A connection can only close after a successful disconnect.
     * Be sure to use the BluetoothGattCallback.onConnectionStateChanged event
     * to notify of a successful disconnect
     */
    public void close() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close(); // close connection to Peripheral
            mBluetoothGatt = null; // release from memory
        }
    }
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }


    /**
     * Clear the GATT Service cache.
     *
     * New in this chapter
     *
     * @return <b>true</b> if the device cache clears successfully
     * @throws Exception
     */
    public boolean refreshDeviceCache() throws Exception {
        Method localMethod = mBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
        if (localMethod != null) {
            boolean bool = ((Boolean) localMethod.invoke(mBluetoothGatt, new Object[0])).booleanValue();
            return bool;
        }

        return false;
    }

    /**
     * Request a data/value read from a Ble Characteristic
     *
     * @param characteristic
     */
    public void readValueFromCharacteristic(final BluetoothGattCharacteristic characteristic) {
        // Reading a characteristic requires both requesting the read and handling the callback that is
        // sent when the read is successful
        // http://stackoverflow.com/a/20020279
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Turn the remote LED on;
     */
    public void turnLedOn() {
        writeCommand(COMMAND_LED_ON);
    }

    /**
     * Turn the remote LED off.
     */
    public void turnLedOff() {
        writeCommand(COMMAND_LED_OFF);
    }

    /**
     * Convert bytes to a hexadecimal String
     *
     * @param bytes a byte array
     * @return hexadecimal string
     */
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Write a value to a Characteristic
     *
     * @param command The command being written
     * @throws Exception
     */
    public void writeCommand(byte command) {
        // build data packet
        byte[] data = new byte[TRANSMISSION_LENGTH];
        data[COMMAND_DATA_POSITION] = command;
        data[COMMAND_FOOTER_POSITION] = COMMAND_FOOTER;

        Log.d(TAG, "Writing Message: "+bytesToHex(data));

        mCommandCharacteristic.setValue(data);
        mBluetoothGatt.writeCharacteristic(mCommandCharacteristic);
    }



    /**
     * Subscribe or unsubscribe from Characteristic Notifications
     *
     * New in this chapter
     *
     * @param characteristic
     * @param isEnabled <b>true</b> for "subscribe" <b>false</b> for "unsubscribe"
     */
    public void setCharacteristicNotification(final BluetoothGattCharacteristic characteristic, final boolean isEnabled) {
        // modified from http://stackoverflow.com/a/18011901/5671180
        // This is a 2-step process
        // Step 1: set the Characteristic Notification parameter locally
        mBluetoothGatt.setCharacteristicNotification(characteristic, isEnabled);
        // Step 2: Write a descriptor to the Bluetooth GATT enabling the subscription on the Perpiheral
        // turns out you need to implement a delay between setCharacteristicNotification and setvalue.
        // maybe it can be handled with a callback, but this is an easy way to implement
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(NOTIFY_DISCRIPTOR_UUID);
                Log.v(TAG, "descriptor: "+descriptor);
                if (isEnabled) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                }
                mBluetoothGatt.writeDescriptor(descriptor);
            }
        }, 10);
    }


    /**
     * Check if a Characetristic supports write permissions
     * @return Returns <b>true</b> if property is writable
     */
    public static boolean isCharacteristicWritable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    /**
     * Check if a Characetristic has read permissions
     *
     * @return Returns <b>true</b> if property is Readable
     */
    public static boolean isCharacteristicReadable(BluetoothGattCharacteristic characteristic) {
        return ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
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
     * BluetoothGattCallback handles connections, state changes, reads, writes, and GATT profile listings to a Peripheral
     *
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        /**
         * Charactersitic successfuly read
         *
         * @param gatt connection to GATT
         * @param characteristic The charactersitic that was read
         * @param status the status of the operation
         */
        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // read more at http://developer.android.com/guide/topics/connectivity/bluetooth-le.html#notification
                final byte[] message = characteristic.getValue();

                Log.v(TAG, "Message received: "+ BleRemoteLed.bytesToHex(message));

                int ledState = RESPONSE_TYPE_ERROR;
                // we are looking to see if the remote command worked
                try {
                    ledState = getResponseType(message);
                } catch (Exception e) {
                    Log.e(TAG, "Could not discern message type from incoming message");
                }

                switch (ledState) {
                    case BleRemoteLed.LED_STATE_ON:
                    case BleRemoteLed.LED_STATE_OFF:
                    {
                        mBleRemoteLedCallback.ledStateChanged(ledState);
                    }
                    break;
                    default:

                        mBleRemoteLedCallback.ledError();

                }
            }
        }

        /**
         * Characteristic was written successfully.  update the UI
         *
         * @param gatt Connection to the GATT
         * @param characteristic The Characteristic that was written
         * @param status write status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "command written");
                mBleRemoteLedCallback.commandWritten();
            } else {
                Log.e(TAG, "problem writing characteristic");

            }
        }

        /**
         * Charactersitic value changed.  Read new value.
         * @param gatt Connection to the GATT
         * @param characteristic The Characterstic
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            Log.v(TAG, "characteristic state changed");
            readValueFromCharacteristic(characteristic);

        }

        /**
         * Peripheral connected or disconnected.  Update UI
         * @param bluetoothGatt Connection to GATT
         * @param status status of the operation
         * @param newState new connection state
         */
        @Override
        public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.v(TAG, "connected");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "Disconnected from device");
                mBleRemoteLedCallback.disconnected();

                disconnect();
            }
        }

        /**
         * GATT Profile discovered.  Update UI
         * @param bluetoothGatt connection to GATT
         * @param status status of operation
         */
        @Override
        public void onServicesDiscovered(final BluetoothGatt bluetoothGatt, int status) {
            Log.v(TAG, "SERVICE DISCOVERED!: ");

            // if services were discovered, then let's iterate through them and display them on screen
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // check if there are matching services and characteristics

                List<BluetoothGattService> gattServices = bluetoothGatt.getServices();
                for (BluetoothGattService gattService : gattServices) {
                    Log.v(TAG, "service: "+gattService.getUuid().toString());
                    // while we are here, let's ask for this service's characteristics:
                    List<BluetoothGattCharacteristic> characteristics = gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic characteristic : characteristics) {
                        if (characteristic != null) {
                            Log.v(TAG, characteristic.getUuid().toString());
                        }
                    }
                }

                BluetoothGattService service = bluetoothGatt.getService(BleRemoteLed.SERVICE_UUID);
                if (service != null) {
                    Log.v(TAG, "service found");
                    mCommandCharacteristic = service.getCharacteristic(BleRemoteLed.COMMAND_CHARACTERISTIC_UUID);
                    mResponseCharacteristic = service.getCharacteristic(BleRemoteLed.RESPONSE_CHARACTERISTIC_UUID);

                    if (isCharacteristicNotifiable(mResponseCharacteristic)) {
                        setCharacteristicNotification(mResponseCharacteristic, true);
                    }
                }

            } else {
                Log.v(TAG, "Something went wrong while discovering GATT services from this device");
            }
            mBleRemoteLedCallback.connected();
        }
    };
}
