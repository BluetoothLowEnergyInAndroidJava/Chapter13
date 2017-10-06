package tonyg.example.com.examplebleperipheral.ble.callbacks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Relay state changes from Echo Server
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2015-12-21
 */
public abstract class BleRemoteLedCallback {
    /**
     * Central Connected
     *
     * @param bluetoothDevice the BluetoothDevice representing the connected Central
     */
    public abstract void onCentralConnected(final BluetoothDevice bluetoothDevice);

    /**
     * Central Disconnected
     *
     * @param bluetoothDevice the BluetoothDevice representing the disconnected Central
     */
    public abstract void onCentralDisconnected(final BluetoothDevice bluetoothDevice);

    /**
     * Led Turned On
     */
    public abstract void onLedTurnedOn();

    /**
     * Led Turned Off
     */
    public abstract void onLedTurnedOff();
}
