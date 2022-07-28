package com.cxj.bluetoothlib.listener;

/**
 * @author chenxiaojin
 * @date 2020/7/2
 * @description 蓝牙设备状态变更回调
 */
public interface BluetoothDeviceStateListener {
    /**
     * 状态变更回调
     * BluetoothProfile.STATE_DISCONNECTED; // 0
     * BluetoothProfile.STATE_CONNECTING; // 1
     * BluetoothProfile.STATE_CONNECTED; // 2
     * BluetoothProfile.STATE_DISCONNECTING; // 3
     *
     * @param oldState 旧状态
     * @param newState 新状态
     */
    void onStateChange(String deviceMac, int oldState, int newState);


    /**
     * BluetoothGattCallback.onServicesDiscovered返回成功后的回调
     * 数据需要发现服务后才能成功发送
     * 如果设备连接成功就要数据, 需要在此方法发送且延迟100ms左右,否则发送失败
     *
     * @param deviceMac
     */
    void onReady(String deviceMac);


    /**
     * 发现服务失败
     *
     * @param deviceMac
     */
    void onDiscoverServicesError(String deviceMac, String errorMessage);

    /**
     * 设备连接超时
     *
     * @param deviceMac
     */
    void onConnectTimeout(String deviceMac);

}
