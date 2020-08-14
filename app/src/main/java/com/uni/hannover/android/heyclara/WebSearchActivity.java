package com.uni.hannover.android.heyclara;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import github.com.vikramezhil.dks.speech.Dks;
import github.com.vikramezhil.dks.speech.DksListener;

/**
 * The WebSearch activity is responsible for recording the speech.
 * It uses DroidSpeech2.0 for continuous recognition.
 * It uses text-to-speech for greetings.
 * The recorded speech is then analysed and the content of the record will then determine the the intent
 * to follow.
 *
 * @author Amrit Gaire
 */

public class WebSearchActivity extends AppCompatActivity implements TextToSpeech.OnInitListener{
    private static final String TAG = WebSearchActivity.class.getName();
    public static BufferedWriter out;
    private Dks dks;
    private TextToSpeech tts;
    private TextView textView;
    private ProgressBar progressBar;
    private String greet = "Yes please!, How can I help you?";
    private String preview_english = "Listening . . .";
    private String preview_deutsch = "Bitte stellen Sie Ihre Anfrage ein.\n \t \t \t Ich höre zu.... ";
    private Button restart;
    private Button back;
    private Button showHelp;
    private String lang = "en";
    private boolean clicked = false;
    private boolean first_attempt = false;
    private FileOutputStream fstream;
    private File myFile;
    private boolean helpClick = false;


    /**
     * The layout is set and Text to Speech is initialized. 
     * The droid speech listener is also initialized here.
     * Further, the preferences like language setting is stored.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_search);

        initializeTts();
        textView = findViewById(R.id.textView);
        showHelp = findViewById(R.id.showHelp);

        try {
            createFileOnDevice();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //get preferred default language from user.
        SharedPreferences prefs = getSharedPreferences("MyPreference", MODE_PRIVATE);
        int spinnerPosition = prefs.getInt("default_lang_pos", -1); // the default value.

        Spinner dropdown = findViewById(R.id.spinner1);

        //create a list of items for the spinner.
        String[] items = new String[]{"en", "de"};
        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        //There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        dropdown.setAdapter(adapter);
        //set the dropdown value to previously selected one.
        dropdown.setSelection(spinnerPosition);
        if(spinnerPosition == 1){
            lang = "de";
        }else{
            lang = "en";
        }
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                lang =  items[position];
                //set the default language as preference for future use
                SharedPreferences.Editor editor = getSharedPreferences("MyPreference", MODE_PRIVATE).edit();
                editor.putInt("default_lang_pos", position);
                editor.commit();
                if(lang.equals("de")){
                    lang = "de-DE";
                    dks.setCurrentSpeechLanguage("de-DE");
                    textView.setText(preview_deutsch);
                    back.setText("Neustart");
                    showHelp.setText("Hinweis");
                }else{
                    lang = "en-US";
                    textView.setText(preview_english);
                    back.setText("Restart");
                    showHelp.setText("Hints");
                    dks.setCurrentSpeechLanguage("en-US");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                lang = "en";
            }
        });
        
        
        restart = findViewById(R.id.start);
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRestart();
                clicked = true;
            }
        });


        //help text provide information to user on how to use the web search and Spotify controls
        TextView helpText = findViewById(R.id.helpText);
        String text_english = "Say any query you want to search and it will search on web.\n" +
                "Include  \"Open Spotify\" on your query to launch Spotify on background.\n" +
                "eg. \"can you please open Spotify\" and \"Open Spotify\" both launches Spotify on bkg.\n " +
                "After Spotify is launched: \n" +
                "say \"Stop\",\"Next\", \"Resume\", \"Previous\", \"Pause\"\n" +
                " for their obvious function. You can lock your screen and still control playback.BUt don't close the app\n";
        String text_deutsch = "Sag etwas um ins Web zu suchen.\n" +
                "Includiere \"Öffne Spotify\", oder \"Spotify Öffnen\" in dem Anfrage um Spotify im hintergrund laufen zu lassen." +
                "\nNachdem Spotify im hintergrund läuft:\n" +
                "Sag \"Stop\",\"Next\", \"Resume\", \"Previous\", \"Pause\"\n für simple Kontrolle.\n" +
                "Bildschrim kann geschlossen werden aber nicht den app";

        if(lang.equals("de")){
            helpText.setText(text_deutsch);

        }else{
            helpText.setText(text_english);

        }

        //to toggle the hint button. 
        showHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(helpClick){
                    helpText.setVisibility(View.GONE);
                    showHelp.setBackgroundResource(android.R.drawable.btn_default);
                    helpClick = false;

                }else{
                    helpText.setVisibility(View.VISIBLE);
                    showHelp.setBackgroundColor(Color.GRAY);
                    helpClick = true;
                }
            }
        });

        initializeDks(lang);
        back = findViewById(R.id.back);
        back.setOnClickListener(view -> {
            recreate();
        });
    }


    /**
     * This method calls parent method on this state.
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "On start");
        //dks.setOneStepResultVerify(true);

    }

    /**
     * This method is responsible for starting the recording process.
     */
    @Override
    protected void onResume(){
        super.onResume();
        Log.i(TAG, "on resume");
        //textView.setText(preview);
        dks.startSpeechRecognition();
    }





    /**
     * This method checks the query for the different possible intent.
     * Then calls the supportive functions to launch the intent accordingly.
     * It is also responsible for presenting the regress meter and cancel option
     * @param query the content of the query in String
     * @throws InterruptedException
     */
    public void defineAction(String query) throws InterruptedException {
        dks.closeSpeechOperations();
        progressBar = findViewById(R.id.pbar);
        progressBar.setMax(7);
        int progress = 0;

        /**
         * This method is responsible for counting the duration presented to cancel the query.
         */
        new CountDownTimer(5000, 1000) {

            @RequiresApi(api = Build.VERSION_CODES.N)
            @SuppressLint("SetTextI18n")
            public void onTick(long millisUntilFinished) {
                progressBar.setVisibility(View.VISIBLE);
                restart.setVisibility(View.VISIBLE);
                if(dks.getCurrentSpeechLanguage().equals("de-DE")){
                    restart.setText("Abfrage Abbrechen");
                }else{
                    restart.setText("Cancel Query");
                }

                progressBar.setProgress((int) (progress + millisUntilFinished/1000), true);
                if(clicked){

                    progressBar.setVisibility(View.GONE);
                    restart.setVisibility(View.GONE);
                    return;
                }
            }

            /**
             * The method will disable the cancel option and sets it invisible after the regression finishes.
             * It will then call the helper method to launch the query if not canceled.
             * It will also write to file if the query is canceled for analysis purpose.
             */
            public void onFinish() {
                progressBar.setVisibility(View.GONE);
                restart.setVisibility(View.GONE);
                if(!clicked){
                    try {
                        decideIntent(query);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{
                    //for analysis, should not be in production
                    try {
                        writeToFile(query, "Cancled ");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                clicked = false;
            }
        }.start();


    }

    /**
     * This method is responsible for deciding the intent according the query passed.
     * It checks for different possibilities and decides if the web search is done or Spotify should
     * be launched.
     * The recording is then closed as soon as the other intent is called.
     * @param query The recorded speech is passed as string value for analysis.
     * @throws InterruptedException
     */

    public void decideIntent(String query) throws InterruptedException {

        if(query.contains("Spotify") || query.contains("Spotify") || query.contains("Spotify")) {
            dks.closeSpeechOperations();
            startActivity(new Intent(this, LaunchSpotify.class));

        }else{

            try {
                dks.closeSpeechOperations();
                Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                intent.putExtra(SearchManager.QUERY, query);
                startActivity(intent);
            } catch (Exception e) {
                throw new InterruptedException();
            }
        }
    }

    /**
     * This method calls its parent method on pause state.
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "on Pause");
    }

    /**
     * This method is responsible for stopping the recognition as soon as the activity stops.
     */
    @Override
    protected void onStop() {
        super.onStop();
        dks.closeSpeechOperations();
        Log.i(TAG, "on Stop");
    }


    /**
     * This methods will also stops the recognition process if the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.shutdown();
        dks.closeSpeechOperations();
        Log.i(TAG, "on destroy");
    }

    /**
     * This method checks if the droid speech recognition is initialized and set the language preferences.
     */
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "on Restart");
        if(dks != null){
            if(Objects.equals(dks.getCurrentSpeechLanguage(), "de-DE")){
                textView.setText(preview_deutsch);
            }else{
                textView.setText(preview_english);
            }
        }
        clicked = false;
    }

    @Override
    public void onInit(int status) {
        //just  overrides
    }

    /**
     * initializes dks continuous recognition and delivers the recorded speech to perform action
     * @param lang takes language as parameter to set default language
     */
    public void initializeDks(String lang){
        //initializing dks for recording purpose
        dks = new Dks(getApplication(), getSupportFragmentManager(), new DksListener() {

            /**
             * This method will show the ~ ~ ~ in the support bar and gives feedback that
             * the recognition has started.
             * It will show the live speech recorded on the screen.
             * @param liveSpeechResult the recorded speech in text form
             */
            @Override
            public void onDksLiveSpeechResult(@NotNull String liveSpeechResult) {
                System.out.println("dks is listening");
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("~ ~ ~");
                }
                Log.d(getPackageName(), "Speech result - " + liveSpeechResult);
                String livePreview = "" + liveSpeechResult;
                //to let the greet message display the first time
                if(first_attempt){
                    textView.setText(livePreview);
                }
            }

            /**
             * This method collects the final speech recorded and passes to the defineAction method
             * for further processing
             * @param speechResult
             */
            @Override
            public void onDksFinalSpeechResult(@NotNull String speechResult) {
                Log.d(getPackageName(), "Final Speech result - " + speechResult);
                dks.closeSpeechOperations();
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("");
                }
                first_attempt = true;
                String query = speechResult;

                dks.closeSpeechOperations();
                //to avoid listening to the own greetings
                if (!speechResult.equals("") && !speechResult.contains("can I help") && !speechResult.contains("wie kann ich Ihnen") && !speechResult.contains("yes please")) {
                    textView.setText(speechResult);
                    try {
                        writeToFile(speechResult, "Logged at ");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (speechResult.equals("thank you")) {
                        tts.speak("Welcome, see you soon", TextToSpeech.QUEUE_FLUSH, null);
                        while(tts.isSpeaking()){
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        try {
                            defineAction(query);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }


            @Override
            public void onDksLiveSpeechFrequency(float frequency) {
                //frequency if needed
            }

            /**
             * This method gives a wide range of values for language option.
             * The default value en-us is set if other options are not givne.
             * @param defaultLanguage the language set as default
             * @param supportedLanguages the language options
             */
            @Override
            public void onDksLanguagesAvailable(@org.jetbrains.annotations.Nullable String defaultLanguage, @org.jetbrains.annotations.Nullable ArrayList<String> supportedLanguages) {
                Log.d(getPackageName(), "defaultLanguage - " + defaultLanguage);
                Log.d(getPackageName(), "supportedLanguages - " + supportedLanguages);

                if(lang.equals("de") && supportedLanguages.contains("de-DE")){
                    dks.setCurrentSpeechLanguage("de-DE");

                }else{
                    if (supportedLanguages != null && supportedLanguages.contains("en-US")) {
                        // Setting the speech recognition language to english US if found
                        dks.setCurrentSpeechLanguage("en-US");
                    }
                }

                //System.out.println("The language is : " + dks.getCurrentSpeechLanguage());
            }

            /**
             * Just to get notified if language options fails.
             * @param errMsg
             */
            @Override
            public void onDksSpeechError(@NotNull String errMsg) {
                Toast.makeText(getApplication(), errMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * This method initializes the text to speech and greets either in german or in english.
     */
    public void initializeTts(){

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int ttsLang = tts.setLanguage(Locale.US);
                    if(dks != null && dks.getCurrentSpeechLanguage().equals("de-DE")){
                        ttsLang = tts.setLanguage(Locale.GERMAN);
                        tts.speak("Ja bitte, wie kann ich Ihnen hilfen?", TextToSpeech.QUEUE_FLUSH, null);
                    }else{
                        int speechStatus = tts.speak("Yes Please, how can I help you?", TextToSpeech.QUEUE_FLUSH, null);
                        System.out.println("I cant speak, I don't know why?????");
                        if (speechStatus == TextToSpeech.ERROR) {
                            Log.e("TTS", "Error in converting Text to Speech!");
                        }
                    }
                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");

                }
                else {
                    Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
           // System.out.println("**********************read only availabe");
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
     * writes all the queries performed and cancelled in a file log.txt created above
     */
    public void writeToFile(String message, String tag) throws IOException {
        fstream = new FileOutputStream(myFile, true);
        Date date = new Date();
        String log_message = tag + date.toString() + " : " + message + "\n";
        fstream.write(log_message.getBytes());
        fstream.close();
    }

}