package com.cxj.bluetoothlib.listener;


import com.cxj.bluetoothlib.bean.BluetoothDeviceData;

/**
 * @author chenxiaojin
 * @date 2020/7/21
 * @description 蓝牙数据回调
 */
public interface BluetoothDeviceDataCallback {
    /**
     * 读特性数据回调(BluetoothGattCallback.onCharacteristicRead)
     *
     * @param deviceData
     * @param status
     */
    void onDataRead(BluetoothDeviceData deviceData, int status);

    /**
     * 写入特性数据回调(BluetoothGattCallback.onCharacteristicWrite)
     *
     * @param deviceData
     * @param status
     */
    void onDataWrite(BluetoothDeviceData deviceData, int status);

    /**
     * 特性数据回调(BluetoothGattCallback.onCharacteristicChanged)
     *
     * @param deviceData
     */
    void onDataChanged(BluetoothDeviceData deviceData);


    void onWriteError(String deviceMac, String serviceUUID,
                      String characteristicUUID, String errorMessage);

    void onReadError(String deviceMac, String serviceUUID,
                     String characteristicUUID, String errorMessage);
}
