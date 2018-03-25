package com.metao.handler;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ProgressBar progressBar;
    private Button download;
    private Button cancel;
    private ImageView image;
    private GlobalHandler globalHandler;
    private static final int DOWNLOAD = 1;
    private static final int ERROR = 2;
    private Timer timer;
    private int counter;
    private boolean locked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        download = (Button) findViewById(R.id.download);
        cancel = (Button) findViewById(R.id.cancel);
        image = (ImageView) findViewById(R.id.image);
        download.setOnClickListener(this);
        cancel.setOnClickListener(this);
        globalHandler = new GlobalHandler(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.contains("counter")) {
            counter = preferences.getInt("counter", counter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putInt("counter", counter);
        edit.apply();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cancel:
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
                break;
            case R.id.download:
                if (timer == null) {
                    timer = new Timer();
                    final TimerTask timerTask = new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(View.VISIBLE);
                                }
                            });
                            if (!locked) {
                                new ThreadHandler("https://picsum.photos/600/800/?image=" + counter++).start();
                            }
                        }
                    };
                    timer.scheduleAtFixedRate(timerTask, 400, 2000);
                    break;
                }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        if (globalHandler != null) {
            globalHandler.removeCallbacks(null);
        }
    }

    public void setError(String message) {
        progressBar.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void setImage(Bitmap image) {
        progressBar.setVisibility(View.GONE);
        this.image.setImageBitmap(image);
        this.image.setVisibility(View.VISIBLE);
    }

    private class ThreadHandler extends Thread {

        private final String urlAddress;
        private int responseCode;
        private InputStream in;
        private Bitmap bitmap;
        private Message message;

        ThreadHandler(String url) {
            this.urlAddress = url;
        }

        @Override
        public void run() {
            URL url = null;
            try {
                locked = true;
                Bundle bundle = new Bundle();
                url = new URL(this.urlAddress);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                //httpURLConnection.setDoOutput(true);
                httpURLConnection.connect();
                responseCode = httpURLConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    in = httpURLConnection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(in);
                    in.close();
                    message = globalHandler.obtainMessage(DOWNLOAD);
                    bundle.putParcelable("image", bitmap);
                    globalHandler.obtainMessage(DOWNLOAD);
                    message.setData(bundle);
                } else {
                    printError(String.valueOf(responseCode));
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                printError(String.valueOf(e.getMessage()));
            } catch (IOException e) {
                e.printStackTrace();
                printError(String.valueOf(e.getMessage()));
            } finally {
                globalHandler.sendMessage(message);
                locked = false;
            }
        }

        private void printError(String messageString) {
            Bundle bundle = new Bundle();
            message = globalHandler.obtainMessage(ERROR);
            bundle.putString("message", messageString);
            message.setData(bundle);
        }
    }

    private final static class GlobalHandler extends Handler {

        WeakReference<MainActivity> wr;

        GlobalHandler(MainActivity reference) {
            wr = new WeakReference<>(reference);
        }

        @Override
        public void dispatchMessage(Message msg) {
            if (msg != null) {
                switch (msg.what) {
                    case DOWNLOAD:
                        Bundle data = msg.getData();
                        if (data != null) {
                            Bitmap bitmap = data.getParcelable("image");
                            if (bitmap != null) {
                                MainActivity mainActivity = wr.get();
                                mainActivity.setImage(bitmap);
                            }
                        }
                        break;
                    case ERROR:
                        data = msg.getData();
                        if (data != null) {
                            String message = data.getString("message");
                            MainActivity mainActivity = wr.get();
                            mainActivity.setError(message);
                        }
                }
            }
        }
    }
}
