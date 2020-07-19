package com.uni.hannover.android.heyclara;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;
import github.com.vikramezhil.dks.speech.Dks;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class LaunchSpotify extends AppCompatActivity implements RecognitionListener {
    private Dks dks;
    private SpotifyAppRemote mSpotifyAppRemote;
    private TextView commands;
    private static final String REDIRECT_URI = "https://heyclara//callback";
    private static final String CLIENT_ID = "49dfa7d0df994d0792a09280d26d3ff2";
    private static final int PERMISSIONS_REQUEST_CODE = 5;
    private static final String PLAYBACK_CONTROL = "PLAYBACK_CONTROL";
    private SpeechRecognizer speechRecognizer;
    private static int sensibility = 90;
    private File myFile;

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_spotify);
        ActivityCompat.requestPermissions(LaunchSpotify.this, new String[]{RECORD_AUDIO, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);

        if(!SpotifyAppRemote.isSpotifyInstalled(getApplicationContext())){
            getInstalled();
        }else{
            try {
                createFileOnDevice();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        runRecognizerSetup();
        moveTaskToBack(true);


        //moveTaskToBack(true);
    }

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


    private void connected() {

        // Play a playlist
            //mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL");
            mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DX8mBRYewE6or");
            mSpotifyAppRemote.getPlayerApi().setShuffle(true);
            //mSpotifyAppRemote.getPlayerApi().play("spotify:playlist:37i9dQZF1DWWmaszSfZpom");
            subPlayerState();


    }

    public void subPlayerState(){

        // Subscribe to PlayerState
        mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(playerState -> {
                    final Track track = playerState.track;
                    if (track != null) {
                        Log.d("Spotify", track.name + " by " + track.artist.name);
                        //commands = findViewById(R.id.command);
                        //commands.setText(track.name + " by " + track.artist.name);
                    }
                });

    }


    @SuppressLint("StaticFieldLeak")
    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
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


    private void setup() {

        try {
            Assets assets = new Assets(LaunchSpotify.this);
            File assetDir = assets.syncAssets();


            speechRecognizer = SpeechRecognizerSetup.defaultSetup()
                    .setAcousticModel(new File(assetDir, "models/en-us-ptm"))
                    .setDictionary(new File(assetDir, "models/lm/words.dic"))
                    .setKeywordThreshold(Float.valueOf("1.e-" + 2 * sensibility))
                    .getRecognizer();

            File controlGrammar = new File(assetDir,"models/lm/control.list");

            speechRecognizer.addListener(this);
            speechRecognizer.addKeywordSearch(PLAYBACK_CONTROL, controlGrammar);
            speechRecognizer.startListening(PLAYBACK_CONTROL);
            Log.d("Launch Spotify", "... listening");
        } catch (IOException e) {
            Log.e("Launch Spotify", e.toString());
        }
    }


    public void controlPlayback(String text) throws IOException {
        writeToFile(text, "Control_command: ");
        speechRecognizer.cancel();
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

    @Override
    protected void onStart() {
        super.onStart();
        Log.i("Launch Spotify", "On start");
        //dks.injectProgressView(R.layout.layout_pv_inject);
        setupSpotify();

    }


    @Override
    protected void onResume(){
        super.onResume();
        Log.i("Launch Spotify", "on resume");

    }



    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onStop() {
        super.onStop();
        //dks.closeSpeechOperations();
        Log.i("Launch Spotify", "on Stop");



    }


    @Override
    public void onBeginningOfSpeech() {
        Log.d("Launch Spotify", "Beginning Of Speech");
    }

    @Override
    public void onEndOfSpeech() {
        Log.d("Launch Spotify", "End Of Speech");
    }

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

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            Log.d("Launch Spotify", "on Result: " + hypothesis.getHypstr() + " : " + hypothesis.getBestScore());

        }
    }
    /**
     * Stop the recognizer.
     * Since cancel() does trigger an onResult() call,
     * we cancel the recognizer rather then stopping it.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (speechRecognizer != null) {
            speechRecognizer.removeListener(this);
            speechRecognizer.cancel();
            speechRecognizer.shutdown();
            Log.d("Launch Spotify", "PocketSphinx Recognizer was shutdown");
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.removeListener(this);
            speechRecognizer.cancel();
            speechRecognizer.shutdown();
            Log.d("Launch Spotify", "PocketSphinx Recognizer was shutdown");
        }
        mSpotifyAppRemote.getPlayerApi().pause();

        try {
            writeToFile("On destroy", "playback_closed ");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onError(final Exception e) {
        Log.e("Launch Spotify", "on Error: " + e);
        try {
            writeToFile("error", "playback_closed ");
        } catch (IOException er) {
            er.printStackTrace();
        }
    }

    @Override
    public void onTimeout() {
        Log.d("Launch Spotify", "on Timeout");
        try {
            writeToFile("timeout", "playback_closed ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
            System.out.println("***********************read and write availabe");
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){
            // Only Read operation available
            Available= true;
            Readable= true;
            System.out.println("**********************read only availabe");
        } else {
            // SD card not mounted
            Available = false;
            System.out.println("********************not availabe");
        }



        String FILENAME = "log.txt";
        File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        myFile = new File(folder, FILENAME);


    }

    public void writeToFile(String message, String tag) throws IOException {
        FileOutputStream fstream = new FileOutputStream(myFile, true);
        Date date = new Date();
        String log_message = tag + date.toString() + " : " + message + "\n";
        fstream.write(log_message.getBytes());
        fstream.close();
        System.out.println("***************I came here*********");
    }



}