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
 * Created by adonis on 1/17/17.
 */

public class BleRemoteLed_old {

        /** Constants **/
        private static final String TAG = tonyg.example.com.examplebleperipheral.ble.BleRemoteLed.class.getSimpleName();

        /** Peripheral and GATT Profile **/
        public static final String ADVERTISING_NAME =  "LedRemote";

        public static final UUID SERVICE_UUID = UUID.fromString("00001815-0000-1000-8000-00805f9b34fb"); // Automation IO Service
        public static final UUID COMMAND_CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
        public static final UUID RESPONSE_CHARACTERISTIC_UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb");


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
        private BluetoothDevice mConnectedCentral;

        private BluetoothGattService mIOService;
        private BluetoothGattCharacteristic mCommandCharacteristic, mResponseCharacteristic;




        /**
         * Construct a new Peripheral
         *
         * @param context The Application Context
         * @param blePeripheralCallback The callback handler that interfaces with this Peripheral
         * @throws Exception Exception thrown if Bluetooth is not supported
         */
        public BleRemoteLed_old(final Context context, BleRemoteLedCallback blePeripheralCallback) throws Exception {
            mBleRemoteLedCallback = blePeripheralCallback;

            mBlePeripheral = new BlePeripheral(context, mBlePeripheralCallback);

            setupDevice();
        }


        /**
         * Set up the Advertising name and GATT profile
         */
        private void setupDevice() throws Exception {
            Log.v(TAG, "setting up IO service");
            mIOService = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

            mCommandCharacteristic = new BluetoothGattCharacteristic(
                    COMMAND_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);

            mResponseCharacteristic = new BluetoothGattCharacteristic(
                    RESPONSE_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

            // add Notification support to Characteristic
            BluetoothGattDescriptor notifyDescriptor = new BluetoothGattDescriptor(BlePeripheral.NOTIFY_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
            mResponseCharacteristic.addDescriptor(notifyDescriptor);

            mIOService.addCharacteristic(mCommandCharacteristic);
            mIOService.addCharacteristic(mResponseCharacteristic);

            // tell the BlePeripheral what the Gatt Profile is
            mBlePeripheral.addService(mIOService);

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
        private void processCommand(final byte[] bleCommandValue) {
            if (bleCommandValue[COMMAND_FOOTER_POSITION] == COMMAND_FOOTER) {
                Log.v(TAG, "Command found");
                switch (bleCommandValue[COMMAND_DATA_POSITION]) {
                    case COMMAND_LED_ON:
                        Log.v(TAG, "Command to turn LED on");
                        turnLedOn();
                        break;

                    case COMMAND_LED_OFF:
                        Log.v(TAG, "Command to turn LED off");
                        turnLedOff();
                        break;

                    default:
                        Log.d(TAG, "Unknown incoming command");
                }
            }
        }

        /**
         * Turn LED on and notify callback
         */
        public void turnLedOn() {
            sendBleResponse(LED_STATE_ON);
            mBleRemoteLedCallback.onLedTurnedOn();
        }

        /**
         * Turn LED off and notify callback
         */
        public void turnLedOff() {
            sendBleResponse(LED_STATE_OFF);
            mBleRemoteLedCallback.onLedTurnedOff();
        }

        /**
         * Send a formatted response out via a Bluetooth Characteristic
         *
         * @param ledState the new LED state
         */
        private void sendBleResponse(byte ledState) {
            byte[] responseValue = new byte[TRANSMISSION_LENGTH];
            responseValue[RESPONSE_FOOTER_POSITION] = RESPONSE_TYPE_CONFIRMATION;
            responseValue[RESPONSE_DATA_POSITION] = ledState;

            Log.v(TAG, "sending response: " + Arrays.toString(mResponseCharacteristic.getValue()) + " to characteristic: "+mResponseCharacteristic);
            mResponseCharacteristic.setValue(responseValue);
            if (mConnectedCentral != null) {
                mBlePeripheral.getGattServer().notifyCharacteristicChanged(mConnectedCentral, mResponseCharacteristic, true);
            }
        }



        private BlePeripheralCallback mBlePeripheralCallback = new BlePeripheralCallback() {
            @Override
            public void onAdvertisingStarted() {
                // do nothing
            }

            @Override
            public void onAdvertisingFailed(int errorCode) {
                // do nothing
            }

            @Override
            public void onAdvertisingStopped() {
                // do nothing
            }

            @Override
            public void onCentralConnected(final BluetoothDevice bluetoothDevice) {
                mConnectedCentral = bluetoothDevice;
                mBleRemoteLedCallback.onCentralConnected(bluetoothDevice);
                turnLedOff();
            }

            @Override
            public void onCentralDisconnected(final BluetoothDevice bluetoothDevice) {
                mConnectedCentral = null;
                mBleRemoteLedCallback.onCentralDisconnected(bluetoothDevice);

            }

            @Override
            public void onCharacteristicWritten(final BluetoothDevice connectedDevice, final BluetoothGattCharacteristic characteristic, final byte[] value) {
                processCommand(value);
            }

            @Override
            public void onCharacteristicSubscribedTo(final BluetoothGattCharacteristic characteristic) {
                // do nothing
            }

            @Override
            public void onCharacteristicUnsubscribedFrom(final BluetoothGattCharacteristic characteristic) {
                // do nothing
            }
        };


    }

