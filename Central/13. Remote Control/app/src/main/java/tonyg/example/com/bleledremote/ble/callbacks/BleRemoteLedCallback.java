package tonyg.example.com.bleledremote.ble.callbacks;


/**
 * Relay state changes from Led Remote
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2015-12-21
 */
public abstract class BleRemoteLedCallback {

    /**
     * Led Remote connected
     */
    public abstract void connected();

    /**
     * Led Remote disconnected
     */
    public abstract void disconnected();

    /**
     * Led Remote received command
     */
    public abstract void commandWritten();

    /**
     * Led Remote successfully changed led state
     *
     * @param ledState the LED state
     */
    public abstract void ledStateChanged(final int ledState);

    /**
     * Led Remote experienced an error
     */
    public abstract void ledError();
}
