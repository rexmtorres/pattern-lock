/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.rexmtorres.android.patternlock.PatternLockUtils;
import com.rexmtorres.android.patternlock.PatternLockView;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class ValidatePatternActivity extends AppCompatActivity implements PatternLockView.OnPatternListener {
    static final String RESULT_PATTERN_CORRECT = "pattern_correct";

    static void launchApp(AppCompatActivity context, Bitmap appIcon, String appName, Intent targetIntent) {
        Intent launchIntent = new Intent(context, ValidatePatternActivity.class);
        launchIntent.putExtra(EXTRA_APP_ICON, appIcon);
        launchIntent.putExtra(EXTRA_APP_NAME, appName);
        launchIntent.putExtra(EXTRA_APP_INTENT, targetIntent);
        launchIntent.putExtra(EXTRA_CHANGE_PATTERN, false);

        context.startActivity(launchIntent);
    }

    static void validate(AppCompatActivity context, int requestCode) {
        Intent launchIntent = new Intent(context, ValidatePatternActivity.class);

        BitmapDrawable appIcon;

        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            appIcon = (BitmapDrawable)(context.getResources().getDrawable(R.mipmap.ic_launcher));
        } else {
            appIcon = (BitmapDrawable)(context.getDrawable(R.mipmap.ic_launcher));
        }

        launchIntent.putExtra(EXTRA_APP_ICON, appIcon != null ? appIcon.getBitmap() : null);
        launchIntent.putExtra(EXTRA_APP_NAME, context.getString(R.string.app_name));
        launchIntent.putExtra(EXTRA_CHANGE_PATTERN, true);

        context.startActivityForResult(launchIntent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        int theme = preferences.getInt(MainActivity.PATTERN_THEME, MainActivity.PATTERN_THEME_DOT);

        setTheme(theme == MainActivity.PATTERN_THEME_DOT ? R.style.AppThemeDot : R.style.AppThemeDroid);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_validate_pattern);

        Intent intent = getIntent();
        Bitmap icon = intent.getParcelableExtra(EXTRA_APP_ICON);
        String name = intent.getStringExtra(EXTRA_APP_NAME);

        mAppIntent = intent.getParcelableExtra(EXTRA_APP_INTENT);
        mChangePattern = intent.getBooleanExtra(EXTRA_CHANGE_PATTERN, false);

        if(icon != null) {
            ImageView appIcon = (ImageView)findViewById(R.id.imageViewAppIcon);
            assert appIcon != null;
            appIcon.setImageBitmap(icon);
        }

        if(name != null) {
            TextView appName = (TextView)findViewById(R.id.textViewAppName);
            assert appName != null;
            appName.setText(name);
        }

        mPatternLockView = (PatternLockView)findViewById(R.id.patternViewUnlock);
        assert mPatternLockView != null;
        mPatternLockView.setOnPatternListener(this);

        mInfoText = (TextView)findViewById(R.id.textViewInfo);

        Button cancelButton = (Button)findViewById(R.id.buttonCancel);
        assert cancelButton != null;
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPatternLockView.setDisplayMode(null);
                mPatternLockView.clearPattern();
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reset();
    }

    @Override
    public void onPatternCellAdded(List<PatternLockView.Cell> pattern) {

    }

    @Override
    public void onPatternCleared() {

    }

    @Override
    public void onPatternDetected(List<PatternLockView.Cell> pattern) {
        if(mChangePattern) {
            validateAndReturn(pattern);
        } else {
            validateAndLaunch(pattern);
        }
    }

    @Override
    public void onPatternStart() {

    }

    private void reset() {
        mInfoText.setText(R.string.draw_pattern_to_unlock);
        mPatternLockView.setDisplayMode(null);
        mPatternLockView.clearPattern();
    }

    private boolean isPatternCorrect(byte[] patternHash) {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);

        if(patternHash == null) {
            return false;
        }

        if(BuildConfig.DEBUG) {
            String input =  Base64.encodeToString(patternHash, Base64.CRLF).trim();
            String stored = preferences.getString(MainActivity.PATTERN_HASH, null);

            Log.i("LOCKED_LAUNCHER", "input: " + input);
            Log.i("LOCKED_LAUNCHER", "stored: " + stored);

            return input.contentEquals(stored);
        } else {
            return Base64.encodeToString(patternHash, Base64.CRLF).trim().contentEquals(preferences.getString(MainActivity.PATTERN_HASH, null));
        }
    }

    private void validateAndReturn(final List<PatternLockView.Cell> pattern) {
        boolean patternCorrect = isPatternCorrect(PatternLockUtils.patternToHash(pattern));
        Log.i("LOCKED_LAUNCHER", "pattern correct: " + patternCorrect);

        if(!patternCorrect) {
            mTryCount++;

            mInfoText.setText(R.string.incorrect_pattern);
            mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Wrong);

            if(mTryCount < NUM_TRIES) {
                clearPattern();
                return;
            }
        }

        Intent result = new Intent();
        result.putExtra(RESULT_PATTERN_CORRECT, patternCorrect);
        setResult(RESULT_OK, result);
        finish();
    }

    private void validateAndLaunch(final List<PatternLockView.Cell> pattern) {
        boolean patternCorrect = isPatternCorrect(PatternLockUtils.patternToHash(pattern));
        Log.i("LOCKED_LAUNCHER", "pattern correct: " + patternCorrect);

        if(!patternCorrect) {
            mTryCount++;

            mInfoText.setText(R.string.incorrect_pattern);
            mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Wrong);

            if(mTryCount < NUM_TRIES) {
                clearPattern();
            } else {
                finish();
            }
        } else {
            mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Correct);
            clearPattern();
            startActivity(mAppIntent);
            finish();
        }
    }

    private void clearPattern() {
        mPatternLockView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPatternLockView.setDisplayMode(null);
                mPatternLockView.clearPattern();
            }
        }, 1000);
    }

    private static final int NUM_TRIES = 3;

    private static final String EXTRA_APP_ICON = "com.rexmtorres.android.patternlockview.APP_ICON";
    private static final String EXTRA_APP_NAME = "com.rexmtorres.android.patternlockview.APP_NAME";
    private static final String EXTRA_APP_INTENT = "com.rexmtorres.android.patternlockview.APP_INTENT";
    private static final String EXTRA_CHANGE_PATTERN = "com.rexmtorres.android.patternlockview.CHANGE_PATTERN";

    private boolean mChangePattern;
    private int mTryCount;

    private TextView mInfoText;
    private PatternLockView mPatternLockView;
    private Intent mAppIntent;
}
