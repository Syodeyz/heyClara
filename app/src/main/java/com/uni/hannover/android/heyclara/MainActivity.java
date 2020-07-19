package com.uni.hannover.android.heyclara;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
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

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.VIBRATE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private static final String LOG_TAG = MainActivity.class.getName();
    private static final String SEARCH_WORD = "SEARCH_WORD";
    private static final int PERMISSIONS_REQUEST_CODE = 5;
    private static int sensibility = 35;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        final TextView threshold = (TextView) findViewById(R.id.threshold);
        threshold.setText(String.valueOf(sensibility));
        final SeekBar seekbar = (SeekBar) findViewById(R.id.seekbar);
        seekbar.setProgress(sensibility);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold.setText(String.valueOf(progress));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // intentionally empty
            }

            public void onStopTrackingTouch(final SeekBar seekBar) {
                sensibility = seekBar.getProgress();
                //Toast.makeText(ListeningActivity.this, "1 low false alarm\n 10 many false alarms", Toast.LENGTH_LONG).show();
                Log.i(LOG_TAG, "Changing Recognition Threshold to " + sensibility);
                threshold.setText(String.valueOf(sensibility));
                speechRecognizer.removeListener(MainActivity.this);
                speechRecognizer.stop();
                speechRecognizer.shutdown();
                setupRecognizer();
            }
        });

        ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, VIBRATE}, PERMISSIONS_REQUEST_CODE);


    }


    @Override
    protected void onResume() {
        super.onResume();
        setupRecognizer();
        //this code is added to see if the ..performing stop of activity that is not resumed...bug
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


    @Override
    public void onBeginningOfSpeech() {
        Log.d(LOG_TAG, "Beginning Of Speech");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("~ ~ ~");
        }
        Toast.makeText(this, "Please say \"Hey Clara\" to wake it up.",
                Toast.LENGTH_LONG).show();

    }



    @Override
    public void onEndOfSpeech() {
        Log.d(LOG_TAG, "End Of Speech");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("");
        }
    }

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
            Log.d(LOG_TAG, "PocketSphinx Recognizer was shutdown");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onError(final Exception e) {
        Log.e(LOG_TAG, "on Error: " + e);
    }

    @Override
    public void onTimeout() {
        Log.d(LOG_TAG, "on Timeout");
    }

}