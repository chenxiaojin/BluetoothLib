package com.cxj.bluetoothlib.util;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author chenxiaojin
 * @date 2020/5/9
 * @description
 */
public class ByteUtil {
    public static final byte[] EMPTY_BYTES = new byte[]{};

    public static final int BYTE_MAX = 0xff;
    // 十六进制字符
    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * 字节数组转16进制字符
     *
     * @param bytes
     * @return
     */
    public static String bytesToHex(byte[] bytes) {
        // 一个byte为8位，可用两个十六进制位标识
        char[] buf = new char[bytes.length * 2];
        int a = 0;
        int index = 0;
        for (byte b : bytes) { // 使用除与取余进行转换
            if (b < 0) {
                a = 256 + b;
            } else {
                a = b;
            }

            buf[index++] = HEX_CHAR[a / 16];
            buf[index++] = HEX_CHAR[a % 16];
        }

        return new String(buf);
    }

    public static String string2Unicode(String word) {
        StringBuffer unicode = new StringBuffer();
        for (int i = 0; i < word.length(); i++) {
            // 取出每一个字符
            char c = word.charAt(i);
            // 转换为unicode, 实测不需要\\u
            unicode.append("\\u" + Integer.toHexString(c));
        }
        return unicode.toString();
    }

    public static String byteToHex(byte data) {
        StringBuilder str = new StringBuilder();
        String hex = Integer.toHexString(data & 0xFF);
        if (hex.length() == 1) {
            // 1得到一位的进行补0操作
            str.append("0");
        }
        str.append(hex);
        return str.toString();
    }

    /**
     * char转为16bit unicode
     * 高位在前, 低位在后
     *
     * @param word
     * @return
     */
    public static byte[] chatTo16bitUnicodeWithBytes(char word) {
        byte[] data = hexStringToByteArray(Integer.toHexString(word));
        for (int i = 0; i < data.length; i++) {
            Log.e("ByteUtil", "unicode:" + Integer.toHexString(data[i]));
        }

        if (data.length == 1) {
            data = new byte[]{0x00, data[0]};
        }
        return data;
    }


    /**
     * 16进制转字节数组
     *
     * @param s
     * @return
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }


    public static byte[] sumCheck(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum = sum + data[i];
        }
        if (sum > 0xff) { //超过了255，使用补码（补码 = 原码取反 + 1）
            sum = ~sum;
            sum = sum + 1;
        }
        return new byte[]{(byte) (sum & 0xff)};

    }

    /**
     * 字节数组转16进制字符串 中间中 空格符 分割
     *
     * @param bytes
     * @return
     */
    public static String bytesToHexSegment(byte[] bytes) {
        // 一个byte为8位，可用两个十六进制位标识
        char[] buf = new char[bytes.length * 2];
        int a = 0;
        int index = 0;
        StringBuffer stringBuffer = new StringBuffer();

        try {
            for (byte b : bytes) { // 使用除与取余进行转换
                if (b < 0) {
                    a = 256 + b;
                } else {
                    a = b;
                }

                buf[index++] = HEX_CHAR[a / 16];
                stringBuffer.append(buf[index - 1]);
                buf[index++] = HEX_CHAR[a % 16];
                stringBuffer.append(buf[index - 1]);
                stringBuffer.append(" ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuffer.toString();
    }

    /**
     * 字节数组转化成集合
     */
    public static List<Integer> bytesToArrayList(byte[] bytes) {
        List<Integer> datas = new ArrayList<>();
        if (bytes != null) {
            for (int i = 0; i < bytes.length; i++) {
                datas.add(bytes[i] & 0xff);
            }
        }
        return datas;
    }


    public static int bytes2Int(byte[] bytes) {
        //如果不与0xff进行按位与操作，转换结果将出错，有兴趣的同学可以试一下。
        int int1 = bytes[3] & 0xff;
        int int2 = (bytes[2] & 0xff) << 8;
        int int3 = (bytes[1] & 0xff) << 16;
        int int4 = (bytes[0] & 0xff) << 24;

        return int1 + int2 + int3 + int4;
    }

    public static int bytes2Short(byte[] bytes) {
        int int1 = (bytes[1] & 0xff);
        int int2 = (bytes[0] & 0xff) << 8;
        return int1 + int2;
    }

    public static byte[] short2Bytes(short n) {
        byte high = (byte) (0x00FF & (n >> 8));//定义第一个byte
        byte low = (byte) (0x00FF & n);//定义第二个byte
        return new byte[]{high, low};
    }

    /**
     * 十六进制String转换成Byte[] * @param hexString the hex string * @return byte[]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        if (hexString.length() % 2 != 0) {
            hexString = "0" + hexString;
        }
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    /**
     * Convert char to byte * @param c char * @return byte
     */
    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static void main(String[] args) {
        byte[] data = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x20};
        long l = bytes2Int(data);
        System.out.print(l);
    }


    public static byte[] getNonEmptyByte(byte[] bytes) {
        return bytes != null ? bytes : EMPTY_BYTES;
    }


    public static byte[] trimLast(byte[] bytes) {
        int i = bytes.length - 1;
        for (; i >= 0; i--) {
            if (bytes[i] != 0) {
                break;
            }
        }
        return Arrays.copyOfRange(bytes, 0, i + 1);
    }

    public static byte[] stringToBytes(String text) {
        int len = text.length();
        byte[] bytes = new byte[(len + 1) / 2];
        for (int i = 0; i < len; i += 2) {
            int size = Math.min(2, len - i);
            String sub = text.substring(i, i + size);
            bytes[i / 2] = (byte) Integer.parseInt(sub, 16);
        }
        return bytes;
    }

    public static boolean isEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    public static byte[] fromInt(int n) {
        byte[] bytes = new byte[4];

        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) (n >>> (i * 8));
        }

        return bytes;
    }

    public static boolean byteEquals(byte[] lbytes, byte[] rbytes) {
        if (lbytes == null && rbytes == null) {
            return true;
        }

        if (lbytes == null || rbytes == null) {
            return false;
        }

        int llen = lbytes.length;
        int rlen = rbytes.length;

        if (llen != rlen) {
            return false;
        }

        for (int i = 0; i < llen; i++) {
            if (lbytes[i] != rbytes[i]) {
                return false;
            }
        }

        return true;
    }

    public static byte[] fillBeforeBytes(byte[] bytes, int len, byte fill) {

        byte[] result = bytes;
        int oldLen = (bytes != null ? bytes.length : 0);

        if (oldLen < len) {
            result = new byte[len];

            for (int i = len - 1, j = oldLen - 1; i >= 0; i--, j--) {
                if (j >= 0) {
                    result[i] = bytes[j];
                } else {
                    result[i] = fill;
                }
            }
        }

        return result;
    }

    public static byte[] cutBeforeBytes(byte[] bytes, byte cut) {
        if (isEmpty(bytes)) {
            return bytes;
        }

        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != cut) {
                return Arrays.copyOfRange(bytes, i, bytes.length);
            }
        }

        return EMPTY_BYTES;
    }

    public static byte[] cutAfterBytes(byte[] bytes, byte cut) {
        if (isEmpty(bytes)) {
            return bytes;
        }

        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] != cut) {
                return Arrays.copyOfRange(bytes, 0, i + 1);
            }
        }

        return EMPTY_BYTES;
    }

    public static byte[] getBytes(byte[] bytes, int start, int end) {
        if (bytes == null) {
            return null;
        }

        if (start < 0 || start >= bytes.length) {
            return null;
        }

        if (end < 0 || end >= bytes.length) {
            return null;
        }

        if (start > end) {
            return null;
        }

        byte[] newBytes = new byte[end - start + 1];

        for (int i = start; i <= end; i++) {
            newBytes[i - start] = bytes[i];
        }

        return newBytes;
    }

    public static int ubyteToInt(byte b) {
        return (int) b & 0xFF;
    }

    public static boolean isAllFF(byte[] bytes) {
        int len = (bytes != null ? bytes.length : 0);

        for (int i = 0; i < len; i++) {
            if (ubyteToInt(bytes[i]) != BYTE_MAX) {
                return false;
            }
        }

        return true;
    }

    public static byte[] fromLong(long n) {
        byte[] bytes = new byte[8];

        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (n >>> (i * 8));
        }

        return bytes;
    }

    public static void copy(byte[] lbytes, byte[] rbytes, int lstart, int rstart) {
        if (lbytes != null && rbytes != null && lstart >= 0) {
            for (int i = lstart, j = rstart; j < rbytes.length && i < lbytes.length; i++, j++) {
                lbytes[i] = rbytes[j];
            }
        }
    }

    public static boolean equals(byte[] array1, byte[] array2) {
        return equals(array1, array2, Math.min(array1.length, array2.length));
    }

    public static boolean equals(byte[] array1, byte[] array2, int len) {
        if (array1 == array2) {
            return true;
        }
        if (array1 == null || array2 == null || array1.length < len || array2.length < len) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] get(byte[] bytes, int offset) {
        return get(bytes, offset, bytes.length - offset);
    }

    public static byte[] get(byte[] bytes, int offset, int len) {
        byte[] result = new byte[len];
        System.arraycopy(bytes, offset, result, 0, len);
        return result;
    }

    /**
     * 数字转二进制字符串，保留0
     * @param value
     * @return
     */
    public static String intToBinaryString(int value) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            str.append(value & 1);
            value = value >>> 1;
        }
        return str.reverse().toString();
    }
}
