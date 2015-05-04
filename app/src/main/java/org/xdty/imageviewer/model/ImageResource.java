package org.xdty.imageviewer.model;

import android.graphics.Bitmap;

import java.lang.ref.WeakReference;

import pl.droidsonroids.gif.GifDrawable;

/**
 * Created by ty on 15-5-4.
 */
public class ImageResource {

    final WeakReference<GifDrawable> gifDrawableWeakReference;
    final WeakReference<Bitmap> bitmapWeakReference;

    public ImageResource(GifDrawable gifDrawable, Bitmap bitmap) {
        this.gifDrawableWeakReference = new WeakReference<>(gifDrawable);
        this.bitmapWeakReference = new WeakReference<>(bitmap);
    }

    public GifDrawable getGifDrawable() {
        return gifDrawableWeakReference.get();
    }

    public Bitmap getBitmap() {
        return bitmapWeakReference.get();
    }
}
