/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.rexmtorres.android.patternlock.PatternLockUtils;
import com.rexmtorres.android.patternlock.PatternLockView;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class SetPatternActivity extends AppCompatActivity implements PatternLockView.OnPatternListener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        int theme = preferences.getInt(MainActivity.PATTERN_THEME, MainActivity.PATTERN_THEME_DOT);

        setTheme(theme == MainActivity.PATTERN_THEME_DOT ? R.style.AppThemeDot : R.style.AppThemeDroid);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_pattern);

        mPatternLockView = (PatternLockView)findViewById(R.id.patternViewSet);
        assert mPatternLockView != null;
        mPatternLockView.setOnPatternListener(this);

        mInfoText = (TextView)findViewById(R.id.textViewInfo);

        Button cancelButton = (Button)findViewById(R.id.buttonCancel);
        assert cancelButton != null;
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
                finish();
            }
        });

        mConfirmationButton = (Button)findViewById(R.id.buttonOk);
        mConfirmationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doButtonAction();
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
        checkPattern(pattern);
    }

    @Override
    public void onPatternStart() {

    }

    private void clearPattern(final int infoId, final int delay) {
        mPatternLockView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(infoId != 0) {
                    mInfoText.setText(infoId);
                }

                mPatternLockView.setDisplayMode(null);
                mPatternLockView.clearPattern();
            }
        }, delay);
    }

    private void checkPattern(List<PatternLockView.Cell> pattern) {
        if(pattern.size() < MIN_PATTERN_LENGTH) {
            mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Wrong);
            mInfoText.setText(R.string.pattern_too_short);
            clearPattern(R.string.draw_unlock_pattern, CLEAR_DELAY);
        } else {
            final int tag = (Integer)(mConfirmationButton.getTag());

            switch (tag) {
                case R.string.continue_:
                    mPatternHashTemp = PatternLockUtils.patternToHash(pattern);

                    mInfoText.setText(R.string.confirm_unlock_pattern);
                    mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Correct);
                    mConfirmationButton.setEnabled(true);

                    //clearPattern(R.string.confirm_unlock_pattern, CLEAR_DELAY);

                    break;
                case R.string.confirm:
                    byte[] patternHash = PatternLockUtils.patternToHash(pattern);

                    if(Arrays.equals(mPatternHashTemp, patternHash)) {
                        mInfoText.setText(R.string.confirm_unlock_pattern);
                        mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Correct);
                        mConfirmationButton.setEnabled(true);

                        //clearPattern(R.string.confirm_unlock_pattern, CLEAR_DELAY);
                    } else {
                        mPatternLockView.setDisplayMode(PatternLockView.DisplayMode.Wrong);
                        mInfoText.setText(R.string.mismatch_pattern);

                        clearPattern(R.string.confirm_unlock_pattern, CLEAR_DELAY);
                    }

                    break;
            }
        }
    }

    private void doButtonAction() {
        mConfirmationButton.setEnabled(false);

        final int tag = (Integer)(mConfirmationButton.getTag());

        switch (tag) {
            case R.string.continue_:
                mConfirmationButton.setText(R.string.confirm);
                mConfirmationButton.setTag(R.string.confirm);
                clearPattern(0, 0);
                break;
            case R.string.confirm:
                savePattern();
                reset();
                finish();

                break;
        }
    }

    private void savePattern() {
        Log.i("LOCKED_LAUNCHER", "write: " + Base64.encodeToString(mPatternHashTemp, Base64.CRLF).trim());
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        preferences
            .edit()
            .putString(MainActivity.PATTERN_HASH, Base64.encodeToString(mPatternHashTemp, Base64.CRLF).trim())
            .putBoolean(MainActivity.PATTERN_SET, true)
            .apply();
    }

    private void reset() {
        mInfoText.setText(R.string.draw_unlock_pattern);

        mConfirmationButton.setText(R.string.continue_);
        mConfirmationButton.setTag(R.string.continue_);
        mConfirmationButton.setEnabled(false);

        mPatternLockView.setDisplayMode(null);
        mPatternLockView.clearPattern();

        clearTempArray();
    }

    private void clearTempArray() {
        if(mPatternHashTemp != null) {
            new SecureRandom().nextBytes(mPatternHashTemp);
        }
    }

    private static final int CLEAR_DELAY = 1000;
    private static final int MIN_PATTERN_LENGTH = 4;

    private Button mConfirmationButton;
    private TextView mInfoText;
    private PatternLockView mPatternLockView;
    private byte[] mPatternHashTemp;
}
