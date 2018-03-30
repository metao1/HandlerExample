package com.metao.handler;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.metao.handler.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int DOWNLOAD = 1;
    private static final int ERROR = 2;
    private static final String TAG = MainActivity.class.getCanonicalName();
    private GlobalHandler globalHandler;
    private ProgressBar progressBar;
    private FloatingActionButton play;
    private FloatingActionButton stop;
    private ImageView image;
    private Timer timer;
    private int counter;
    private boolean locked;
    private int width, height;
    private int from, to = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_main);
        progressBar = findViewById(R.id.progress);
        Drawable draw = getResources().getDrawable(R.drawable.progress_bar);
        progressBar.setProgressDrawable(draw);
        play = findViewById(R.id.play);
        stop = findViewById(R.id.stop);
        image = findViewById(R.id.image);
        play.setOnClickListener(this);
        stop.setOnClickListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("counter", counter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (globalHandler != null) {
            stop();
        } else {
            globalHandler = new GlobalHandler(this);
        }
        width = Utils.getDisplayDimens(this).x;
        height = Utils.getDisplayDimens(this).y;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.contains("counter")) {
            counter = preferences.getInt("counter", counter);
        }
    }

    private void stop() {
        globalHandler.removeCallbacks(null);
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putInt("counter", counter);
        edit.apply();
        stop();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.stop:
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
                break;
            case R.id.play:
                if (timer == null) {
                    timer = new Timer();
                    final TimerTask timerTask = new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(View.VISIBLE);
                                    ProgressBarAnimation anim = new ProgressBarAnimation(progressBar, from, to);
                                    if (from == 100) {
                                        from = 0;
                                    } else {
                                        from = 100;
                                    }
                                    if (to == 100) {
                                        to = 0;
                                    } else {
                                        to = 100;
                                    }
                                    anim.setDuration(1000);
                                    progressBar.startAnimation(anim);
                                }
                            });
                            if (!locked) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_out);
                                        image.startAnimation(animation);
                                    }
                                });
                                new ThreadHandler("https://picsum.photos/" + width + "/" + height + "/?image=" + counter++).start();
                            }
                        }
                    };
                    timer.scheduleAtFixedRate(timerTask, 400, 5000);
                    break;
                }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }

    public void setError(String message) {
        progressBar.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void setImage(final Bitmap bitmap) {
        progressBar.setVisibility(View.GONE);
        this.image.setVisibility(View.VISIBLE);
        Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_in);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                image.setImageBitmap(bitmap);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        image.startAnimation(animation);
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
                                if (mainActivity != null) {
                                    mainActivity.setImage(bitmap);
                                }
                            }
                        }
                        break;
                    case ERROR:
                        data = msg.getData();
                        if (data != null) {
                            String message = data.getString("message");
                            MainActivity mainActivity = wr.get();
                            if (mainActivity != null) {
                                mainActivity.setError(message);
                            }
                        }
                }
            }
        }
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
                if (globalHandler != null) {
                    globalHandler.sendMessage(message);
                }
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
}
