package org.eu.hanana.reimu.mcvp;

public class Mathutil {
    public static byte[] shortArrayToByteArray(short[] shortArray) {
        byte[] byteArray = new byte[shortArray.length * 2]; // 一个short占两个字节

        for (int i = 0; i < shortArray.length; i++) {
            // 从short获取高位字节和低位字节
            byte highByte = (byte) (shortArray[i] >> 8); // 高8位
            byte lowByte = (byte) shortArray[i]; // 低8位

            // 将高位字节和低位字节存储在byteArray中
            byteArray[i * 2] = highByte;
            byteArray[i * 2 + 1] = lowByte;
        }

        return byteArray;
    }
}
