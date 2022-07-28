package com.cxj.bluetooth.device.util

/**
 *  author : chenxiaojin
 *  date : 2021/3/10 下午 12:22
 *  description :
 */
object ByteConverter {


    /**
     * int 转成 两个字节
     * @param num 待转数字
     * @param isBigEndian 是否大端, 大端：高位在前，低位在后, 小端: 低位在前, 高位在后
     */
    fun intTo2ByteArray(num: Int, isBigEndian: Boolean): ByteArray {
        var byteArray = ByteArray(2)
        var lowH = ((num shr 8) and 0xff).toByte()
        var lowL = (num and 0xff).toByte()
        if (isBigEndian) {
            byteArray[0] = lowH
            byteArray[1] = lowL
        } else {
            byteArray[0] = lowL
            byteArray[1] = lowH
        }
        return byteArray
    }

    /**
     * int 转 字节数组(4字节)
     * @param num 待转换数字
     */
    private fun intTo4ByteArray(num: Int): ByteArray {
        var byteArray = ByteArray(4)
        var highH = ((num shr 24) and 0xff).toByte()
        var highL = ((num shr 16) and 0xff).toByte()
        var lowH = ((num shr 8) and 0xff).toByte()
        var lowL = (num and 0xff).toByte()
        byteArray[0] = highH
        byteArray[1] = highL
        byteArray[2] = lowH
        byteArray[3] = lowL
        return byteArray
    }



}