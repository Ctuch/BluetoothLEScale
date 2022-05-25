package com.example.bluetoothleheartrate;

import java.util.HashMap;
import java.util.UUID;

public class GattAttributeUUIDs {
    public static UUID WEIGHT_CHARACTERISTIC = UUID.fromString("00008a21-0000-1000-8000-00805f9b34fb");
    public static UUID WEIGHT_SERVICE = UUID.fromString("000078b2-0000-1000-8000-00805f9b34fb");
    public static UUID CMD_MEASUREMENT_CHARACTERISTIC =  UUID.fromString("00008a81-0000-1000-8000-00805f9b34fb");
    public static UUID CLIENT_CHARACTERISTIC_CONFIG = fromShortCode(0x2902);
    public static UUID FEATURE_MEASUREMENT_CHARACTERISTIC = fromShortCode(0x8a22); // indication, read-only
    public static UUID CUSTOM5_MEASUREMENT_CHARACTERISTIC = fromShortCode(0x8a82); // indication, read-only
    // public static UUID DEVICE_SERVICE = fromShortCode(0x180a);
    // public static UUID MANUFACTURE_NAME_CHARACTERISTIC = fromShortCode(0x2a29);


    private static final String STANDARD_SUFFIX = "-0000-1000-8000-00805f9b34fb";

    public static final UUID fromShortCode(long code) {
        return UUID.fromString(String.format("%08x%s", code, STANDARD_SUFFIX));
    }
}
