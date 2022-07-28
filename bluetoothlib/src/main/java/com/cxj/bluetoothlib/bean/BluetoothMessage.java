package com.cxj.bluetoothlib.bean;

import android.bluetooth.BluetoothGattCharacteristic;

/**
 * author : chenxiaojin
 * date : 2021/5/8 下午 03:47
 * description : 待发送蓝牙消息
 */
public class BluetoothMessage {

    private BluetoothGattCharacteristic characteristic;

    private byte[] data;

    private MessageType messageType;


    public enum MessageType {
        READ, WRITE
    }

    public BluetoothMessage(BluetoothGattCharacteristic characteristic, byte[] data, MessageType messageType) {
        this.characteristic = characteristic;
        this.data = data;
        this.messageType = messageType;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    public byte[] getData() {
        return data;
    }

    public MessageType getMessageType() {
        return messageType;
    }

}
