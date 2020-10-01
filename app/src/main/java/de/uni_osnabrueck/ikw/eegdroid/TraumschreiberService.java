package de.uni_osnabrueck.ikw.eegdroid;

import android.util.Log;

import java.util.UUID;


public class TraumschreiberService {


    public final static String DEVICE_NAME = "traumschreiber";
    //Names chosen according to the python tflow_edge Traumschreiber.py
    public final static UUID BIOSIGNALS_UUID = UUID.fromString("faa7b588-19e5-f590-0545-c99f193c5c3e");
    public final static UUID LEDS_UUID = UUID.fromString("fcbea85a-4d87-18a2-2141-0d8d2437c0a4");
    String mTraumschreiberDeviceAddress;
    private byte[] dpcmBuffer = byte[30];
    private boolean dpcmBufferReady = false;
    private byte[] dpcmBuffer2 = byte[30];
    private boolean dpcmBuffer2Ready = false;
    private int[] previousData = int[24];
    private boolean characteristic0Ready = false;
    private boolean characteristic1Ready = false;
    private boolean characteristic2Ready = false;
    private int[] data = int[24];
    private final static String TAG = "TraumschreiberService";

    // public final static UUID UUID_HEART_RATE_MEASUREMENT =
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
    public int[] decompress(byte[] data_bytes, boolean newModel, int characteristicNumber) {

        // Log Compressed Values
        String data_string = "";
        /*
        for (byte b: data_bytes) { data_string += String.format("%02X ", b); }
        Log.v(TAG, "Compressed: " + data_string);
        */

        boolean dpcmEncoded = true;


        int[] data_ints;
        int new_int;
        int bLen = newModel ? 3 : 2; // byte length per datapoint


        // ____OLD TRAUMSCHREIBER ____
        if (!newModel) {
            data_ints = new int[data_bytes.length / bLen];
            Log.d("Decompressing", "decompress: " + String.format("%02X %02X ", data_bytes[0], data_bytes[1]));
            //https://stackoverflow.com/questions/9581530/converting-from-byte-to-int-in-java
            //Example: rno[0]&0x000000ff)<<24|(rno[1]&0x000000ff)<<16|
            for (int ch = 0; ch < data_bytes.length / bLen; ch++) {
                new_int = (data_bytes[ch * bLen + 1]) << 8 | (data_bytes[ch * bLen]) & 0xff;
                //new_int = new_int << 8;
                data_ints[ch] = new_int;
            }

            // ____NEW TRAUMSCHREIBER____
        } else if (!dpcmEncoded){
            // Process Header
            int header = data_bytes[0] &0xff; // Unsigned Byte
            int pkg_id = header / 16; // 1. Nibble
            int pkgs_lost = header % 16; // 2. Nibble

            //Log.v(TAG, String.format("ID: %02d  Lost pkgs: %02d",pkg_id, pkgs_lost));

            // Prepare Data Array
            data_ints = new int[data_bytes.length / bLen + 2];   // +2: 1 for pkg id, 1 for lost pkg count
            data_ints[0] = pkg_id;
            data_ints[data_ints.length - 1] = pkgs_lost;

            // Decode
            /* value of channel n is encoded by 3 bytes placed at positions 3n+1, 3n+2 and 3n+3 in data_bytes*/
            for (int ch = 0; ch < data_bytes.length / bLen; ch++) {
                new_int = (data_bytes[ch * bLen + 1]) << 16 | (data_bytes[ch * bLen + 2] & 0xff) << 8 | (data_bytes[ch * bLen + 3] & 0xff);
                data_ints[ch + 1] = new_int;
            }

            // ____ DPCM DECODING ____
        } else {
            if(characteristicNumber == 0){
                // Write data_bytes to positions 0-19 on dpcmBuffer
                for (int i=0; i<data_bytes.length; i++){
                    dpcmBuffer[i] = data_bytes[i];
                }
                characteristic0Ready = true;

            } else if (characteristic0Ready && characteristicNumber == 1){
                // Split data_bytes in half,
                // write first half to positions 20-29 on dpcmBuffer,
                // write second half to positions 0-9 on dpcmBuffer2
                for (int i=0; i<data_bytes.length; i++){
                    if (i < data_bytes.length/2) {
                        dpcmBuffer[20 + i] = data_bytes[i];
                    } else {
                        dpcmBuffer2[i - 10] = data_bytes[i];
                    }
                }
                characteristic1Ready = true;
                }
            } else if (characteristic1Ready && characteristicNumber == 2){
                // Write data_bytes to positions 10-29 on dpcmBuffer2
                for (int i=0; i<data_bytes.length; i++){
                    dpcmBuffer2[10+i] = data_bytes[i];
                }
                characteristic2Ready = true;
            } else {
                Log.v(TAG, "No characteristic Ready.");
            }
            // Process DPCMbuffer, if they are ready
            if (characteristic0Ready && characteristic1Ready){
                // Do something with dpcmBuffer
            } else if(characteristic1Ready);

        }

        // Log Decompressed Values
        /*
        data_string = "";
        for (int n: data_ints) { data_string += Integer.toString(n) + " "; }
        Log.v(TAG, "Decompressed " + data_string);
        */

        return data_ints;

    }
}