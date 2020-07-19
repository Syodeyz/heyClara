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

public class WebSearchActivity extends AppCompatActivity implements TextToSpeech.OnInitListener{


    private static final String TAG = WebSearchActivity.class.getName();
    public static BufferedWriter out;
    private Dks dks;
    private TextToSpeech tts;
    private TextView textView;
    private ProgressBar progressBar;
    private String greet = "Yes please!, How can I help you?";
    private String preview_english = "Please speak your query.\n \t \t \t I'm listening...";
    private String preview_deutsch = "Bitte stellen Sie Ihre Anfrage ein.\n \t \t \t Ich höre zu.... ";
    private Button start;
    private Button back;
    private Button showHelp;
    private String lang = "en";
    private boolean clicked = false;
    private boolean first_attempt = false;
    private FileOutputStream fstream;
    private File myFile;
    private boolean helpClick = false;

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

        start = findViewById(R.id.start);
        start.setOnClickListener(new View.OnClickListener() {
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


    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "On start");
        //dks.injectProgressView(R.layout.layout_pv_inject);
        dks.setOneStepResultVerify(true);

    }


    @Override
    protected void onResume(){
        super.onResume();
        Log.i(TAG, "on resume");
        //textView.setText(preview);
        dks.startSpeechRecognition();
    }

    /*
     * activity logic implementation
     * to diverse different activity according to the user query
     *
     */
    public void defineActivity(String query) throws InterruptedException {
        dks.closeSpeechOperations();
        progressBar = findViewById(R.id.pbar);
        progressBar.setMax(7);
        int progress = 0;

        new CountDownTimer(5000, 1000) {

            @RequiresApi(api = Build.VERSION_CODES.N)
            @SuppressLint("SetTextI18n")
            public void onTick(long millisUntilFinished) {
                progressBar.setVisibility(View.VISIBLE);
                start.setVisibility(View.VISIBLE);
                if(dks.getCurrentSpeechLanguage().equals("de-DE")){
                    start.setText("Abfrage Abbrechen");
                }else{
                    start.setText("Cancel Query");
                }

                progressBar.setProgress((int) (progress + millisUntilFinished/1000), true);
                if(clicked){

                    progressBar.setVisibility(View.GONE);
                    start.setVisibility(View.GONE);
                    return;
                }
            }

            public void onFinish() {
                progressBar.setVisibility(View.GONE);
                start.setVisibility(View.GONE);
                if(!clicked){
                    try {
                        bePolite(query);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else{
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

    public void bePolite(String query) throws InterruptedException {

        if(query.contains("open Spotify") || query.contains("öffne Spotify") || query.contains("Spotify öffnen")) {
            startActivity(new Intent(this, LaunchSpotify.class));

        }else{

            try {
                Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                intent.putExtra(SearchManager.QUERY, query);
                startActivity(intent);
            } catch (Exception e) {
                throw new InterruptedException();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "on Pause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        dks.closeSpeechOperations();
        Log.i(TAG, "on Stop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.shutdown();
        dks.closeSpeechOperations();
        Log.i(TAG, "on destroy");
    }

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

    }

    /**
     * initializes dks continuous recognition and delivers the recorded speech to perform action
     * @param lang takes language as parameter to set default language
     */
    public void initializeDks(String lang){
        //initializing dks for recording purpose
        dks = new Dks(getApplication(), getSupportFragmentManager(), new DksListener() {

            @Override
            public void onDksLiveSpeechResult(@NotNull String liveSpeechResult) {
                System.out.println("dks is listening");
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("~ ~ ~");
                }
                Log.d(getPackageName(), "Speech result - " + liveSpeechResult);
                String livePreview = "" + liveSpeechResult;
                if(first_attempt){
                    textView.setText(livePreview);
                }
            }
            @Override
            public void onDksFinalSpeechResult(@NotNull String speechResult) {
                Log.d(getPackageName(), "Final Speech result - " + speechResult);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle("");
                }
                first_attempt = true;
                String query = speechResult;

                dks.closeSpeechOperations();//.....................testing
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
                            defineActivity(query);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onDksLiveSpeechFrequency(float frequency) {
            }

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

                System.out.println("The language is : " + dks.getCurrentSpeechLanguage());
            }

            @Override
            public void onDksSpeechError(@NotNull String errMsg) {
                Toast.makeText(getApplication(), errMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /*
    *initializes talk to speech and greets according to the language choosen
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


    /*
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

    /*
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
        System.out.println("***************I came here*********");
    }

}