package tonyg.example.com.examplebleperipheral.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

import tonyg.example.com.examplebleperipheral.ble.callbacks.BlePeripheralCallback;
import tonyg.example.com.examplebleperipheral.ble.callbacks.BleRemoteLedCallback;


/**
 * This class creates a local Bluetooth Peripheral
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2016-03-06
 */
public class BleRemoteLed {

    /** Constants **/
    private static final String TAG = BleRemoteLed.class.getSimpleName();

    public static final String CHARSET = "ASCII";

    private static final String MODEL_NUMBER = "1AB2";
    private static final String SERIAL_NUMBER = "1234";

    /** Peripheral and GATT Profile **/
    public static final String ADVERTISING_NAME =  "LedRemote";

    public static final UUID AUTOMATION_IO_SERVICE_UUID = UUID.fromString("00001815-0000-1000-8000-00805f9b34fb"); // Automation IO Service
    public static final UUID COMMAND_CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
    public static final UUID RESPONSE_CHARACTERISTIC_UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb");


    private static final int COMMAND_CHARACTERISTIC_LENGTH = 20;
    private static final int RESPONSE_CHARACTERISTIC_LENGTH = 20;


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

    public static final byte RESPONSE_LED_STATE_ERROR = 1;
    public static final byte LED_STATE_ON = 1;
    public static final byte LED_STATE_OFF = 2;


    /** Callback Handlers **/
    public BleRemoteLedCallback mBleRemoteLedCallback;

    /** Bluetooth Stuff **/
    private BlePeripheral mBlePeripheral;

    private BluetoothGattService mAutomationIoService;
    private BluetoothGattCharacteristic mCommandCharacteristic, mResponseCharacteristic;




    /**
     * Construct a new Peripheral
     *
     * @param context The Application Context
     * @param bleRemoteLedCallback The callback handler that interfaces with this Peripheral
     * @throws Exception Exception thrown if Bluetooth is not supported
     */
    public BleRemoteLed(final Context context, BleRemoteLedCallback bleRemoteLedCallback) throws Exception {
        mBleRemoteLedCallback = bleRemoteLedCallback;

        mBlePeripheral = new BlePeripheral(context, mBlePeripheralCallback);

        setupDevice();
    }


    /**
     * Set up the Advertising name and GATT profile
     */
    private void setupDevice() throws Exception {
        mBlePeripheral.setModelNumber(MODEL_NUMBER);
        mBlePeripheral.setSerialNumber(SERIAL_NUMBER);

        mBlePeripheral.setupDevice();

        mAutomationIoService = new BluetoothGattService(AUTOMATION_IO_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        mCommandCharacteristic = new BluetoothGattCharacteristic(
                COMMAND_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        mResponseCharacteristic = new BluetoothGattCharacteristic(
                RESPONSE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // add Notification support to Characteristic
        BluetoothGattDescriptor notifyDescriptor = new BluetoothGattDescriptor(BlePeripheral.NOTIFY_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        mResponseCharacteristic.addDescriptor(notifyDescriptor);

        mAutomationIoService.addCharacteristic(mCommandCharacteristic);
        mAutomationIoService.addCharacteristic(mResponseCharacteristic);

        mBlePeripheral.addService(mAutomationIoService);
    }

    /**
     * Start Advertising
     *
     * @throws Exception Exception thrown if Bluetooth Peripheral mode is not supported
     */
    public void startAdvertising() throws Exception {
        // set the device name
        mBlePeripheral.setPeripheralAdvertisingName(ADVERTISING_NAME);

        mBlePeripheral.startAdvertising();
    }

    /**
     * Stop advertising
     */
    public void stopAdvertising() {
        mBlePeripheral.stopAdvertising();
    }

    /**
     * Get the BlePeripheral
     */
    public BlePeripheral getBlePeripheral() {
        return mBlePeripheral;
    }

    /**
     * Make sense of the incoming byte array as a command
     *
     * @param bleCommandValue the incoming Bluetooth value
     */
    private void processCommand(final BluetoothDevice connectedDevice, final byte[] bleCommandValue) {
        if (bleCommandValue[COMMAND_FOOTER_POSITION] == COMMAND_FOOTER) {
            Log.v(TAG, "Command found");
            switch (bleCommandValue[COMMAND_DATA_POSITION]) {
                case COMMAND_LED_ON:
                    Log.v(TAG, "Command to turn LED on");
                    sendBleResponse(connectedDevice, LED_STATE_ON);
                    mBleRemoteLedCallback.onLedTurnedOn();
                    break;

                case COMMAND_LED_OFF:
                    Log.v(TAG, "Command to turn LED off");
                    sendBleResponse(connectedDevice, LED_STATE_OFF);
                    mBleRemoteLedCallback.onLedTurnedOff();
                    break;

                default:
                    Log.d(TAG, "Unknown incoming command");
            }
        }
    }

    /**
     * Send a formatted response out via a Bluetooth Characteristic
     *
     * @param ledState the new LED state
     */
    private void sendBleResponse(final BluetoothDevice connectedDevice, byte ledState) {
        byte[] responseValue = new byte[TRANSMISSION_LENGTH];
        responseValue[RESPONSE_FOOTER_POSITION] = RESPONSE_TYPE_CONFIRMATION;
        responseValue[RESPONSE_DATA_POSITION] = ledState;

        Log.v(TAG, "sending response: " + Arrays.toString(mResponseCharacteristic.getValue()) + " to characteristic: "+mResponseCharacteristic);
        mResponseCharacteristic.setValue(responseValue);
        mBlePeripheral.getGattServer().notifyCharacteristicChanged(connectedDevice, mResponseCharacteristic, true);
    }

    private BlePeripheralCallback mBlePeripheralCallback = new BlePeripheralCallback() {
        @Override
        public void onAdvertisingStarted() {

        }

        @Override
        public void onAdvertisingFailed(int errorCode) {

        }

        @Override
        public void onAdvertisingStopped() {

        }

        @Override
        public void onCentralConnected(BluetoothDevice bluetoothDevice) {
            mBleRemoteLedCallback.onCentralConnected(bluetoothDevice);
        }

        @Override
        public void onCentralDisconnected(BluetoothDevice bluetoothDevice) {
            mBleRemoteLedCallback.onCentralDisconnected(bluetoothDevice);
        }

        @Override
        public void onCharacteristicWritten(BluetoothDevice connectedDevice, BluetoothGattCharacteristic characteristic, byte[] value) {
            // copy value to the read Characteristic
            processCommand(connectedDevice, value);
        }

        @Override
        public void onCharacteristicSubscribedTo(BluetoothGattCharacteristic characteristic) {

        }

        @Override
        public void onCharacteristicUnsubscribedFrom(BluetoothGattCharacteristic characteristic) {

        }
    };
}