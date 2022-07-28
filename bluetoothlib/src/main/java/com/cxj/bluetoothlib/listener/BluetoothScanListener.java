package com.cxj.bluetoothlib.listener;

import android.bluetooth.BluetoothDevice;

/**
 * @author chenxiaojin
 * @date 2020/7/22
 * @description
 */
public interface BluetoothScanListener {

    void onStartScan();

    void onScanFailed(String errorMessage);

    void onDeviceFounded(BluetoothDevice device, int rssi, byte[] scanRecord);

    void onStopScan();
}
