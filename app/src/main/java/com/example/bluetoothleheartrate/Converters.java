package com.example.bluetoothleheartrate;

public class Converters {
    public static byte[] toInt32Le(long value) {
        byte[] data = new byte[4];
        toInt32Le(data, 0, value);
        return data;
    }

    public static void toInt32Le(byte[] data, int offset, long value) {
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 0] = (byte) (value & 0xFF);
    }
}
