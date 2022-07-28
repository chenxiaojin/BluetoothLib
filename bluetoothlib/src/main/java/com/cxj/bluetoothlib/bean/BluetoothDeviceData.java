package com.cxj.bluetoothlib.bean;

import java.util.UUID;

/**
 * @author chenxiaojin
 * @date 2020/6/19
 * @descriptionn 蓝牙设备数据
 */
public class BluetoothDeviceData {
    private String deviceMac;
    private UUID characteristicsUUID;
    private byte[] data;

    public BluetoothDeviceData(String deviceMac, UUID characteristicsUUID, byte[] data) {
        this.deviceMac = deviceMac;
        this.characteristicsUUID = characteristicsUUID;
        this.data = data;
    }

    public String getDeviceMac() {
        return deviceMac;
    }

    public void setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
    }

    public UUID getCharacteristicsUUID() {
        return characteristicsUUID;
    }

    public void setCharacteristicsUUID(UUID characteristicsUUID) {
        this.characteristicsUUID = characteristicsUUID;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
