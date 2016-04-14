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
    final String appLabel;
    final String packageName;
    final Drawable appIcon;
    final Intent launchIntent;

    AppInfo(String appLabel, String packageName, Drawable appIcon, Intent launchIntent) {
        this.appLabel = appLabel;
        this.packageName = packageName;
        this.appIcon = appIcon;
        this.launchIntent = launchIntent;
    }

    @Override
    public String toString() {
        return String.format("AppInfo@0x%1$08X { [appLabel: %2$s] [packageName: %3$s] [appIcon: %4$s] [launchIntent: %5$s] }",
            hashCode(), appLabel, packageName, appIcon, launchIntent);
    }
}
