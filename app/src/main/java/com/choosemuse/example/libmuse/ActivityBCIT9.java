/**
 * Example of using libmuse library on android.
 * Interaxon, Inc. 2016
 */

package com.choosemuse.example.libmuse;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.choosemuse.libmuse.AnnotationData;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.LibmuseVersion;
import com.choosemuse.libmuse.MessageType;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConfiguration;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseFileFactory;
import com.choosemuse.libmuse.MuseFileReader;
import com.choosemuse.libmuse.MuseFileWriter;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;
import com.choosemuse.libmuse.Result;
import com.choosemuse.libmuse.ResultLevel;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This example will illustrate how to connect to a Muse headband,
 * register for and receive EEG data and disconnect from the headband.
 * Saving EEG data to a .muse file is also covered.
 *
 * For instructions on how to pair your headband with your Android device
 * please see:
 * http://developer.choosemuse.com/hardware-firmware/bluetooth-connectivity/developer-sdk-bluetooth-connectivity-2
 *
 * Usage instructions:
 * 1. Pair your headband if necessary.
 * 2. Run this project.
 * 3. Turn on the Muse headband.
 * 4. Press "Refresh". It should display all paired Muses in the Spinner drop down at the
 *    top of the screen.  It may take a few seconds for the headband to be detected.
 * 5. Select the headband you want to connect to and press "Connect".
 * 6. You should see EEG and accelerometer data as well as connection status,
 *    version information and relative alpha values appear on the screen.
 * 7. You can pause/resume data transmission with the button at the bottom of the screen.
 * 8. To disconnect from the headband, press "Disconnect"
 */
public class ActivityBCIT9 extends AppCompatActivity implements OnClickListener {

    /**
     * Tag used for logging purposes.
     */
    private final String TAG = "Noirtier de Villefort";

    /**
     * The MuseManager is how you detect Muse headbands and receive notifications
     * when the list of available headbands changes.
     */
    private MuseManagerAndroid manager;

    /**
     * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
     * headband, register listeners to receive EEG data and get headband
     * configuration and version information.
     */
    private Muse muse;

    /**
     * The ConnectionListener will be notified whenever there is a change in
     * the connection state of a headband, for example when the headband connects
     * or disconnects.
     *
     * Note that ConnectionListener is an inner class at the bottom of this file
     * that extends MuseConnectionListener.
     */
    private ConnectionListener connectionListener;

    /**
     * The DataListener is how you will receive EEG (and other) data from the
     * headband.
     *
     * Note that DataListener is an inner class at the bottom of this file
     * that extends MuseDataListener.
     */
    private DataListener dataListener;

    /**
     * Data comes in from the headband at a very fast rate; 220Hz, 256Hz or 500Hz,
     * depending on the type of headband and the preset configuration.  We buffer the
     * data that is read until we can update the UI.
     *
     * The stale flags indicate whether or not new data has been received and the buffers
     * hold the values of the last data packet received.  We are displaying the EEG, ALPHA_RELATIVE
     * and ACCELEROMETER values in this example.
     *
     * Note: the array lengths of the buffers are taken from the comments in
     * MuseDataPacketType, which specify 3 values for accelerometer and 6
     * values for EEG and EEG-derived packets.
     */
    private final double[] eegBuffer = new double[6];
    private boolean eegStale;
    private final double[] alphaBuffer = new double[6];
    private boolean alphaStale;
    private final double[] accelBuffer = new double[3];
    private boolean accelStale;

    private double eegBufferT9; // Temporal T9
    private double eegBufferT10; // Temporal T10
    private List<double[]> data;

    int c = 0;
    boolean jaw = true;

    // From the Eye blink module
    ImageView[] iv;
    TextView[] suggTV;
    Button btnBackspace;
    TextToSpeech tts;
    T9 T9Communicator;
    String lines[];
    TextView autoCompleteTextView;
    EditText accuEditText;
    ImageView ivRedSignalE;
    ImageView ivRedSignalM;
    private int blinkInterval;
    private int highlightInterval;
    CountDownTimer highlightTimer;
    CountDownTimer blinkTimer;
    CountDownTimer jawTimer;
    private int currentState = 0;
    int currentHighlight = 0;
    public boolean blinkWithinTime = false;
    public boolean jawWithinTime = false;
    public int blinkCount = 0;
    public int jawCount = 0;
    int currentSuggestion = -1;
    StringBuilder digitSequence = new StringBuilder("");
    String response = "";

    /**
     * We will be updating the UI using a handler instead of in packet handlers because
     * packets come in at a very high frequency and it only makes sense to update the UI
     * at about 60fps. The update functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private final Handler handler = new Handler();

    /**
     * In the UI, the list of Muses you can connect to is displayed in a Spinner object for this example.
     * This spinner adapter contains the MAC addresses of all of the headbands we have discovered.
     */
    private ArrayAdapter<String> spinnerAdapter;

    /**
     * It is possible to pause the data transmission from the headband.  This boolean tracks whether
     * or not the data transmission is enabled as we allow the user to pause transmission in the UI.
     */
    private boolean dataTransmission = true;

    /**
     * To save data to a file, you should use a MuseFileWriter.  The MuseFileWriter knows how to
     * serialize the data packets received from the headband into a compact binary format.
     * To read the file back, you would use a MuseFileReader.
     */
    private final AtomicReference<MuseFileWriter> fileWriter = new AtomicReference<>();

    /**
     * We don't want file operations to slow down the UI, so we will defer those file operations
     * to a handler on a separate thread.
     */
    private final AtomicReference<Handler> fileHandler = new AtomicReference<>();


    //--------------------------------------
    // Lifecycle / Connection code


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        Log.i(TAG, "LibMuse version=" + LibmuseVersion.instance().getString());

        WeakReference<ActivityBCIT9> weakActivity =
                new WeakReference<ActivityBCIT9>(this);
        // Register a listener to receive connection state changes.
        connectionListener = new ConnectionListener(weakActivity);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakActivity);
        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(new MuseL(weakActivity));

        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        ensurePermissions();

        // Load and initialize our UI.
        initUI();

        // Start up a thread for asynchronous file operations.
        // This is only needed if you want to do File I/O.
        fileThread.start();

        // Start our asynchronous updates of the UI.
//        handler.post(tickUi);

        // The user has pressed the "Connect" button to connect to
        // the headband in the spinner.

        // Listening is an expensive operation, so now that we know
        // which headband the user wants to connect to we can stop
        // listening for other headbands.
        manager.stopListening();
        manager.startListening();

        // Cache the Muse that the user has selected.
        muse = Config.muse;
        // Unregister all prior listeners and register our data listener to
        // receive the MuseDataPacketTypes we are interested in.  If you do
        // not register a listener for a particular data type, you will not
        // receive data packets of that type.
        muse.unregisterAllListeners();
        muse.registerConnectionListener(connectionListener);
        muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
        muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
        muse.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER);
        muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
        muse.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
        muse.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);
        muse.registerDataListener(dataListener, MuseDataPacketType.ARTIFACTS);

        // Initiate a connection to the headband and stream the data asynchronously.
        muse.runAsynchronously();

        blinkTimer = new CountDownTimer(blinkInterval, blinkInterval) {
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                if (blinkCount == 6) {

                    // The user has pressed the "Disconnect" button.
                    // Disconnect from the selected Muse.
                    if (muse != null) {
                        muse.disconnect();
                    }
                    Intent intent = new Intent(ActivityBCIT9.this, MainActivityBCI.class);
                    startActivity(intent);
                }

                switch (currentState) {
                    case 0:
                        // In keyboard
                        switch (blinkCount) {
                            case 2:
                            case 3:
                                if(currentHighlight == 0){
                                    playAccumatedWords();
                                    currentState = 0;
                                    currentSuggestion = 0;

                                    highlightTimer.cancel();
                                    highlightTimer.start();
                                }else {
                                    setKey(Integer.toString(currentHighlight + 1));
                                    response = T9.getResponse(digitSequence.toString());
                                    if (response.isEmpty()) {
                                        showToast("Vocabulary not Found in Dictionary!", Toast.LENGTH_SHORT);
                                    } else {
                                        autoCompleteTextView.setText(response);
                                        currentState = 1;
                                        currentHighlight = 0;
                                        try {
                                            Queue<Word> words = T9.getWordQueue(digitSequence.toString());
                                            resetSuggestionColor();
                                            for (int i = 0; i < 5 && i < words.size(); i++) {

                                                if (i < words.size() - 1) {
                                                    suggTV[i].setText(words.toArray()[i + 1].toString());
                                                } else {
                                                    setRemainingSuggestions(i);
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            // Catch code
                                        }
                                    }
                                }

                                highlightTimer.cancel();
                                highlightTimer.start();
                                break;
                        }
                        break;
                    case 1:
                        // In editView and list
                        switch (blinkCount) {
                            case 2:
                            case 3:
                                if(currentHighlight == 0 || currentHighlight == 1){
                                    if (accuEditText.getText().length() == 0) {
                                        accuEditText.append(autoCompleteTextView.getText().toString());
                                    } else {
                                        accuEditText.append(" " + autoCompleteTextView.getText().toString());
                                    }
                                    digitSequence.delete(0, digitSequence.length());
                                    autoCompleteTextView.setText("");
                                    currentSuggestion = 0;
                                    currentState = 3;
                                    hideSuggestions();
                                }
                                else {
                                    if (accuEditText.getText().length() == 0) {
                                        accuEditText.append(suggTV[currentHighlight - 2].getText().toString());
                                    } else {
                                        accuEditText.append(" " + suggTV[currentHighlight - 2].getText().toString());
                                    }
                                    digitSequence.delete(0, digitSequence.length());
                                    autoCompleteTextView.setText("");
                                    currentSuggestion = 0;
                                    currentState = 3;
                                    hideSuggestions();
                                }

                                highlightTimer.cancel();
                                highlightTimer.start();
                                break;
                        }
                        break;
                    case 2:
                        // In backspace
                        switch (blinkCount) {
                            case 2:
                            case 3:
                                if (accuEditText.getText().length() == 0) {
                                    accuEditText.append(suggTV[4].getText().toString());
                                } else {
                                    accuEditText.append(" " + suggTV[4].getText().toString());
                                }
                                digitSequence.delete(0, digitSequence.length());
                                autoCompleteTextView.setText("");
                                currentSuggestion = 0;
                                currentState = 3;
                                hideSuggestions();
                                highlightTimer.cancel();
                                highlightTimer.start();

                                break;
                        }
                        break;
                    case 3:
                        // In Accu
                        switch (blinkCount) {
                            case 2:
                            case 3:
//                                playAccumatedWords();
//                                currentState = 0;
//                                currentSuggestion = 0;
//
//                                highlightTimer.cancel();
//                                highlightTimer.start();
                                if (autoCompleteTextView.getText().length() != 0) {
                                    autoCompleteTextView.setText(
                                            autoCompleteTextView.getText().toString().
                                                    substring(0, autoCompleteTextView.getText().length() - 1));
                                }
                                digitSequence.delete(digitSequence.length() - 1, digitSequence.length());
                                currentState = 0;
                                currentSuggestion = 0;
                                hideSuggestions();

                                highlightTimer.cancel();
                                highlightTimer.start();
                                break;
                        }
                        break;
                }

                blinkCount = 0;
                blinkWithinTime = false;
            }
        };

        jawTimer = new CountDownTimer(blinkInterval, blinkInterval) {
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                if (jawCount > 3) {
                    playAccumatedWords();
                    currentState = 0;
                    currentSuggestion = 0;
                }

                jawCount = 0;
                jawWithinTime = false;
            }
        };

    }

    public void hideSuggestions() {
        for (int i = 0; i < 5; i++)
            suggTV[i].setText("");
    }

    public void setRemainingSuggestions(int current) {
//        resetSuggestionColor();
        String c = autoCompleteTextView.getText().toString();
        int i = 0;
        boolean isAlreadyShown;
        while (current != 5 && i != lines.length) {

            if (lines[i].startsWith(c)) {
                isAlreadyShown = false;
                for (int iter = 0; iter < current; iter++) {
                    if (suggTV[iter].getText().toString().equals(lines[i]) || autoCompleteTextView.getText().toString().equals(lines[i])) {
                        isAlreadyShown = true;
                        break;
                    }
                }
                if (!isAlreadyShown) {
                    suggTV[current].setText(lines[i]);
                    current++;
                }
            }
            i++;
        }
    }

    private void setKey(String key) {
        digitSequence.append(key);
    }

    public void playAccumatedWords() {

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {

                if (status == TextToSpeech.SUCCESS) {

                    int result = tts.setLanguage(Locale.US);

                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                        Log.e("TTS", "This Language is not supported");
                        showToast("This Language is not supported", Toast.LENGTH_SHORT);
                    } else {
                        tts.speak(accuEditText.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
                        accuEditText.setText("");
                    }

                } else {
//                    Log.e("TTS", "Initilization Failed!");
                    showToast("Initilization Failed!", Toast.LENGTH_SHORT);
                }

            }
        }

        );

    }

    public void showToast(final String msg, final int timeStyle) {
        ActivityBCIT9.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }

        });
    }

    protected void onPause() {
        super.onPause();
        // It is important to call stopListening when the Activity is paused
        // to avoid a resource leak from the LibMuse library.
        manager.stopListening();
    }

    public boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.refresh) {
            // The user has pressed the "Refresh" button.
            // Start listening for nearby or paired Muse headbands. We call stopListening
            // first to make sure startListening will clear the list of headbands and start fresh.
            manager.stopListening();
            manager.startListening();

        } else if (v.getId() == R.id.connect) {



        } else if (v.getId() == R.id.disconnect) {

        } else if (v.getId() == R.id.pause) {

            // The user has pressed the "Pause/Resume" button to either pause or
            // resume data transmission.  Toggle the state and pause or resume the
            // transmission on the headband.
            if (muse != null) {
                dataTransmission = !dataTransmission;
                muse.enableDataTransmission(dataTransmission);
            }
        }
    }

    //--------------------------------------
    // Permissions

    /**
     * The ACCESS_COARSE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.

            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which){
                            dialog.dismiss();
                            ActivityCompat.requestPermissions(ActivityBCIT9.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    0);
                        }
                    };

            // This is the context dialog which explains to the user the reason we are requesting
            // this permission.  When the user presses the positive (I Understand) button, the
            // standard Android permission dialog will be displayed (as defined in the button
            // listener above).
            AlertDialog introDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_dialog_title)
                    .setMessage(R.string.permission_dialog_description)
                    .setPositiveButton(R.string.permission_dialog_understand, buttonListener)
                    .create();
            introDialog.show();
        }
    }


    //--------------------------------------
    // Listeners

    /**
     * You will receive a callback to this method each time a headband is discovered.
     * In this example, we update the spinner with the MAC address of the headband.
     */
//    public void museListChanged() {
//        final List<Muse> list = manager.getMuses();
//        spinnerAdapter.clear();
//        for (Muse m : list) {
//            spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
//        }
//    }

    /**
     * You will receive a callback to this method each time there is a change to the
     * connection state of one of the headbands.
     * @param p     A packet containing the current and prior connection states
     * @param muse  The headband whose state changed.
     */
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;
        Log.i(TAG, status);

        // Update the UI with the change in connection state.
        handler.post(new Runnable() {
            @Override
            public void run() {

                final TextView statusText = (TextView) findViewById(R.id.con_status);

//                final MuseVersion museVersion = muse.getMuseVersion();
//                final TextView museVersionText = (TextView) findViewById(R.id.version);
//                // If we haven't yet connected to the headband, the version information
//                // will be null.  You have to connect to the headband before either the
//                // MuseVersion or MuseConfiguration information is known.
//                if (museVersion != null) {
//                    final String version = museVersion.getFirmwareType() + " - "
//                            + museVersion.getFirmwareVersion() + " - "
//                            + museVersion.getProtocolVersion();
//                    museVersionText.setText(version);
//                } else {
//                    museVersionText.setText("R.string.undefined");
//                }
            }
        });

        if (current == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Muse disconnected:" + muse.getName());
            // Save the data file once streaming has stopped.
            saveFile();
            // We have disconnected from the headband, so set our cached copy to null.
            this.muse = null;
        }
    }

    /**
     * You will receive a callback to this method each time the headband sends a MuseDataPacket
     * that you have registered.  You can use different listeners for different packet types or
     * a single listener for all packet types as we have done here.
     * @param p     The data packet containing the data from the headband (eg. EEG data)
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
        writeDataPacketToFile(p);

        // valuesSize returns the number of data values contained in the packet.
        final long n = p.valuesSize();
        switch (p.packetType()) {
            case EEG:
                assert(eegBuffer.length >= n);
                getEegChannelValues(eegBuffer,p);
                eegStale = true;
                break;
            case ACCELEROMETER:
                assert(accelBuffer.length >= n);
                getAccelValues(p);
                accelStale = true;
                break;
            case ALPHA_RELATIVE:
                assert(alphaBuffer.length >= n);
                getEegChannelValues(alphaBuffer,p);
                alphaStale = true;
                break;
            case BATTERY:
            case DRL_REF:
            case QUANTIZATION:
            case ARTIFACTS:
//                Toast.makeText(getApplicationContext(), "Artifact1", Toast.LENGTH_SHORT).show();
//                TextView tvAxn = (TextView) findViewById(R.id.tvAxn);
//                tvAxn.setText(Double.toString(p.values().get(1)));
//                c++;


            default:
                break;
        }
    }

    /**
     * You will receive a callback to this method each time an artifact packet is generated if you
     * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
     * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
     * @param p     The artifact packet with the data from the headband.
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {

//        TextView tvAxn = (TextView) findViewById(R.id.tvAxn);
        if (p.getJawClench()) {

            if (eegBufferT9 > 900 | eegBufferT9 < 800)
            {
                if (eegBufferT10 > 900 | eegBufferT10 < 800)
                {

                    Toast.makeText(getApplicationContext(), "Jaw"+jawCount, Toast.LENGTH_SHORT).show();
                    jaw = false;
                    new Thread(new Runnable()  {
                        public void run() {
                            try {
                                Thread.sleep(1000);
                                jaw = true;
                            }
                            catch (Exception e){}
                        }
                    }).start();
                    if (jawWithinTime) {
                        jawTimer.cancel();
                        jawTimer.start();
                        jawWithinTime = true;
                        jawCount++;
                    }
                    else {
                        jawTimer.start();
                        jawWithinTime = true;
                        jawCount = 1;
                    }

//                    tvAxn.setText("Jaw");
//                    new Thread(new Runnable()  {
//                        public void run() {
//                            try {
//                                ivRedSignalM.setVisibility(View.VISIBLE);
//                                Thread.sleep(200);
//                                ivRedSignalM.setVisibility(View.INVISIBLE);
//                            }
//                            catch (Exception e){}
//                        }
//                    }).start();
                }
            }
        }
        if (p.getBlink() & jaw) {
//            tvAxn.setText("Eye");
            Toast.makeText(getApplicationContext(), "Eye"+blinkCount, Toast.LENGTH_SHORT).show();
            if (blinkWithinTime) {
                blinkTimer.cancel();
                blinkTimer.start();
                blinkWithinTime = true;
                blinkCount++;
            }
            else {
                blinkTimer.start();
                blinkWithinTime = true;
                blinkCount = 1;
            }
//            new Thread(new Runnable()  {
//                public void run() {
//                    try {
//                        ivRedSignalE.setVisibility(View.VISIBLE);
//                        Thread.sleep(200);
//                        ivRedSignalE.setVisibility(View.INVISIBLE);
//                    }
//                    catch (Exception e){}
//                }
//            }).start();
        }

    }

    /**
     * Helper methods to get different packet values.  These methods simply store the
     * data in the buffers for later display in the UI.
     *
     * getEegChannelValue can be used for any EEG or EEG derived data packet type
     * such as EEG, ALPHA_ABSOLUTE, ALPHA_RELATIVE or HSI_PRECISION.  See the documentation
     * of MuseDataPacketType for all of the available values.
     * Specific packet types like ACCELEROMETER, GYRO, BATTERY and DRL_REF have their own
     * getValue methods.
     */
    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }

    private void getAccelValues(MuseDataPacket p) {
//        accelBuffer[0] = p.getAccelerometerValue(Accelerometer.X);
//        accelBuffer[1] = p.getAccelerometerValue(Accelerometer.Y);
//        accelBuffer[2] = p.getAccelerometerValue(Accelerometer.Z);
    }


    //--------------------------------------
    // UI Specific methods

    /**
     * Initializes the UI of the     example application.
     */
    private void initUI() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_t9_temp);
        iv = new ImageView[8];
        iv[0] = findViewById(R.id.iv2);
        iv[1] = findViewById(R.id.iv3);
        iv[2] = findViewById(R.id.iv4);
        iv[3] = findViewById(R.id.iv5);
        iv[4] = findViewById(R.id.iv6);
        iv[5] = findViewById(R.id.iv7);
        iv[6] = findViewById(R.id.iv8);
        iv[7] = findViewById(R.id.iv9);

        suggTV = new TextView[5];
        suggTV[0] = findViewById(R.id.tvSuggestion1);
        suggTV[1] = findViewById(R.id.tvSuggestion2);
        suggTV[2] = findViewById(R.id.tvSuggestion3);
        suggTV[3] = findViewById(R.id.tvSuggestion4);
        suggTV[4] = findViewById(R.id.tvSuggestion5);
        resetSuggestionColor();
        btnBackspace = findViewById(R.id.btnBackspace);

        T9Communicator = new T9();

        T9.makeDictionary(getResources().getStringArray(R.array.vocabularyMin));

        lines = getResources().getStringArray(R.array.vocabulary);
        autoCompleteTextView = findViewById(R.id.autoCompleteTextView);
        accuEditText = findViewById(R.id.accuEditText);
        ivRedSignalE = findViewById(R.id.ivRedSignalE);
        ivRedSignalM = findViewById(R.id.ivRedSignalM);
        blinkInterval = getApplicationContext().getResources().getInteger(R.integer.blinkInterval);
        highlightInterval = getApplicationContext().getResources().getInteger(R.integer.highlightInterval);
//        setContentView(R.layout.activity_main_bci);
//        Button refreshButton = (Button) findViewById(R.id.refresh);
//        refreshButton.setOnClickListener(this);
//        Button connectButton = (Button) findViewById(R.id.connect);
//        connectButton.setOnClickListener(this);
//        Button disconnectButton = (Button) findViewById(R.id.disconnect);
//        disconnectButton.setOnClickListener(this);
//        Button pauseButton = (Button) findViewById(R.id.pause);
//        pauseButton.setOnClickListener(this);
//
//        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
//        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
//        musesSpinner.setAdapter(spinnerAdapter);
        highlightTimer = new CountDownTimer(highlightInterval, highlightInterval) {
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                changeHighlightedItem();
                highlightTimer.start();
            }
        };
        highlightTimer.start();
    }


    void changeHighlightedItem(){

        iv[0].setImageResource(R.drawable.b2);
        iv[1].setImageResource(R.drawable.b3);
        iv[2].setImageResource(R.drawable.b4);
        iv[3].setImageResource(R.drawable.b5);
        iv[4].setImageResource(R.drawable.b6);
        iv[5].setImageResource(R.drawable.b7);
        iv[6].setImageResource(R.drawable.b8);
        iv[7].setImageResource(R.drawable.b9);
        autoCompleteTextView.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
        btnBackspace.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
        accuEditText.setTextColor(getResources().getColor(R.color.colorPrimaryDark));
        for(int i = 0; i < 5; i++)
            suggTV[i].setTextColor(getResources().getColor(R.color.colorPrimaryDark));
        if (currentState == 0) {
            switch (currentHighlight) {
                case 0:
                    iv[0].setImageResource(R.drawable.gr2);
                    break;
                case 1:
                    iv[1].setImageResource(R.drawable.gr3);
                    break;
                case 2:
                    iv[2].setImageResource(R.drawable.gr4);
                    break;
                case 3:
                    iv[3].setImageResource(R.drawable.gr5);
                    break;
                case 4:
                    iv[4].setImageResource(R.drawable.gr6);
                    break;
                case 5:
                    iv[5].setImageResource(R.drawable.gr7);
                    break;
                case 6:
                    iv[6].setImageResource(R.drawable.gr8);
                    break;
                case 7:
                    iv[7].setImageResource(R.drawable.gr9);
                    break;
            }
            // Change the below line according to the length of text
            if(currentHighlight < 8)
                currentHighlight++;
            else {
                iv[0].setImageResource(R.drawable.gr2);
                currentHighlight = 1;
            }
//    currentHighlight = (currentHighlight + 1) % 9;
        }else if(currentState == 1 && currentHighlight == 0) {
            autoCompleteTextView.setTextColor(getResources().getColor(R.color.colorHighlight));
            currentHighlight++;
        }else if(currentState == 1) {
            suggTV[currentHighlight - 1].setTextColor(getResources().getColor(R.color.colorHighlight));
            if(currentHighlight == 5)
            {
                currentState++;
            }
            else
                currentHighlight++;
        }
        else if(currentState == 2) {
            btnBackspace.setTextColor(getResources().getColor(R.color.colorHighlight));
            currentState++;
//    currentHighlight = 1;
        }
        else if(currentState == 3) {
            accuEditText.setTextColor(getResources().getColor(R.color.colorHighlight));
            currentState = 0;
            currentHighlight = 0;
        }
    }

    /**
     * The runnable that is used to update the UI at 60Hz.
     *
     * We update the UI from this Runnable instead of in packet handlers
     * because packets come in at high frequency -- 220Hz or more for raw EEG
     * -- and it only makes sense to update the UI at about 60fps. The update
     * functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
//    private final Runnable tickUi = new Runnable() {
//        @Override
//        public void run() {
//            if (eegStale) {
//                updateEeg();
//            }
//            if (accelStale) {
//                updateAccel();
//            }
//            if (alphaStale) {
//                updateAlpha();
//            }
//            handler.postDelayed(tickUi, 1000 / 60);
//        }
//    };

    /**
     * The following methods update the TextViews in the UI with the data
     * from the buffers.
     */
    private void updateAccel() {
//        TextView acc_x = (TextView)findViewById(R.id.acc_x);
//        TextView acc_y = (TextView)findViewById(R.id.acc_y);
//        TextView acc_z = (TextView)findViewById(R.id.acc_z);
//        acc_x.setText(String.format("%6.2f", accelBuffer[0]));
//        acc_y.setText(String.format("%6.2f", accelBuffer[1]));
//        acc_z.setText(String.format("%6.2f", accelBuffer[2]));
    }

//    private void updateEeg() {
//        eegBufferT9 = eegBuffer[0];
//        eegBufferT10 = eegBuffer[3];
////        TextView tp9 = (TextView)findViewById(R.id.eeg_tp9);
////        TextView fp1 = (TextView)findViewById(R.id.eeg_af7);
////        TextView fp2 = (TextView)findViewById(R.id.eeg_af8);
////        TextView tp10 = (TextView)findViewById(R.id.eeg_tp10);
////        tp9.setText(String.format("%6.2f", eegBuffer[0]));
////        fp1.setText(String.format("%6.2f", eegBuffer[1]));
////        fp2.setText(String.format("%6.2f", eegBuffer[2]));
////        tp10.setText(String.format("%6.2f", eegBuffer[3]));
//        data.add(eegBuffer);
//    }

//    private void updateAlpha() {
////        TextView elem1 = (TextView)findViewById(R.id.elem1);
////        elem1.setText(String.format("%6.2f", alphaBuffer[0]));
////        TextView elem2 = (TextView)findViewById(R.id.elem2);
////        elem2.setText(String.format("%6.2f", alphaBuffer[1]));
////        TextView elem3 = (TextView)findViewById(R.id.elem3);
////        elem3.setText(String.format("%6.2f", alphaBuffer[2]));
////        TextView elem4 = (TextView)findViewById(R.id.elem4);
////        elem4.setText(String.format("%6.2f", alphaBuffer[3]));
//    }


    //--------------------------------------
    // File I/O

    /**
     * We don't want to block the UI thread while we write to a file, so the file
     * writing is moved to a separate thread.
     */
    private final Thread fileThread = new Thread() {
        @Override
        public void run() {
            Looper.prepare();
            fileHandler.set(new Handler());
            final File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            final File file = new File(dir, "new_muse_file.muse" );
            // MuseFileWriter will append to an existing file.
            // In this case, we want to start fresh so the file
            // if it exists.
            if (file.exists()) {
                file.delete();
            }
            Log.i(TAG, "Writing data to: " + file.getAbsolutePath());
            fileWriter.set(MuseFileFactory.getMuseFileWriter(file));
            Looper.loop();
        }
    };

    /**
     * Writes the provided MuseDataPacket to the file.  MuseFileWriter knows
     * how to write all packet types generated from LibMuse.
     * @param p     The data packet to write.
     */
    private void writeDataPacketToFile(final MuseDataPacket p) {
        Handler h = fileHandler.get();
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    fileWriter.get().addDataPacket(0, p);
                }
            });
        }
    }

    /**
     * Flushes all the data to the file and closes the file writer.
     */
    private void saveFile() {
        Handler h = fileHandler.get();
        if (h != null) {
            h.post(new Runnable() {
                @Override public void run() {
                    MuseFileWriter w = fileWriter.get();
                    // Annotation strings can be added to the file to
                    // give context as to what is happening at that point in
                    // time.  An annotation can be an arbitrary string or
                    // may include additional AnnotationData.
                    w.addAnnotationString(0, "Disconnected");
                    w.flush();
                    w.close();
                }
            });
        }
    }

    /**
     * Reads the provided .muse file and prints the data to the logcat.
     * @param name  The name of the file to read.  The file in this example
     *              is assumed to be in the Environment.DIRECTORY_DOWNLOADS
     *              directory.
     */
    private void playMuseFile(String name) {

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, name);

        final String tag = "Muse File Reader";

        if (!file.exists()) {
            Log.w(tag, "file doesn't exist");
            return;
        }

        MuseFileReader fileReader = MuseFileFactory.getMuseFileReader(file);

        // Loop through each message in the file.  gotoNextMessage will read the next message
        // and return the result of the read operation as a Result.
        Result res = fileReader.gotoNextMessage();
        while (res.getLevel() == ResultLevel.R_INFO && !res.getInfo().contains("EOF")) {

            MessageType type = fileReader.getMessageType();
            int id = fileReader.getMessageId();
            long timestamp = fileReader.getMessageTimestamp();

            Log.i(tag, "type: " + type.toString() +
                  " id: " + Integer.toString(id) +
                  " timestamp: " + String.valueOf(timestamp));

            switch(type) {
                // EEG messages contain raw EEG data or DRL/REF data.
                // EEG derived packets like ALPHA_RELATIVE and artifact packets
                // are stored as MUSE_ELEMENTS messages.
                case EEG:
                case BATTERY:
                case ACCELEROMETER:
                case QUANTIZATION:
                case GYRO:
                case MUSE_ELEMENTS:
                    MuseDataPacket packet = fileReader.getDataPacket();
                    Log.i(tag, "data packet: " + packet.packetType().toString());
                    break;
                case VERSION:
                    MuseVersion version = fileReader.getVersion();
                    Log.i(tag, "version" + version.getFirmwareType());
                    break;
                case CONFIGURATION:
                    MuseConfiguration config = fileReader.getConfiguration();
                    Log.i(tag, "config" + config.getBluetoothMac());
                    break;
                case ANNOTATION:
                    AnnotationData annotation = fileReader.getAnnotation();
                    Log.i(tag, "annotation" + annotation.getData());
                    break;
                default:
                    break;
            }

            // Read the next message.
            res = fileReader.gotoNextMessage();
        }
    }

    //--------------------------------------
    // Listener translators
    //
    // Each of these classes extend from the appropriate listener and contain a weak reference
    // to the activity.  Each class simply forwards the messages it receives back to the Activity.
    class MuseL extends MuseListener {
        final WeakReference<ActivityBCIT9> activityRef;

        MuseL(final WeakReference<ActivityBCIT9> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void museListChanged() {
//            activityRef.get().museListChanged();
        }
    }

    class ConnectionListener extends MuseConnectionListener {
        final WeakReference<ActivityBCIT9> activityRef;

        ConnectionListener(final WeakReference<ActivityBCIT9> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
            activityRef.get().receiveMuseConnectionPacket(p, muse);
        }
    }

    class DataListener extends MuseDataListener {
        final WeakReference<ActivityBCIT9> activityRef;

        DataListener(final WeakReference<ActivityBCIT9> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
            activityRef.get().receiveMuseDataPacket(p, muse);
        }

        @Override
        public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
            activityRef.get().receiveMuseArtifactPacket(p, muse);
        }
    }


    public void resetSuggestionColor() {
        for (int i = 0; i < 5; i++) {
            suggTV[i].setTextColor(getResources().getColor(R.color.colorAccentMild));
        }
    }
}


class T9 {

    /**
     * T9 dictionary is the hash table having the digit seq mapped to words the
     * digit sequence is the key and the word tree is the value
     */
    public static HashMap<String, Queue<Word>> T9dictionary = new LinkedHashMap();

    /**
     * T9dicitionary is constructed.
     */
    public T9() {
//        makeDictionary();
//        System.out.println(T9dictionary);
    }

    /**
     * @param queries
     * @return response for the query
     * this method is only used when this code is run.
     */
    public static String respondQuery(String[] queries) {
        StringBuilder response = new StringBuilder();

        for (String eachQuery : queries) {
            response.append(getResponse(eachQuery) + " ");
        }
        return response.toString();
    }

    /**
     * @param query
     * @return
     * @throws SequenceNotFoundException converts the digit sequence input by the user into words and
     *                                   returns it
     */
    public static String getResponse(String query) {
        Queue<Word> WordQueue;
        try {
            WordQueue = getWordQueue(query);
        } catch (SequenceNotFoundException e) {
            System.out.println(e.getMessage());
            return "";
        }
        return (WordQueue.peek().toString().substring(0, query.length()));
    }

    /**
     * @param query
     * @return
     * @throws SequenceNotFoundException returns the wordQueue corresponding to the given Sequence
     */
    public static Queue<Word> getWordQueue(String query) throws SequenceNotFoundException {
        if (!T9dictionary.containsKey(query)) {
            Set<String> Keys = T9dictionary.keySet();
            for (String eachKey : Keys) {
                if (eachKey.startsWith(query)) {
                    System.out.println("in loop: " + eachKey);
                    return (T9dictionary.get(eachKey));
                }
            }
            throw (new SequenceNotFoundException(query));
        }
        return T9dictionary.get(query);
    }

    /**
     * @param eachQuery
     * @param mayBeThis
     * @throws SequenceNotFoundException
     * @returns Word In case the Word at the head of the tree is not the
     * expected one, on pressing * (T9GUI) this method gets Invoked and
     * based on the number of times * is pressed the tree returns a
     * different Word everytime
     */
    public static String notThisWord(String eachQuery, int mayBeThis) throws SequenceNotFoundException {
        Queue<Word> WordQueue = getWordQueue(eachQuery);
        List<Word> temporaryList = new ArrayList();

        int numberOfWords = WordQueue.size();
        int notThisWord = 1;
        System.out.println(numberOfWords + " " + mayBeThis + " " + (mayBeThis % numberOfWords));
        // till the tree is not empty and a different word is not encountered

        while ((!WordQueue.isEmpty())
                && (notThisWord != (mayBeThis % numberOfWords)) && (mayBeThis % numberOfWords != 0)) {
            temporaryList.add(WordQueue.remove());
            System.out.println("dict " + WordQueue);

            notThisWord = (notThisWord + 1) % numberOfWords;
        }

        String mayBeThisWord = WordQueue.peek().toString();
        WordQueue.addAll(temporaryList);
        return mayBeThisWord.substring(0, eachQuery.length());

    }

    /**
     * reads a dicitionary word by word adding the sequence to the T9 dictionary
     */
    public static void makeDictionary(String lines[]) {

        try {
//			FileInputStream fstream = new FileInputStream(
//					"../T9/Dictionary.txt");
            String sequence;
            for (String line : lines) {
                sequence = generateSeq(line.toLowerCase());
                insert(sequence, line);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * @param word
     * @returns an equivalent digit sequence to the word
     */
    public static String generateSeq(String word) {
        StringBuilder sequence = new StringBuilder();
        for (char c : word.toCharArray())
            sequence.append(getDigit(c));
        return sequence.toString();
    }

    /**
     * @param sequence
     * @param word     inserts the sequence into the T9dictionary if it is not there
     *                 already, updates the wordQueue with the new word or increments
     *                 the wodfrequencey if it already exists
     */
    public static void insert(String sequence, String word) {
        if (T9dictionary.containsKey(sequence)) {

            Queue<Word> wordQueue = T9dictionary.get(sequence);
            // System.out.println(wordQueue.contains(new Word(word)));
            if (wordQueue.contains(new Word(word))) {
                // System.out.println("Before adding: "+ wordQueue + " "+word);
                Word toUpdate = removeWord(wordQueue, new Word(word));
                // System.out.println("Removed Word: "+toUpdate +
                // " from : "+wordQueue);
                toUpdate.setFrequency(toUpdate.getFrequency() + 1);
                wordQueue.add(toUpdate);
                // System.out.println("After adding: "+ wordQueue + " "+word);
            } else {
                // System.out.println("Before adding: "+ wordQueue + " "+word);
                Word ne = new Word(word);
                wordQueue.add(ne);
                // System.out.println("After adding: "+ wordQueue);

            }
        } else {
            Queue<Word> wordQueue = new PriorityQueue(1,
                    new Comparator<Word>() {

                        public int compare(Word o1, Word o) {
                            if (o1.words.equals(o.words)) {
                                return 0;
                            } else if (o1.frequency < o.frequency) {
                                return 1;
                            } else {
                                return -1;
                            }
                        }

                    });
            wordQueue.add(new Word(word));
            T9dictionary.put(sequence, wordQueue);
        }
    }

    /**
     * @param wordQueue
     * @param word
     * @return Word removes the Word from the PriorityQueue and returns the
     * Word.
     */
    private static Word removeWord(Queue<Word> wordQueue, Word word) {
        for (Word eachWordIn : wordQueue) {
            if (eachWordIn.equals(word)) {
                wordQueue.remove(eachWordIn);
                return eachWordIn;
            }
        }
        // since the method is called only if the Word is present return null
        // statement never gets executed
        return null;
    }

    /**
     * @param alphabet
     * @return digit mapped to the corresponding character
     */
    public static char getDigit(char alphabet) {
        if (alphabet >= '0' && alphabet <= '9') {
            return alphabet;
        }
        switch (alphabet) {
            case 'a':
            case 'b':
            case 'c':
                return '2';
            case 'd':
            case 'e':
            case 'f':
                return '3';
            case 'g':
            case 'h':
            case 'i':
                return '4';
            case 'j':
            case 'k':
            case 'l':
                return '5';
            case 'm':
            case 'n':
            case 'o':
                return '6';
            case 'p':
            case 'q':
            case 'r':
            case 's':
                return '7';
            case 't':
            case 'u':
            case 'v':
                return '8';
            case 'w':
            case 'x':
            case 'y':
            case 'z':
                return '9';
            default:
                return '1';
        }
    }
}


class Word {
    String words;
    int frequency;

    public Word(String word) {
        this.words = word;
        this.frequency = 1;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object) Overrided equals returns
     * true if the word matches false otherwise (frequency is ignored)
     */
    @Override
    public boolean equals(Object word) {
//		System.out.println("My compare function : "+((Word) this).words + " " + ((Word) word).words);
        return this.words.equals(((Word) word).words);
    }

    @Override
    public String toString() {
//		return this.words + " " + this.frequency;
        return this.words;
    }

}


class SequenceNotFoundException extends Exception {
    public String msg;

    public SequenceNotFoundException(String sequence) {
        msg = sequence + ": corresponding word not found in Dictionary";
    }

    @Override
    public String getMessage() {
        return msg;
    }
}