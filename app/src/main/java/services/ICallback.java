package services;

import android.graphics.Bitmap;

//FOR SENDING DOWNLOADED BITMAP TO SERVICE CLASS
public interface ICallback {

    void done(Bitmap bitmap);
}
