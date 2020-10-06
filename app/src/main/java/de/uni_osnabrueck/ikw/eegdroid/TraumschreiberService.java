package de.uni_osnabrueck.ikw.eegdroid;

import android.util.Log;

import java.util.Arrays;
import java.util.UUID;


public class TraumschreiberService {
    public final static String DEVICE_NAME = "traumschreiber";
    private final static String TAG = "TraumschreiberService";

    public String mTraumschreiberDeviceAddress;
    private byte[] dpcmBuffer = new byte[30];
    private byte[] dpcmBuffer2 = new byte[30];
    private int[] previousData = new int[24];
    private boolean characteristic0Ready = false;

    public TraumschreiberService() {

    }

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

        boolean dpcmEncoded = true;
        int[] data_ints;
        int new_int;
        int bLen = newModel ? 3 : 2; // bytes needed to encode 1 int (old encodings)
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
            if (characteristicNumber == 0){
                System.arraycopy(data_bytes,0,dpcmBuffer,0,20);
                characteristic0Ready = true;
                data_ints = null;
                return data_ints;

            } else if (characteristic0Ready && characteristicNumber == 1){
                System.arraycopy(data_bytes,0,dpcmBuffer,20,10);
                System.arraycopy(data_bytes,10, dpcmBuffer2, 0, 10);
                data_ints = decodeDpcm(dpcmBuffer);

            } else if (characteristic0Ready && characteristicNumber == 2){
                System.arraycopy(data_bytes,0,dpcmBuffer2,10,20);
                data_ints = decodeDpcm(dpcmBuffer2);

            } else {
                data_ints = null;
                return data_ints;
            }

        }



        return data_ints;

    }

    /***
     * Converts bytes to ints and adds the values of the current data to the previous data.
     * @param  bytes
     * @return int[] data
     */
    public int[] decodeDpcm(byte[] bytes){
        Log.v(TAG, "Encoded Delta: " + Arrays.toString(bytes));
        int[] data = bytesTo10bitInts(bytes);
        Log.v(TAG, "Decoded Delta: " + Arrays.toString(data));

        for(int i=0; i<data.length; i++){
            data[i] += previousData[i];
        }

        return data;

    }
    /***
     * Turns an array of bytes into an array fo 10bit ints.
     * @param bytes
     * @return int[] data
     */
    public int[] bytesTo10bitInts(byte[] bytes){
        // Number of ints : bytes*8/10 (8bits per byte and 10bits per int)
        int[] data = new int[bytes.length*8/10];

        /**
        * Pattern repeats after 5 bytes. Therefore we process the bytes in chunks of 5.
        * Processing 5 bytes yields 4 (10bit) ints.
        * The index 'idx' of the resulting int array 'data' has to be adjusted at every loop step
        * to account for the gap between the indices resulting from the 5/4 byte-to-int ratio
        */
        int idx = 0;
        for(int i=0; i<=bytes.length-5; i+=5){
            idx = i * 4/5;
            data[idx+0] = ((bytes[i+0]&0xff) << 2) | ((bytes[i+1]&0xc0) >> 6);
            data[idx+1] = ((bytes[i+1]&0x3f) << 4) | ((bytes[i+2]&0xf0) >> 4);
            data[idx+2] = ((bytes[i+2]&0x0f) << 6) | ((bytes[i+3]&0xff) >> 2);
            data[idx+3] = ((bytes[i+3]&0x03) << 8) | ((bytes[i+4]&0xff) >> 0);
        }
        // -1024 turns unsigned 10bits into their signed 2's complement
        for(int i=0; i<data.length; i++){ if(data[i] > 511) data[i] -= 1024; }

        return data;
    }


}