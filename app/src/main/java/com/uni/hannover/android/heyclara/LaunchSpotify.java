package com.uni.hannover.android.heyclara;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.Track;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Random;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

/**
 * The LaunchSpotify is responisble for launching the Spotify app in the background.
 * It connects with Spotify remote API and gives playback commands.
 * It records the commands given using pocketsphinx recognizer.
 * The recorded commands are then analyzed and the actions are performed accordingly.
 * @author Amrit Gaire
 */
public class LaunchSpotify extends AppCompatActivity implements RecognitionListener {
    private static final long HEADSET_ENABLE_TIMEOUT = 2000;
    private SpotifyAppRemote mSpotifyAppRemote;
    private TextView commands;
    private static final String REDIRECT_URI = "https://heyclara//callback";
    private static final String CLIENT_ID = "49dfa7d0df994d0792a09280d26d3ff2";
    private static final int PERMISSIONS_REQUEST_CODE = 5;
    private static final String PLAYBACK_CONTROL = "PLAYBACK_CONTROL";
    private SpeechRecognizer speechRecognizer;
    private static int sensibility = 100;
    private File myFile;
    private AudioManager audioManager;


    /**
     * This method checks for the installation of Spotify in the running device.
     * If Spotify is not installed will provide the option to download the app from playstore.
     * It also launches the app on the background.
     * @param savedInstanceState saved instances
     */
    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_spotify);
        ActivityCompat.requestPermissions(LaunchSpotify.this, new String[]{RECORD_AUDIO,  WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        //registerReceiver(connectionReceiver, new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));
        if (!SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())) {
            getInstalled();
        } else {
            try {
                createFileOnDevice();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //runs the app in the background
        moveTaskToBack(true);

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
                @RequiresApi(api = Build.VERSION_CODES.M)
                public void run() {

                    audioManager.startBluetoothSco();
                    audioManager.setMicrophoneMute(true);
                    System.out.println("!!!!! Mic is mute  " + audioManager.isMicrophoneMute());
                    //System.out.println();
                    //runRecognizerSetup();
                }
            }, HEADSET_ENABLE_TIMEOUT);
        }
    }

    /**
     * This method is responsible for connecting to Spotify Remote API
     */
    public void setupSpotify(){

        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        ConnectionParams connectionParams =
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(true)
                        .build();

        SpotifyAppRemote.connect(getApplicationContext(), connectionParams,
                new Connector.ConnectionListener() {

                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        Log.d("********SpotifyActivity", "Connected! Yay!");

                        // Now you can start interacting with App Remote
                        connected();

                    }

                    public void onFailure(Throwable throwable) {
                        Log.e("********SpotifyActivity", throwable.getMessage(), throwable);
                        // Something went wrong when attempting to connect! Handle errors here
                    }
                });
    }

    /**
     * This method is responsible for checking Spotify installation and if not installed helps user
     * to download it from Play Store
     */
    public void getInstalled(){
        final String appPackageName = "com.spotify.music";
        final String referrer = "adjust_campaign=com.uni.hannover.android.heyclara&adjust_tracker=ndjczk&utm_source=adjust_preinstall";

        try {
            Uri uri = Uri.parse("market://details")
                    .buildUpon()
                    .appendQueryParameter("id", appPackageName)
                    .appendQueryParameter("referrer", referrer)
                    .build();
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (android.content.ActivityNotFoundException ignored) {
            Uri uri = Uri.parse("https://play.google.com/store/apps/details")
                    .buildUpon()
                    .appendQueryParameter("id", appPackageName)
                    .appendQueryParameter("referrer", referrer)
                    .build();
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }

    }

    /**
     * This method is called after the connection to sSpotify Remote API is successful.
     * It will then generate a random number to choose a playlist from the given ones.
     * It also set the shuffle option to true as default.
     */
    private void connected() {

        // Play a playlist
        Random rand = new Random();
        int playList = rand.nextInt(3);
        System.out.println("Random playlist " + playList);
        if(playList == 0){
            mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL");
        }else if(playList == 1){
            mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DX8mBRYewE6or");
        }else{
            mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DWWmaszSfZpom");
        }
        mSpotifyAppRemote.getPlayerApi().setShuffle(true);
        subPlayerState();


    }

    /**
     * This method is responsible for collecting metada of the tracks being played.
     */
    public void subPlayerState(){

        // Subscribe to PlayerState
        mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(playerState -> {
                    final Track track = playerState.track;
                    if (track != null) {
                        Log.d("Spotify", track.name + " by " + track.artist.name);
                        commands = findViewById(R.id.command);
                        commands.setText(track.name + " by " + track.artist.name);
                    }
                });

    }

    /**
     * This method is responsible for initializing the cmusphinx recognizer.
     * The initialization costs time so the process is executed in async task.
     */
    @SuppressLint("StaticFieldLeak")
    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {

            /**
             * This method call the actual setup of pocketsphinx recognizer.
             * @param params parameter for launching in background
             * @return exceptions if thrown
             */
            @Override
            protected Exception doInBackground(Void... params) {

                setup();
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    System.out.println(result.getMessage());
                }
            }
        }.execute();
    }

    /**
     * This method is for setting up the recognizer.
     * It checks the files in assets for correctness.
     * Remember to create a md5 hash whenever the dictionary is edited for adding or removing controls
     */
    private void setup() {

        try {
            Assets assets = new Assets(LaunchSpotify.this);
            File assetDir = assets.syncAssets();


            speechRecognizer = SpeechRecognizerSetup.defaultSetup()
                    .setAcousticModel(new File(assetDir, "models/en-us-ptm"))
                    .setDictionary(new File(assetDir, "models/lm/words.dic"))
                    .setKeywordThreshold(Float.valueOf("1.e-" + 2 * sensibility))
                    //.setRawLogDir(assetDir)
                    .getRecognizer();

            File controlGrammar = new File(assetDir,"models/lm/control.list");


            AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            // to be informed for error to debug.
            while (am.getMode() == -38){
                am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
                //System.out.println("State of Mic ::::::::::::::::::::: " + am.getMode());
            }
            am.getMode();
            //System.out.println("State of Mic outside ::::::::::::::::::::: " + am.getMode());

            speechRecognizer.addListener(this);
            speechRecognizer.addKeywordSearch(PLAYBACK_CONTROL, controlGrammar);
            speechRecognizer.startListening(PLAYBACK_CONTROL);
            Log.d("Launch Spotify", "... listening");
        } catch (IOException e) {
            Log.e("Launch Spotify", e.toString());
        }
    }

    /**
     * This methods controls the action that should be performed after the recognition of commands.
     * It sends the command to Spotify Remote API
     * @param text the control command given.
     * @throws IOException
     */
    public void controlPlayback(String text) throws IOException {
        writeToFile(text, "Control_command: ");
        speechRecognizer.cancel();
        if(mSpotifyAppRemote != null){
            if (text.contains("next")) {
                mSpotifyAppRemote.getPlayerApi().skipNext();
            }else if(text.contains("previous")){
                mSpotifyAppRemote.getPlayerApi().skipPrevious();
                mSpotifyAppRemote.getPlayerApi().skipPrevious();
                mSpotifyAppRemote.getPlayerApi().resume();
            }else if(text.contains("pause")){
                mSpotifyAppRemote.getPlayerApi().pause();
            }else if(text.contains("resume")){
                mSpotifyAppRemote.getPlayerApi().resume();
            }else if(text.contains("stop")){
                mSpotifyAppRemote.getPlayerApi().pause();
            }
        }


    }

    /**
     * This method is responible for calling the setting up Spotify on start.
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.i("Launch Spotify", "On start");
        setupSpotify();
    }

    /**
     * This method is responsible for setting up the recognizer whenever the activity is resumed.
     */
    @Override
    protected void onResume(){
        super.onResume();
        Log.i("Launch Spotify", "on resume");
        runRecognizerSetup();
    }


    /**
     * Calls the parent method on stop.
     */
    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onStop() {
        super.onStop();
        Log.i("Launch Spotify", "on Stop");



    }

    /**
     * This method writes the log whenever the recording begins
     */
    @Override
    public void onBeginningOfSpeech() {
        Log.d("Launch Spotify", "Beginning Of Speech");
    }

    /**
     * This method writes the lof at the end of the recognition
     */
    @Override
    public void onEndOfSpeech() {
        Log.d("Launch Spotify", "End Of Speech");
    }

    /**
     * This method is responsible for presenting the pre-result.
     * It passes the commands if recognized from contrl_Grammer to execute.
     * @param hypothesis the speech recorded before final result.
     */
    @Override
    public void onPartialResult(final Hypothesis hypothesis) {
        if (hypothesis != null) {
            final String text = hypothesis.getHypstr();
            try {
                controlPlayback(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
            speechRecognizer.startListening(PLAYBACK_CONTROL);
            Log.d("LaunchSpotify", "on partial: " + text);

        }
    }

    /**
     * This method present the final result recorded.
     * @param hypothesis the final recorded speech
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            Log.d("Launch Spotify", "on Result: " + hypothesis.getHypstr() + " : " + hypothesis.getBestScore());

        }
    }


    /**
     * This method cancels the recognition process if the activity is paused.
     * After that no recognition is done.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.removeListener(this);
            speechRecognizer.cancel();
            speechRecognizer.shutdown();
            Log.d("Launch Spotify on Pause", "PocketSphinx Recognizer was shutdown");
        }

    }

    /**
     * This method stops the connection to SPotify Remote API if the activity is destroyed.
     * It sets the microphone of phone free if bluetooth was connected.
     * It also shut downs the recognition process.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mSpotifyAppRemote != null){
            mSpotifyAppRemote.getPlayerApi().pause();
            SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        }

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.stopBluetoothSco();
        audioManager.setMicrophoneMute(false);

        if (speechRecognizer != null) {
            speechRecognizer.removeListener(this);
            speechRecognizer.cancel();
            speechRecognizer.shutdown();
            Log.d("LaunchSpotify Ondestroy", "PocketSphinx Recognizer was shutdown");
        }


        try {
            writeToFile("On destroy", "playback_closed ");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * To write log of any error present
     * @param e
     */
    @Override
    public void onError(final Exception e) {
        Log.e("Launch Spotify", "on Error: " + e);

    }

    /**
     * To write log if timeout happens.
     */
    @Override
    public void onTimeout() {
        Log.d("Launch Spotify", "on Timeout");
        try {
            writeToFile("timeout", "playback_closed ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * to collect log_data for analysing purpose,
     * should not be on production
     * checks for storage permissions
     */
    private void createFileOnDevice() throws IOException {
        /*
         * Function to initially create the log file and it also writes the time of creation to file.
         */
        boolean Available= false;
        boolean Readable= false;
        String state = Environment.getExternalStorageState();
        if(Environment.MEDIA_MOUNTED.equals(state)){
            // Both Read and write operations available
            Available= true;
            //System.out.println("***********************read and write availabe");
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){
            // Only Read operation available
            Available= true;
            Readable= true;
            //System.out.println("**********************read only availabe");
        } else {
            // SD card not mounted
            Available = false;
            //System.out.println("********************not availabe");
        }



        String FILENAME = "log.txt";
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        myFile = new File(folder, FILENAME);


    }

    /**
     * to collect log_data for analysing purpose,
     * should not be on production
     * appends the commands performed  in a file log.txt created above
     */
    public void writeToFile(String message, String tag) throws IOException {
        FileOutputStream fstream = new FileOutputStream(myFile, true);
        Date date = new Date();
        String log_message = tag + date.toString() + " : " + message + "\n";
        fstream.write(log_message.getBytes());
        fstream.close();
    }
}