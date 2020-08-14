package com.uni.hannover.android.heyclara;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.Manifest.permission.BROADCAST_STICKY;
import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.VIBRATE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * This class is a launcher so it will be the first screen if the app runs.
 * It is responsible for setting layout and listening for the wake word, detecting it and launching
 * websearch acitvity if the detection is successful.
 *
 * @author Amrit Gaire
 */

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private static final String LOG_TAG = MainActivity.class.getName();
    private static final String SEARCH_WORD = "SEARCH_WORD";
    private static final int PERMISSIONS_REQUEST_CODE = 5;
    private static int sensibility = 35;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;
    AudioManager audioManager;
    private TextView wakeWord;
    private Button sense;
    String default_wake_word = " Say \"Hey Clara\"";
    private boolean helpClick = false;
    private TextView sense_text;

    /**
     * The layout of the welcome screen is set, the sensibility setting is tracked and all needed
     * permissions are called in onCreate method.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wakeWord = findViewById(R.id.wake_word);
        wakeWord.setText(default_wake_word);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);

        sense_text = findViewById(R.id.sense_preview);
        String hint = "********Wake Sensibility**********\n" +
                      "* 1 will avoid many false alarms *\n" +
                      "* but misses right ones too \t\t\t\t *\n" +
                      "* 100 will allow many correct\t\t\t*\n" +
                      "* as well as false alarms.\t\t\t\t\t *\n" +
                      "*********************************";
        sense_text.setText(hint);
        sense = findViewById(R.id.btnSense);
        sense.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(helpClick){
                    sense_text.setVisibility(View.GONE);
                    sense.setBackgroundResource(android.R.drawable.btn_default);
                    helpClick = false;

                }else{
                    sense_text.setVisibility(View.VISIBLE);
                    sense.setBackgroundColor(Color.GRAY);
                    helpClick = true;
                }
            }
        });

        final TextView threshold = (TextView) findViewById(R.id.threshold);
        threshold.setText(String.valueOf(sensibility));
        final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
        seekbar.setProgress(sensibility);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold.setText(String.valueOf(progress));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // keep it empty
            }


            public void onStopTrackingTouch(final SeekBar seekBar) {
                sensibility = seekBar.getProgress();
                Log.i(LOG_TAG, "Changing Recognition Threshold to " + sensibility);
                threshold.setText(String.valueOf(sensibility));
                speechRecognizer.removeListener(MainActivity.this);
                speechRecognizer.stop();
                speechRecognizer.shutdown();
                setupRecognizer();
            }
        });

        //to register the state of the bluetooth connectivity
        registerReceiver(connectionReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, BROADCAST_STICKY, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, VIBRATE}, PERMISSIONS_REQUEST_CODE);


    }

    /**
     * This method cheks the connection sate of bluetooth device and closes any scope for bluetooth
     * if its not connected to it.
     * If connected it will call headset method for further configuration.
     */
    BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1);
            Log.d("Launch Spotify", "Bluetooth connection state " + state);
            checkHeadset();
            //System.out.println("Headset state ***" + state);
            if(state == 0){
                audioManager.stopBluetoothSco();
                audioManager.setMicrophoneMute(false);
            }
        }
    };

    /**
     * The method checks if bluetooth connection is active and if active then creates a new recorder for
     * bluetooth audio service, muting the phones microphone in the process.
     */

    @SuppressWarnings("deprecation")
    private void checkHeadset() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED) {

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    audioManager.startBluetoothSco();
                    audioManager.setMicrophoneMute(true);
                }
            }, 1000);
        }
    }

    /**
     * This method sets up the wakeWord recognizer as soon as the app is in the resume state.
     * The listening process for wake word recognition starts in this state.
     */
    @Override
    protected void onResume() {
        super.onResume();
        setupRecognizer();
    }

    /**
     * This method checks for permission to record the audio.
     * A toast message will appear if permission is denied.
     * @param requestCode the value set for the request
     * @param permissions the array of values set for the permission
     * @param grantResults  the array of values to grant results
     */
    @Override
    public void onRequestPermissionsResult(
            final int requestCode,
            @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (0 < grantResults.length && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio recording permissions denied.", Toast.LENGTH_LONG).show();
                this.finish();
            }
        }
    }

    /**
     * This method is to setup the cmusphinx-pocket-recognizer.
     * It checks the models and dictionary in assets file for the correctness.
     * It also set the sensibility of recognition.
     * After the setup it will listen for key word and decodes the speech recorded to compare if the
     * recorded speech is a wake word or not.
     * Do not forget to create a new md5 hash after editing the dictionary in assets.
     */
    public void setupRecognizer(){
        try {
            Assets assets = new Assets(MainActivity.this);
            File assetDir = assets.syncAssets();
            speechRecognizer = SpeechRecognizerSetup.defaultSetup()
                    .setAcousticModel(new File(assetDir, "models/en-us-ptm"))
                    .setDictionary(new File(assetDir, "models/lm/words.dic"))
                    .setKeywordThreshold(Float.parseFloat("1.e-" + 2 * sensibility))
                    .getRecognizer();
            speechRecognizer.addKeyphraseSearch(SEARCH_WORD, getString(R.string.trigger));
            speechRecognizer.addListener(this);
            speechRecognizer.startListening(SEARCH_WORD);
            Log.d(LOG_TAG, "... listening");
        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
        }
    }

    /**
     * The support action bar will show then . . . as it starts to listen to the wake words.
     */
    @Override
    public void onBeginningOfSpeech() {
        Log.d(LOG_TAG, "Beginning Of Speech");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(". . .");
        }


    }


    /**
     * This method signifies the end of speech and the ... dots are toggled of as it happens.
     */
    @Override
    public void onEndOfSpeech() {
        Log.d(LOG_TAG, "End Of Speech");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("");
        }
    }

    /**
     * This method will check whether the speech recorded is the wake word.It does its calculation
     * (see pocketsphinx-5prealpharelease).
     * If the hypothesis is correct, the phone vibrates to indicate the wake word is detected.
     * The method then launches the websearchactivity after the detection of wake word.
     * @param hypothesis the pre-result value of spoken words
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            final String text = hypothesis.getHypstr();
            Log.d(LOG_TAG, "on partial: " + text);
            if (text.equals(getString(R.string.trigger))) {
                vibrator.vibrate(100);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("");
                }
                startActivity(new Intent(this, WebSearchActivity.class));

            }
        }

    }

    /**
     * This method shows then the final result of the recorded speech.
     * @param hypothesis the final version of the recorded value
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            Log.d(LOG_TAG, "on Result: " + hypothesis.getHypstr() + " : " + hypothesis.getBestScore());
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle("");
            }
        }

    }

    /**
     * This method serves for cancelling the recognizer if the activity is paused.
     * The recognizer will then be shut-down and wont be listening anymore.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.removeListener(this);
            speechRecognizer.cancel();
            speechRecognizer.shutdown();
            Log.d(LOG_TAG, "PocketSphinx Recognizer was shutdown");
        }
    }

    /**
     * This methods just calls it parent method if the activity is stopped.
     */
    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * This mehtod calls the parent method on destroy thus anythig unsaved will be gone.
     * Further the method also unmute the microphone of the phone.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioManager.setMicrophoneMute(false);
    }

    /**
     * This method is responisible for logging in case of error.
     * @param e
     */
    @Override
    public void onError(final Exception e) {
        Log.e(LOG_TAG, "on Error: " + e);
    }

    /**
     * This method will lof timeout if it happens.
     */
    @Override
    public void onTimeout() {
        Log.d(LOG_TAG, "on Timeout");
    }

}