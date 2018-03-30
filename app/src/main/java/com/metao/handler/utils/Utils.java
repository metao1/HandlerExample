package com.metao.handler.utils;

import android.app.Activity;
import android.graphics.Point;
import android.util.DisplayMetrics;

public class Utils {

    public static Point getDisplayDimens(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        return new Point(width, height);
    }

}
