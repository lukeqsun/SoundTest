package edu.rit.soundtest;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;

import com.google.android.glass.timeline.LiveCard;

public class MainService extends Service {
    private final String CARD_ID = "my_music_card";
    private final IBinder binder = new LocalBinder();
    private LiveCard liveCard;
    private Handler handler = new Handler();
    private boolean paused = true;
    private MusicRender render;

    private int duration = 3; // seconds
    private int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private final double sample[] = new double[numSamples];
    private final byte generatedSnd[] = new byte[2 * numSamples];
    private double freqOfTone = 500; // hz
    private AudioTrack audioTrack;
    /**
     * Thread which we will update our songs time in
     */
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            genTone();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                8000, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, numSamples,
                AudioTrack.MODE_STREAM);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (liveCard == null) {
            liveCard = new LiveCard(getApplicationContext(), CARD_ID);
            // Display the options menu when the live card is tapped.
            Intent menuIntent = new Intent(this, MenuActivity.class);
            menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            liveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
            final RemoteViews loadingView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.loading_card);
            liveCard.setViews(loadingView);
            //Load our files in the background. I believe that doing this on the main thread caused an issue with my glass
            // that I had to factory reset to to resolve
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    try {
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    if (result) {
                        //Make sure we actually have songs to play

                        render = new MusicRender(getApplicationContext());

                        liveCard.unpublish();
                        liveCard.setDirectRenderingEnabled(true).getSurfaceHolder().addCallback(render);
                        //Immediately play the first song
                        try {
                            render.setTextOfView("500Hz", null);
                            //handler.postAtTime(updateTask, 250);
                            liveCard.publish(LiveCard.PublishMode.REVEAL);
                        } catch (Exception e) {

                        }

                    } else {
                        //If we have error, fail gracefully
                        loadingView.setTextViewText(R.id.message, getString(R.string.error_loading_music));
                        loadingView.setViewVisibility(R.id.progressBar, View.GONE);
                        liveCard.setViews(loadingView);
                        //Start a three second timer to kill the program
                        new CountDownTimer(3000, 3000) {
                            @Override
                            public void onTick(long millisUntilFinished) {

                            }

                            @Override
                            public void onFinish() {
                                stopSelf();
                            }
                        }.start();
                    }
                    super.onPostExecute(result);
                }
            }.execute();
            liveCard.publish(LiveCard.PublishMode.REVEAL);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        //unpublish our live card
        if (liveCard != null) {
            liveCard.unpublish();
            liveCard = null;
        }

        if (handler != null) {
            handler.removeCallbacks(updateTask);
            handler = null;
        }

        if (audioTrack != null)
            audioTrack.release();

        super.onDestroy();
    }

    /**
     * @return If the music is currently paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses music if it is currently playing
     */
    public void pauseMusic() {
        if (!paused) {
            if (handler != null) {
                handler.removeCallbacks(updateTask);
            }
            paused = true;
            audioTrack.pause();
        }
    }

    /**
     * Resumes the music if it is currently paused
     */
    public void resumeMusic() {
        if (paused) {
            paused = false;
            audioTrack.play();
            handler.postAtTime(updateTask, 1000);
        }
    }

    /**
     * Goes back to the previous track
     */
    public void previousTrack() {
        try {
            freqOfTone -= 100;
            if (freqOfTone <= 0)
                freqOfTone = 0;
            render.setTextOfView("" + freqOfTone + "Hz", null);
        } catch (Exception e) {

        }
    }

    /**
     * Goes to the next track
     */
    public void nextTrack() {
        try {
            freqOfTone += 100;
            if (freqOfTone >= 1000)
                freqOfTone = 1000;
            render.setTextOfView("" + freqOfTone + "Hz", null);
        } catch (Exception e) {

        }
    }

    private void genTone() {
        for (int i = 0; i < numSamples; ++i) {
            //  float angular_frequency =
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone));
        }
        int idx = 0;

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        audioTrack.write(generatedSnd, 0, numSamples);
    }

    /**
     * Converts milliseconds to a Human readable format
     *
     * @param milliseconds time in milliseconds
     * @return Human readable representation of the time
     */
    private String getHumanReadableTime(long milliseconds) {
        String finalTimerString = "";
        String secondsString = "";
        int minutes = (int) (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = (int) ((milliseconds % (1000 * 60 * 60)) % (1000 * 60) / 1000);
        if (seconds < 10) {
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;
        }
        finalTimerString = finalTimerString + minutes + ":" + secondsString;
        return finalTimerString;
    }

    public class LocalBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }
}
