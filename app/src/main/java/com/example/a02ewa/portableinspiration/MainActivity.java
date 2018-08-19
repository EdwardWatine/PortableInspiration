package com.example.a02ewa.portableinspiration;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_MICROPHONE = 1;
    private final double AVERAGE_THRESHOLD_CONSTANT = 0.8; //the higher the number the lower the sensitivity (0-1)
    //affected by background noise (works by magnitude) as checks current magnitude is greater then average (historically) times this
    private final double INTER_NOTE_CONSTANT = 0.75; //checks by a factor of this on either side of the note (normally between 0.5 and 1)
    //higher is less sensitive (mostly unaffected by background noise but can lead to ignoring note changes)
    private final HashMap mymap = new HashMap(64, (float) 1);
    private int NOTE_RANGE = 64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MICROPHONE);
        } else {
            recordSound();
        }
    }

    public void recordSound() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int sampleRateInHz = 11025;//8000 44100, 22050 and 11025 //picked 11025 based on high piano notes C7
                int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int bufferSize = 2048;
                short sData[] = new short[bufferSize / Short.BYTES];
                double magnitude[] = new double[NOTE_RANGE]; //A1
                double relativeFreq[] = new double[magnitude.length];
                double relativeMag[] = new double[magnitude.length];
                final double freqList[] = new double[magnitude.length];
                double lastFreq = -1;

                String[] noteList = new String[] {"A", "A#/Bb", "B", "C", "C#/Db", "D", "D#/Eb", "E", "F", "F#/Gb", "G", "G#/Ab"};

                for (int i = 0; i < magnitude.length; i++) {
                    double current = Math.round(5500*Math.pow(2, i/12.0))/100.0;
                    relativeFreq[i] = current / sampleRateInHz;
                    freqList[i] = current;
                    mymap.put(current, noteList[i%12]+(1+((i+9)/12)));
                }
                System.out.println("BEFORE");
                AudioRecord recorder = new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        sampleRateInHz,
                        channelConfig,
                        audioFormat,
                        bufferSize
                );
                System.out.println("BEFORE2");
                recorder.startRecording();
                System.out.println("AFTER");

                int counter = 0;
                double freqHistTotal = 0;
                while (true) {
                    counter++;
                    recorder.read(
                            sData,
                            0,
                            sData.length
                    );
                    double doubleArray[] = new double[sData.length];
                    for (int i = 0; i < doubleArray.length; i++) {
                        doubleArray[i] = (double) sData[i];
                    }
                    computeDft(doubleArray, relativeFreq, magnitude);

            /*System.out.print("OUTPUT: ");
            for (int i=0; i<magnitude.length; i++){
                System.out.print(magnitude[i]+",");
            }
            System.out.println("");*/

                    double max = 0;
                    int highest = 0;
                    for (int i = 0; i < magnitude.length; i++) {
                        if (magnitude[i] > max) {
                            max = magnitude[i];
                            highest = i;
                        }
                    }

                    double average = freqHistTotal/counter;

                    double newFreq = freqList[highest];
                    for (int i=0; i < magnitude.length; i++){
                        relativeMag[i] = magnitude[i]/max;
                    }
                    if (average*AVERAGE_THRESHOLD_CONSTANT<max) {

                        double upperFreq = freqList[Math.min(highest+1, 63)];
                        double lowerFreq = freqList[Math.max(highest-1, 0)];
                        if (lastFreq==upperFreq || lastFreq==lowerFreq) {
                            double relFreqList1[] = {(newFreq + (lastFreq - newFreq) * (1 - INTER_NOTE_CONSTANT)) / sampleRateInHz};

                            double mag1[] = new double[1];
                            computeDft(doubleArray, relFreqList1, mag1);
                            if (mag1[0] > max) {
                                newFreq = lastFreq;
                            }
                        }
                        final double finalFreq = newFreq;
                        final String note = (String) mymap.get(newFreq);
                        System.out.println(String.format("\nFrequency: %f Note: %s", finalFreq, note));
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                //update UI

                            }
                        });
                    }
                    lastFreq = freqList[highest];
                    freqHistTotal += max;
                }
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_MICROPHONE:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Audio permissions denied", Toast.LENGTH_SHORT).show();
                } else {
                    recordSound();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /*
     * Discrete Fourier transform (Java)
     * by Project Nayuki, 2017. Public domain.
     * https://www.nayuki.io/page/how-to-implement-the-discrete-fourier-transform
     */

    public static void computeDft(double[] inreal, double[] atf, double[] magnitude) {
        int n = inreal.length;
        assert atf.length==magnitude.length;
        for (int k = 0; k < magnitude.length; k++) {  // For each output element
            double sumreal = 0;
            double sumimag = 0;
            for (int t = 0; t < n; t++) {  // For each input element
                double angle = 2 * Math.PI * t * atf[k];
                sumreal +=  inreal[t] * Math.cos(angle) + inreal[t] * Math.sin(angle);
                sumimag += -inreal[t] * Math.sin(angle) + inreal[t] * Math.cos(angle);
            }
            magnitude[k] = Math.sqrt(sumreal*sumreal+sumimag*sumimag);

        }
    }
}
