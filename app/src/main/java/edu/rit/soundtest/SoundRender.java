package edu.rit.soundtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff.Mode;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.TextView;

public class SoundRender implements SurfaceHolder.Callback {
    private SurfaceHolder surfaceHolder;

    private TextView title, artist, time;

    private View view;

    private int surfaceWidth;

    private int surfaceHeight;

    /**
     * Renders the current song title, album artwork, and artist of the current song
     *
     * @param context context of the view
     */
    public SoundRender(Context context) {
        view = LayoutInflater.from(context).inflate(R.layout.card, null);
        title = (TextView) view.findViewById(R.id.title);
        time = (TextView) view.findViewById(R.id.time);
    }

    @Override

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        doLayout();
        draw();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceHolder = null;
    }

    /**
     * Measures the layout
     */
    private void doLayout() {
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(surfaceWidth, View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(surfaceHeight, View.MeasureSpec.EXACTLY);
        view.measure(measuredWidth, measuredHeight);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    /**
     * Redraw the view to the surface
     */
    private synchronized void draw() {
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
        } catch (RuntimeException e) {

        }
        if (canvas != null) {
            doLayout();
            canvas.drawColor(0, Mode.CLEAR);
            view.draw(canvas);
            try {
                surfaceHolder.unlockCanvasAndPost(canvas);
            } catch (RuntimeException e) {

            }
        }
    }

    /**
     * Set the text of the Title, Artist, and Time. Passing null as a value will result in the text not being changed
     *
     * @param title
     * @param time
     */
    public void setTextOfView(String title, String time) {
        if (title != null) {
            this.title.setText(title);
        }
        if (time != null) {
            this.time.setText(time);
        }
        draw();
    }
}
