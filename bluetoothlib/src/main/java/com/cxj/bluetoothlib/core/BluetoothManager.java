package com.cxj.bluetoothlib.core;

import android.util.Log;

import com.cxj.bluetoothlib.bean.BluetoothDeviceData;
import com.cxj.bluetoothlib.listener.BluetoothDeviceDataCallback;
import com.cxj.bluetoothlib.listener.BluetoothDeviceStateListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author chenxiaojin
 * @date 2020/7/22
 * @description 蓝牙多设备管理
 */
public class BluetoothManager implements BluetoothDeviceStateListener, BluetoothDeviceDataCallback {

    private static final String TAG = "BluetoothManager";
    private static BluetoothManager bluetoothManager;

    private Map<String, BluetoothLEDevice> devicesMap = new HashMap<>();
    private List<BluetoothDeviceStateListener> deviceStateListeners = new ArrayList<>();
    private List<BluetoothDeviceDataCallback> deviceDataCallbacks = new ArrayList<>();

    public static BluetoothManager getInstance() {
        if (null == bluetoothManager) {
            bluetoothManager = new BluetoothManager();
        }
        return bluetoothManager;
    }


    /**
     * 连接设备, 通过此方法连接的设备才能被管理起来
     *
     * @param device
     * @return
     */
    public boolean connect(BluetoothLEDevice device) {
        return connect(device, false);
    }

    /**
     * 连接设备, 通过此方法连接的设备才能被管理起来
     *
     * @param device
     * @param isReset 是否重新设置，true：当已存在设备时，先断开设备、删除设备, 再重连设备
     * @return
     */
    public boolean connect(BluetoothLEDevice device, boolean isReset) {
        Log.e(TAG, "Connect device :" + device.getDeviceMac());
        BluetoothLEDevice oldDevice = devicesMap.get(device.getDeviceMac());
        if (null == oldDevice) {
            return connectDevice(device);
        }

        // 如果需要重置, 先断开并删除旧设备, 再进行连接
        if (isReset) {
            oldDevice.close();
            devicesMap.remove(device.getDeviceMac());
            return connectDevice(device);
        }
        // 设备已经连接时, 不再继续重连
        if (oldDevice.isConnected()) {
            Log.e(TAG, "Device is connected , no need to connect again.");
            return false;
        }
        Log.e(TAG, "Prepare to connect device:" + device.getDeviceMac());
        return oldDevice.connect();
    }

    private boolean connectDevice(BluetoothLEDevice device) {
        devicesMap.put(device.getDeviceMac(), device);
        device.addDeviceStateListener(this);
        device.addDeviceDataCallback(this);
        return device.connect();
    }

    /**
     * 读特征数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param data
     */
    public void readData(String deviceMac, byte[] data) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            notifyDataReadError(deviceMac, null, null,
                    "Can not find device:" + deviceMac);
            return;
        }
        device.readData(data);
    }

    /**
     * 通过指定服务、特性写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param deviceMac
     * @param serviceUUID
     * @param characteristicUUID
     * @param data
     */
    public void readData(String deviceMac, String serviceUUID,
                         String characteristicUUID, byte[] data) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            notifyDataReadError(deviceMac, serviceUUID, characteristicUUID,
                    "Can not find device:" + deviceMac);
            return;
        }
        device.readData(serviceUUID, characteristicUUID, data);
    }


    /**
     * 写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param data
     */
    public void writeData(String deviceMac, byte[] data) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            notifyDataWriteError(deviceMac, null, null,
                    "Can not find device:" + deviceMac);
            return;
        }
        device.writeData(data);

    }

    /**
     * 通过指定服务、特性写数据
     * 注意:
     * 1、BLE特征一次写入的最大字节是20个. 超过20的会丢弃
     * 2、需要在UI线程中写数据，否则接受不到回调
     * 3、为了保证数据能正常发送，延迟了100ms去发送，如果有问题，需自行修改
     *
     * @param serviceUUID
     * @param characteristicUUID
     * @param data
     */
    public void writeData(String deviceMac, String serviceUUID,
                          String characteristicUUID, byte[] data) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            notifyDataWriteError(deviceMac, serviceUUID, characteristicUUID,
                    "Can not find device:" + deviceMac);
            return;
        }
        device.writeData(data);
    }

    public boolean isConnected(String deviceMac) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            Log.e(TAG, "Can not find device:" + deviceMac);
            return false;
        }
        return device.isConnected();
    }

    public List<BluetoothLEDevice> getAllDevices() {
        List<BluetoothLEDevice> allDevices = new ArrayList<>();
        for (BluetoothLEDevice device : devicesMap.values()) {
            allDevices.add(device);
        }
        return allDevices;
    }

    public List<BluetoothLEDevice> getConnectedDevices() {
        List<BluetoothLEDevice> allDevices = new ArrayList<>();
        for (BluetoothLEDevice device : devicesMap.values()) {
            if (device.isConnected()) {
                allDevices.add(device);
            }
        }
        return allDevices;
    }

    public BluetoothLEDevice getDevice(String deviceMac) {
        return devicesMap.get(deviceMac);
    }

    /**
     * 断连设备
     *
     * @param deviceMac
     */
    public void disconnect(String deviceMac) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            Log.e(TAG, "Can not find device, no need to disconnect device, mac:" + deviceMac);
            return;
        }
        device.disconnect();
    }

    /**
     * 断连所有管理的设备
     */
    public void disconnectAllDevices() {
        Log.e(TAG, "Disconnect all devices.");
        for (BluetoothLEDevice device : devicesMap.values()) {
            disconnect(device.getDeviceMac());
        }
    }


    /**
     * 关闭设备
     *
     * @param deviceMac
     */
    public void close(String deviceMac) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null == device) {
            Log.e(TAG, "Can not find device, no need to close device, mac:" + deviceMac);
            return;
        }
        device.close();
    }

    /**
     * 关闭当前管理的所有设备
     */
    public void closeAllDevices() {
        Log.e(TAG, "Close all devices.");
        for (BluetoothLEDevice device : devicesMap.values()) {
            close(device.getDeviceMac());
        }
    }

    /**
     * 清除所有管理设备(先断开所有设备, 再清除)
     */
    public void clearAllDevices() {
        closeAllDevices();
        devicesMap.clear();
    }

    /**
     * 移除设备, 移除后不再管理
     *
     * @param deviceMac
     */
    public void remove(String deviceMac) {
        BluetoothLEDevice device = devicesMap.get(deviceMac);
        if (null != device) {
            device.removeDeviceDataCallback(this);
            device.removeDeviceStateListener(this);
            close(deviceMac);
            Log.e(TAG, "Remove device :" + deviceMac);
            devicesMap.remove(deviceMac);
            device = null;
            return;
        }
        Log.e(TAG, "Can not find device, no need to remove. mac:" + deviceMac);
    }

    @Override
    public void onStateChange(String deviceMac, int oldState, int newState) {
        notifyConnectStateChange(deviceMac, oldState, newState);
    }

    @Override
    public void onReady(String deviceMac) {
        notifyDeviceReady(deviceMac);
    }

    @Override
    public void onDiscoverServicesError(String deviceMac, String errorMessage) {
        notifyDiscoverServicesError(deviceMac, errorMessage);
    }

    @Override
    public void onConnectTimeout(String deviceMac) {
        notifyDeviceConnectTimeout(deviceMac);
    }

    @Override
    public void onDataRead(BluetoothDeviceData deviceData, int status) {
        notifyDeviceDataRead(deviceData, status);
    }

    @Override
    public void onDataWrite(BluetoothDeviceData deviceData, int status) {
        notifyDeviceDataWrite(deviceData, status);
    }

    @Override
    public void onDataChanged(BluetoothDeviceData deviceData) {
        notifyDeviceDataChanged(deviceData);
    }

    @Override
    public void onWriteError(String deviceMac, String serviceUUID, String characteristicUUID,
                             String errorMessage) {
        notifyDataWriteError(deviceMac, serviceUUID, characteristicUUID, errorMessage);
    }

    @Override
    public void onReadError(String deviceMac, String serviceUUID, String characteristicUUID,
                            String errorMessage) {
        notifyDataReadError(deviceMac, serviceUUID, characteristicUUID, errorMessage);
    }

    private void notifyDataReadError(String deviceMac, String serviceUUID,
                                     String characteristicUUID, String errorMessage) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onReadError(deviceMac, serviceUUID, characteristicUUID, errorMessage);
        }
    }

    private void notifyConnectStateChange(String deviceMac, int oldState, int newState) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onStateChange(deviceMac, oldState, newState);
        }
    }

    private void notifyDeviceReady(String deviceMac) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onReady(deviceMac);
        }
    }

    private void notifyDiscoverServicesError(String deviceMac, String errorMessage) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onDiscoverServicesError(deviceMac, errorMessage);
        }
    }

    private void notifyDeviceConnectTimeout(String deviceMac) {
        for (BluetoothDeviceStateListener stateListener : deviceStateListeners) {
            stateListener.onConnectTimeout(deviceMac);
        }
    }

    private void notifyDeviceDataRead(BluetoothDeviceData deviceData, int state) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onDataRead(deviceData, state);
        }
    }

    private void notifyDeviceDataWrite(BluetoothDeviceData deviceData, int state) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onDataWrite(deviceData, state);
        }
    }

    private void notifyDeviceDataChanged(BluetoothDeviceData deviceData) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onDataChanged(deviceData);
        }
    }

    private void notifyDataWriteError(String deviceMac, String serviceUUID,
                                      String characteristicUUID, String errorMessage) {
        for (BluetoothDeviceDataCallback dataCallback : deviceDataCallbacks) {
            dataCallback.onWriteError(deviceMac, serviceUUID, characteristicUUID, errorMessage);
        }
    }


    public void addDeviceStateListener(BluetoothDeviceStateListener deviceStateListener) {
        deviceStateListeners.add(deviceStateListener);
    }

    public void removeDeviceStateListener(BluetoothDeviceStateListener deviceStateListener) {
        deviceStateListeners.remove(deviceStateListener);
    }

    public void addDeviceDataCallback(BluetoothDeviceDataCallback dataCallback) {
        deviceDataCallbacks.add(dataCallback);
    }

    public void removeDeviceDataCallback(BluetoothDeviceDataCallback dataCallback) {
        deviceDataCallbacks.remove(dataCallback);
    }

    public void destroy() {
        for (BluetoothLEDevice device : devicesMap.values()) {
            device.removeDeviceDataCallback(this);
            device.removeDeviceStateListener(this);
        }
        devicesMap.clear();
        deviceDataCallbacks.clear();
        deviceStateListeners.clear();
    }
}
