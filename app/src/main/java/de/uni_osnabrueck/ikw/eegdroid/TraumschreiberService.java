package de.uni_osnabrueck.ikw.eegdroid;

import android.util.Log;

import java.util.UUID;


public class TraumschreiberService {

    public final static String DEVICE_NAME = "traumschreiber";
    //Names chosen according to the python tflow_edge Traumschreiber.py
    public final static UUID BIOSIGNALS_UUID = UUID.fromString("faa7b588-19e5-f590-0545-c99f193c5c3e");
    public final static UUID LEDS_UUID = UUID.fromString("fcbea85a-4d87-18a2-2141-0d8d2437c0a4");
    String mTraumschreiberDeviceAddress;
    private final static String TAG = "TraumschreiberService";
    //public final static UUID UUID_HEART_RATE_MEASUREMENT =
    //       UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    public TraumschreiberService(String traumschreiberDeviceAddress) {
        this.mTraumschreiberDeviceAddress = traumschreiberDeviceAddress;
    }

    public static boolean isTraumschreiberDevice(String bluetoothDeviceName) {
        return bluetoothDeviceName.toLowerCase().contains(DEVICE_NAME);
    }

    public static boolean isNewModel(String bluetoothDeviceName) {
        return bluetoothDeviceName.startsWith("T");
    }

    /***
     * decompress takes a bytearray data_bytes and converts it to integers, according to the way the Traumschreiber transmits the data via bluetooth
     * @param data_bytes
     * @return int[] data_ints of the datapoint values as integers
     */
    public static int[] decompress(byte[] data_bytes, boolean newModel) {
        // Log Compressed Values
        /*String data_string = "";
        for (byte b: data_bytes) { data_string += String.format("%02X ", b); }
        Log.v(TAG, "Compressed: " + data_string);
        */
        newModel = true;
        int[] data_ints;
        int new_int;
        int bLen = newModel ? 3 : 2; // byte length per datapoint

        if (!newModel) {  // for old Traumschreiber
            data_ints = new int[data_bytes.length / bLen];
            Log.d("Decompressing", "decompress: " + String.format("%02X %02X ", data_bytes[0], data_bytes[1]));
            //https://stackoverflow.com/questions/9581530/converting-from-byte-to-int-in-java
            //Example: rno[0]&0x000000ff)<<24|(rno[1]&0x000000ff)<<16|
            for (int ch = 0; ch < data_bytes.length / bLen; ch++) {
                new_int = (data_bytes[ch * bLen + 1]) << 8 | (data_bytes[ch * bLen]) & 0xff;
                //new_int = new_int << 8;
                data_ints[ch] = new_int;
            }
        } else {  // for new Traumschreiber
            int header = data_bytes[0] &0xff; // Unsigned Byte
            int pkg_id = header / 16; // 1. Nibble
            int pkgs_lost = header % 16; // 2. Nibble
            //Log.v(TAG, String.format("ID: %02d  Lost pkgs: %02d",pkg_id, pkgs_lost));

            data_ints = new int[data_bytes.length / bLen + 2];   // +2: 1 for pkg id, 1 for lost pkg count
            data_ints[0] = pkg_id;
            data_ints[data_ints.length - 1] = pkgs_lost;

            // value of channel n is encoded by 3 bytes placed at positions 3n+1, 3n+2 and 3n+3 in data_bytes
            for (int ch = 0; ch < data_bytes.length / bLen; ch++) {
                new_int = (data_bytes[ch * bLen + 1]) << 16 | (data_bytes[ch * bLen + 2] & 0xff) << 8 | (data_bytes[ch * bLen + 3] & 0xff);
                data_ints[ch + 1] = new_int;
            }
        }
        // Log Decompressed Values
        /*data_string = "";
        for (int n: data_ints) { data_string += Integer.toString(n) + " "; }
        Log.v(TAG, "Decompressed " + data_string);
        */

        return data_ints;

    }
}