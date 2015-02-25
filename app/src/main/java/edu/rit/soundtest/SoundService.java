package edu.rit.soundtest;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

import com.google.android.glass.timeline.LiveCard;

public class SoundService extends Service {
    private final String CARD_ID = "my_music_card";
    private final IBinder binder = new LocalBinder();
    private LiveCard liveCard;
    private Handler handler = new Handler();
    private boolean paused = true;
    private SoundRender render;

    private int duration = 1; // seconds
    private int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private final double sample[] = new double[numSamples];
    private final byte generatedSnd[] = new byte[2 * numSamples];
    private AudioTrack audioTrack;

    private double freqOfTone = 500; // hz
    private int delay = 0; // sample

    public static final int MIN_FREQUENCY_VALUE = 100;
    public static final int MAX_FREQUENCY_VALUE = 4000;
    public static final int MIN_DELAY_VALUE = 0;
    public static final int MAX_DELAY_VALUE = 441;

    private long toneUpdateTime = 1000;
    private String playStatus = "|| Paused";

    private boolean enableDelayPeriod = false;

    /**
     * Thread which we will update our songs time in
     */
    private Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (enableDelayPeriod) {
                enableDelayPeriod = false;
                long realDelay = delay * 1000 / sampleRate;
                handler.postAtTime(updateTask, realDelay);
            } else {
                enableDelayPeriod = true;
                genTone();
                handler.postAtTime(updateTask, toneUpdateTime);
            }
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
            // Display the sound card when the live card is tapped.
            Intent soundIntent = new Intent(this, SoundActivity.class);
            soundIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            liveCard.setAction(PendingIntent.getActivity(this, 0, soundIntent, 0));

            render = new SoundRender(getApplicationContext());
            if (isPaused())
                playStatus = "|| Paused";
            else
                playStatus = "|> Playing";

            render.setTextOfView(playStatus + "\nfrequency: " + freqOfTone + " Hz\ndelay: " + delay + " sample(s)", null);

            liveCard.setDirectRenderingEnabled(true).getSurfaceHolder().addCallback(render);

            liveCard.publish(LiveCard.PublishMode.REVEAL);
        } else {
            liveCard.navigate();
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

    public int getCurrentFrequency() {
        return (int)freqOfTone;
    }

    public int getCurrentDelay() {
        return delay;
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

            playStatus = "|| Paused";

            render.setTextOfView(playStatus +
                            "\nfrequency: " + freqOfTone + " Hz\ndelay: " + delay + " sample(s)", null);
        }
    }

    /**
     * Resumes the music if it is currently paused
     */
    public void resumeMusic() {
        if (paused) {
            playStatus = "|> Playing";

            render.setTextOfView(playStatus +
                            "\nfrequency: " + freqOfTone + " Hz\ndelay: " + delay + " sample(s)", null);
            paused = false;
            audioTrack.play();
            handler.postAtTime(updateTask, toneUpdateTime);
        }
    }

    /**
     * Update frequency
     */
    public void updateFrequency(double delta) {
        try {
            freqOfTone += delta;
            if (freqOfTone <= MIN_FREQUENCY_VALUE)
                freqOfTone = MIN_FREQUENCY_VALUE;
            else if (freqOfTone >= MAX_FREQUENCY_VALUE)
                freqOfTone = MAX_FREQUENCY_VALUE;
            render.setTextOfView(playStatus + "\nfrequency: " + freqOfTone + " Hz\ndelay: " + delay + " sample(s)",
                    null);
        } catch (Exception e) {

        }
    }

    /**
     * Update delay
     */
    public void updateDelay(double delta) {
        try {
            delay += delta/10;
            if (delay <= MIN_DELAY_VALUE)
                delay = MIN_DELAY_VALUE;
            else if (delay >= MAX_DELAY_VALUE)
                delay = MAX_DELAY_VALUE;
            render.setTextOfView(playStatus + "\nfrequency: " + freqOfTone + " Hz\ndelay: " + delay + " sample(s)",
                    null);
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
        SoundService getService() {
            return SoundService.this;
        }
    }
}
