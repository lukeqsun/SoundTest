/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.rit.soundtest;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;

import com.google.android.glass.touchpad.GestureDetector;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.widget.Slider;
import com.google.android.glass.widget.Slider.Determinate;

import java.util.ArrayList;
import java.util.List;

import edu.rit.utils.CardAdapter;

/**
 * An activity that demonstrates the slider API.
 */
public class SoundActivity extends Activity
    implements GestureDetector.OneFingerScrollListener {


    private SoundService mService;
    private boolean mBound = false;

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            SoundService.LocalBinder binder = (SoundService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    // Index of slider demo cards.
    private static final int PLAY_PAUSE = 0;
    private static final int FREQUENCY = 1;
    private static final int DELAY = 2;
    private static final int STOP = 3;

    private static final long ANIMATION_DURATION_MILLIS = 1;

    private CardScrollView mCardScroller;
    private Slider mSlider;
    private Determinate determinate;

    private GestureDetector mGestureDetector;
    private boolean enableFrequencyControl = false;
    private boolean enableDelayControl = false;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Ensure screen stays on during demo.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(new CardAdapter(createCards(this)));
        mCardScroller.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                processSliderRequest(position);
            }
        });
        mCardScroller.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                enableFrequencyControl = false;
                enableDelayControl = false;
                mCardScroller.activate();
                if (determinate != null) {
                    determinate.hide();
                    determinate = null;
                }
            }

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            }
        });
        setContentView(mCardScroller);
        mSlider = Slider.from(mCardScroller);

        // Initialize the gesture detector and set the activity to listen to the continuous
        // gestures.
        mGestureDetector = new GestureDetector(this)
                .setOneFingerScrollListener(this);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        //Bind to our service so we can manipulate theMediaPlayer if needed
        Intent intent = new Intent(this, SoundService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();
        super.onPause();
    }

    /**
     * Processes a request to show a slider.
     *
     * Starting a new Slider, regardless of its type, automatically hides any shown Slider.
     */
    private void processSliderRequest(int position) {
        switch (position) {
            case PLAY_PAUSE:
                if (mService.isPaused()) {
                    mService.resumeMusic();
                } else {
                    mService.pauseMusic();
                }
                break;
            case FREQUENCY:
                mCardScroller.deactivate();
                enableFrequencyControl = true;
                determinate = mSlider.startDeterminate(mService.MAX_FREQUENCY_VALUE, mService.getCurrentFrequency());
                break;
            case DELAY:
                mCardScroller.deactivate();
                enableDelayControl = true;
                determinate = mSlider.startDeterminate(mService.MAX_DELAY_VALUE, mService.getCurrentDelay());
                break;
            case STOP:
                //FIXME
                stopService(new Intent(this, SoundService.class));
                mService.onDestroy();
                this.finish();
                break;
        }
    }

    /**
     * Create a list of cards to display as activity content.
     */
    private List<CardBuilder> createCards(Context context) {
        ArrayList<CardBuilder> cards = new ArrayList<CardBuilder>();
        cards.add(PLAY_PAUSE, new CardBuilder(context, CardBuilder.Layout.TEXT)
                .setText(R.string.play_control));
        cards.add(FREQUENCY, new CardBuilder(context, CardBuilder.Layout.TEXT)
                .setText(R.string.frequency));
        cards.add(DELAY, new CardBuilder(context, CardBuilder.Layout.TEXT)
                .setText(R.string.delay));
        cards.add(STOP, new CardBuilder(context, CardBuilder.Layout.TEXT)
                .setText(R.string.stop));
        return cards;
    }

    /**
     * Overridden to allow the gesture detector to process motion events that occur anywhere within
     * the activity.
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mGestureDetector.onMotionEvent(event);
    }

    @Override
    public boolean onOneFingerScroll(float displacement, float delta, float velocity) {
        if (enableFrequencyControl) {
            mService.updateFrequency((double) delta);
            if (determinate != null) {
                determinate.setPosition(mService.getCurrentFrequency());
            }
        } else if (enableDelayControl) {
            mService.updateDelay((double) delta);
            if (determinate != null) {
                determinate.setPosition(mService.getCurrentDelay());
            }
        }

        return false;
    }
}
