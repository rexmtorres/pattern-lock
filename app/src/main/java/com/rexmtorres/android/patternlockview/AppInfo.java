/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * Created by Rex on 2016.04.12.
 */
class AppInfo {
    final String appName;
    final Drawable appIcon;
    final Intent launchIntent;

    AppInfo(String appName, Drawable appIcon, Intent launchIntent) {
        this.appName = appName;
        this.appIcon = appIcon;
        this.launchIntent = launchIntent;
    }

    @Override
    public String toString() {
        return String.format("AppInfo@0x%1$08X { [appName: %2$s] [appIcon: %3$s] [launchIntent: %4$s] }",
            hashCode(), appName, appIcon, launchIntent);
    }
}
