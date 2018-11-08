package com.duvitech.testusb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_USB_READY = "com.six15.connectivityservices.USB_READY";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_NOT_SUPPORTED = "com.six15.usbservice.USB_NOT_SUPPORTED";
    public static final String ACTION_NO_USB = "com.six15.usbservice.NO_USB";
    public static final String ACTION_USB_PERMISSION_GRANTED = "com.six15.usbservice.USB_PERMISSION_GRANTED";
    public static final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.six15.usbservice.USB_PERMISSION_NOT_GRANTED";
    public static final String ACTION_USB_DISCONNECTED = "com.six15.usbservice.USB_DISCONNECTED";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final String TAG = "TestUSB";

    public static boolean SERVICE_CONNECTED = false;

    private static boolean displayPortConnected = false;

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface mInterface;
    private UsbEndpoint outEndpoint;
    private int MAX_PACKET_SIZE = 128;


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (arg1.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = arg1.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) // User accepted our USB connection. Try to open the device as a serial port
                {
                    Intent intent = new Intent(ACTION_USB_PERMISSION_GRANTED);
                    arg0.sendBroadcast(intent);
                    connection = usbManager.openDevice(device);
                    int vid = device.getVendorId();
                    int pid = device.getProductId();
                    int iface = 3;
                    Log.i(TAG, "Found " + device.getInterfaceCount() + " interfaces");
                    mInterface = device.getInterface(iface);
                    if(mInterface != null){
                        openDisplayPort();
                    }

                } else // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    Intent intent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
                    arg0.sendBroadcast(intent);
                }
            } else if (arg1.getAction().equals(ACTION_USB_ATTACHED)) {
                if (!displayPortConnected)
                    findDisplayPortDevice(); // A USB device has been attached. Try to open it as a Display port
            } else if (arg1.getAction().equals(ACTION_USB_DETACHED)) {
                // Usb device was disconnected. send an intent to the Main Activity
                Intent intent = new Intent(ACTION_USB_DISCONNECTED);
                arg0.sendBroadcast(intent);
                if (displayPortConnected) {
                    // serialPort.syncClose();
                    connection.releaseInterface(mInterface);
                    connection.close();
                }
                displayPortConnected = false;
            }
        }
    };

    private void requestUserPermission() {
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPendingIntent);
    }

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }

    private boolean openDisplayPort() {
        if(connection.claimInterface(mInterface, true))
        {
            Log.i(TAG, "Interface succesfully claimed");
        }else
        {
            Log.i(TAG, "Interface could not be claimed");
            return false;
        }

        // Assign endpoints
        int numberEndpoints = mInterface.getEndpointCount();
        for(int i=0;i<=numberEndpoints-1;i++)
        {
            UsbEndpoint endpoint = mInterface.getEndpoint(i);

            Log.i(TAG, "Type: " + endpoint.getType() );
            Log.i(TAG, "Direction: " + endpoint.getDirection() );
            if(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_OUT)
            {
                Log.i(TAG, "OutEndpoint Set");
                outEndpoint = endpoint;
                MAX_PACKET_SIZE = outEndpoint.getMaxPacketSize();
            }
        }

        if(outEndpoint == null)
        {
            Log.i(TAG, "Interface does not have an OUT interface");
            return false;
        }


        return true;
    }

    private void findDisplayPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                if (deviceVID == 0x2DC4 && devicePID == 0x0210) {
                    // There is a device connected to our Android device. Try to open it as a Serial Port.
                    requestUserPermission();
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
            if (!keep) {
                // There is no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                Intent intent = new Intent(ACTION_NO_USB);
                sendBroadcast(intent);
            }
        } else {
            // There is no USB devices connected. Send an intent to MainActivity
            Intent intent = new Intent(ACTION_NO_USB);
            sendBroadcast(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        Button btn = findViewById(R.id.btnStart);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int sent = 0;
                Log.d(TAG,"Start Button Click");
                byte[] arr = new byte[640*480*2];
                Arrays.fill(arr,(byte)0xFF);
                // start timer
                sent = connection.bulkTransfer(outEndpoint, arr, arr.length, 100);

                // end timer

                if(sent<0){
                    Log.e(TAG,"error");
                }else{
                    Log.d(TAG, String.format("Sent %d bytes ", sent));
                }
            }
        });

//        tv.setText(stringFromJNI());

        SERVICE_CONNECTED = true;
        displayPortConnected = false;
        setFilter();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        findDisplayPortDevice();

    }

    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        SERVICE_CONNECTED = false;
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
