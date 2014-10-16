/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.omnirom.music.app.fragments.PlaylistChooserFragment;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utilities class used throughout the app
 */
public class Utils {
    private static final String TAG = "Utils";

    private static final Map<String, Bitmap> mBitmapQueue = new HashMap<String, Bitmap>();

    /**
     * Format milliseconds into an human-readable track length.
     * Examples:
     *  - 01:48:24 for 1 hour, 48 minutes, 24 seconds
     *  - 24:02 for 24 minutes, 2 seconds
     *  - 52s for 52 seconds
     * @param timeMs The time to format, in milliseconds
     * @return A formatted string
     */
    public static String formatTrackLength(int timeMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(timeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs)
                - TimeUnit.HOURS.toSeconds(hours)
                - TimeUnit.MINUTES.toSeconds(minutes);

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%02d:%02d", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("%02ds", seconds);
        } else {
            return "N/A";
        }
    }

    /**
     * Calculates the RMS audio level from the provided short sample extract
     *
     * @param audioData The audio samples
     * @return The RMS level
     */
    public static int calculateRMSLevel(short[] audioData, int numframes) {
        long lSum = 0;
        int numread = 0;
        for (short s : audioData) {
            lSum = lSum + s;
            numread++;
            if (numread == numframes) break;
        }

        double dAvg = lSum / numframes;
        double sumMeanSquare = 0d;

        numread = 0;
        for (short anAudioData : audioData) {
            sumMeanSquare = sumMeanSquare + Math.pow(anAudioData - dAvg, 2d);
            numread++;
            if (numread == numframes) break;
        }

        double averageMeanSquare = sumMeanSquare / numframes;

        return (int) (Math.pow(averageMeanSquare, 0.5d) + 0.5);
    }

    /**
     * Shows a short Toast style message
     * @param context The application context
     * @param res The String resource id
     */
    public static void shortToast(Context context, int res) {
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show();
    }

    /**
     * Calculates the optimal size of the text based on the text view width
     * @param textView The text view in which the text should fit
     * @param desiredWidth The desired final text width, or -1 for the TextView's getMeasuredWidth
     */
    public static void adjustTextSize(TextView textView, int desiredWidth) {
        if (desiredWidth <= 0) {
            desiredWidth = textView.getMeasuredWidth();

            if (desiredWidth <= 0) {
                // Invalid width, don't do anything
                Log.w("Utils", "adjustTextSize: Not doing anything (measured width invalid)");
                return;
            }
        }

        // Add some margin to width
        desiredWidth *= 0.8f;

        Paint paint = new Paint();
        Rect bounds = new Rect();

        paint.setTypeface(textView.getTypeface());
        float textSize = textView.getTextSize() * 2.0f;
        paint.setTextSize(textSize);
        String text = textView.getText().toString();
        paint.getTextBounds(text, 0, text.length(), bounds);

        while (bounds.width() > desiredWidth) {
            textSize--;
            paint.setTextSize(textSize);
            paint.getTextBounds(text, 0, text.length(), bounds);
        }

        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    /**
     * Temporarily store a bitmap
     * @param key The key
     * @param bmp The bitmap
     */
    public static void queueBitmap(String key, Bitmap bmp) {
        mBitmapQueue.put(key, bmp);
    }

    /**
     * Retrieve a bitmap from the store, and removes it
     * @param key The key used in queueBitmap
     * @return The bitmap associated with the key
     */
    public static Bitmap dequeueBitmap(String key) {
        Bitmap bmp = mBitmapQueue.get(key);
        mBitmapQueue.remove(key);
        return bmp;
    }

    /**
     * Animate a view expansion (unwrapping)
     * @param v The view to animate
     * @param expand True to animate expanding, false to animate closing
     * @return The animation object created
     */
    public static Animation animateExpand(final View v, final boolean expand) {
        try {
            Method m = v.getClass().getDeclaredMethod("onMeasure", int.class, int.class);
            m.setAccessible(true);
            m.invoke(
                    v,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(((View) v.getParent()).getMeasuredWidth(), View.MeasureSpec.UNSPECIFIED)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        final int initialHeight = v.getMeasuredHeight();

        if (expand) {
            v.getLayoutParams().height = 0;
        }
        else {
            v.getLayoutParams().height = initialHeight;
        }
        v.setVisibility(View.VISIBLE);

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int newHeight;
                if (expand) {
                    newHeight = (int) (initialHeight * interpolatedTime);
                } else {
                    newHeight = (int) (initialHeight * (1 - interpolatedTime));
                }
                v.getLayoutParams().height = newHeight;
                v.requestLayout();

                if (interpolatedTime == 1 && !expand)
                    v.setVisibility(View.GONE);
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        a.setDuration(500);
        return a;
    }


    public static void setLargeFabOutline(View[] views) {
        setFabOutline(R.dimen.floating_button_large_size, views);
    }

    public static void setSmallFabOutline(View[] views) {
        setFabOutline(R.dimen.floating_button_small_size, views);
    }

    private static void setFabOutline(int dimenRes, View[] views) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            /* Resources res = views[0].getResources();
            int size = res.getDimensionPixelSize(dimenRes);

            Outline outline = new Outline();
            outline.setOval(0, 0, size, size);

            for (View v : views) {
                v.setOutline(outline);
            } */
        }
    }

    /**
     * Provides an API-independent way to set a Drawable background on a view
     */
    public static void setViewBackground(View v, Drawable d) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            v.setBackground(d);
        } else {
            v.setBackgroundDrawable(d);
        }
    }

    public static int dpToPx(Resources res, int dp) {
        final DisplayMetrics displayMetrics = res.getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    /**
     * Figures out the main artist of an album based on its songs
     * @param a The album
     * @return A reference to the main artist, or null if none
     */
    public static String getMainArtist(Album a) {
        HashMap<String, Integer> occurrences = new HashMap<String, Integer>();
        Iterator<String> it = a.songs();

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        while (it.hasNext()) {
            String songRef = it.next();
            if (songRef == null) {
                Log.e(TAG, "Album '" + a.getName() + "' contains null songs!");
                continue;
            }

            Song song = aggregator.retrieveSong(songRef, a.getProvider());
            if (song != null) {
                String artistRef = song.getArtist();
                Integer count = occurrences.get(artistRef);
                if (count == null) {
                    count = 0;
                }
                count++;
                occurrences.put(artistRef, count);
            }
        }

        // Figure the max
        Map.Entry<String,Integer> maxEntry = null;
        for (Map.Entry<String,Integer> entry : occurrences.entrySet()) {
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }

        // If there's more than 5 tracks in the album and the most occurrences is one, consider
        // there's no major artist in the album and return null.
        if (maxEntry != null &&
                ((a.getSongsCount() > 5 && maxEntry.getValue() > 1) || a.getSongsCount() < 5)) {
            return maxEntry.getKey();
        } else {
            return null;
        }
    }

    /**
     * Figures out the main artist of a playlist based on its songs
     * @param p The playlist
     * @return A reference to the main artist, or null if none
     */
    public static String getMainArtist(Playlist p) {
        HashMap<String, Integer> occurrences = new HashMap<String, Integer>();
        Iterator<String> it = p.songs();

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        while (it.hasNext()) {
            String songRef = it.next();
            Song song = aggregator.retrieveSong(songRef, p.getProvider());

            if (song != null) {
                String artistRef = song.getArtist();
                Integer count = occurrences.get(artistRef);
                if (count == null) {
                    count = 0;
                }
                count++;
                occurrences.put(artistRef, count);
            }
        }

        // Figure the max
        Map.Entry<String,Integer> maxEntry = null;
        for (Map.Entry<String,Integer> entry : occurrences.entrySet()) {
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }

        // If there's more than 5 tracks in the album and the most occurrences is one, consider
        // there's no major artist in the album and return null.
        if (maxEntry != null &&
                ((p.getSongsCount() > 5 && maxEntry.getValue() > 1) || p.getSongsCount() < 5)) {
            return maxEntry.getKey();
        } else {
            return null;
        }
    }

    public static void showSongOverflow(final FragmentActivity context, final View parent,
                                        final Song song) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.track_overflow);
        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();

                switch (menuItem.getItemId()) {
                    case R.id.menu_play_now:
                        PlaybackProxy.playSong(song);
                        break;

                    case R.id.menu_open_artist:
                        ArtistActivity.craftIntent(context, null, song.getArtist(),
                                context.getResources().getColor(R.color.default_album_art_background));
                        break;

                    case R.id.menu_add_to_queue:
                        PlaybackProxy.queueSong(song, false);
                        break;

                    case R.id.menu_add_album_to_queue:
                        PlaybackProxy.queueAlbum(aggregator.retrieveAlbum(song.getAlbum(),
                                song.getProvider()), false);
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                        fragment.show(context.getSupportFragmentManager(), song.getRef());
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    public static void showCurrentSongOverflow(final FragmentActivity context, final View parent,
                                        final Song song) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.queue_overflow);
        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();

                switch (menuItem.getItemId()) {
                    case R.id.menu_open_album:
                        final Resources res = context.getResources();
                        Intent intent = AlbumActivity.craftIntent(context,
                                ((BitmapDrawable) res.getDrawable(R.drawable.album_placeholder)).getBitmap(),
                                aggregator.retrieveAlbum(song.getAlbum(), song.getProvider()),
                                res.getColor(R.color.default_album_art_background));
                        context.startActivity(intent);
                        break;

                    case R.id.menu_add_album_to_queue:
                        PlaybackProxy.queueAlbum(aggregator.retrieveAlbum(song.getAlbum(),
                                song.getProvider()), false);
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                        fragment.show(context.getSupportFragmentManager(), song.getRef());
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    /**
     * Serializes a string array with the specified separator
     *
     * @param elements The elements to serialize
     * @param separator The separator to use between the elements
     * @return The elements in a single string, separated with the specified separator, an empty
     *         string if elements is empty, or null if elements is null.
     */
    public static String implode(String[] elements, String separator) {
        if (elements == null) {
            return null;
        }

        if (elements.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(elements[0]);

        boolean skippedFirst = false;
        for (String s : elements) {
            if (skippedFirst) {
                builder.append(separator);
                builder.append(s);
            } else {
                skippedFirst = true;
            }
        }

        return builder.toString();
    }

    public static void animateScale(View v, boolean animate, boolean visible) {
        if (visible) {
            if (animate) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
            }
        } else {
            if (animate) {
                v.animate().scaleX(0.0f).scaleY(0.0f).setDuration(400)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                v.setScaleX(0.0f);
                v.setScaleY(0.0f);
            }
        }
    }

    public static void colorFloatingButton(View fab, int color, boolean bigFab) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            // RippleDrawable ripple = (RippleDrawable) fab.getBackground();
            // GradientDrawable back = (GradientDrawable) ripple.getDrawable(0);
            // back.setColor(color);
        } else {
            StateListDrawable rootShape = (StateListDrawable) fab.getBackground();
            LayerDrawable layer = (LayerDrawable) rootShape.getCurrent();
            GradientDrawable shape = (GradientDrawable) layer.getDrawable(1);
            shape.setColor(color);
        }
    }

    public static void setupLargeFabShadow(View fab) {
        setupFabShadow(fab, Utils.dpToPx(fab.getResources(), 48));
    }

    public static void setupSmallFabShadow(View fab) {
        int measuredWidth = fab.getMeasuredWidth();
        if (measuredWidth <= 0) {
            measuredWidth = fab.getWidth();
        }
        if (measuredWidth <= 0) {
            measuredWidth = Utils.dpToPx(fab.getResources(), 30);
        }
        setupFabShadow(fab, measuredWidth - 8);
    }

    private static void setupFabShadow(View fab, int radius) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            StateListDrawable rootShape = (StateListDrawable) fab.getBackground();
            LayerDrawable layer = (LayerDrawable) rootShape.getCurrent();
            GradientDrawable shadow = (GradientDrawable) layer.getDrawable(0);
            shadow.setColors(new int[]{0x80000000, 0x80000000, 0x80000000, 0x80000000, 0x80000000,
                    0x80000000, 0x0});
            shadow.setGradientRadius(radius);
        }
    }

    public static void setChildrenAlpha(ViewGroup root, final float alpha) {
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            root.getChildAt(i).setAlpha(alpha);
        }
    }
}
