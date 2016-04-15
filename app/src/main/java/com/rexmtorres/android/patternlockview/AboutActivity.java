/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import com.rexmtorres.android.patternlock.PatternLockView;
import com.rexmtorres.android.patternlockview.databinding.ActivityAboutBinding;

import java.util.Arrays;
import java.util.List;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        int theme = preferences.getInt(MainActivity.PATTERN_THEME, MainActivity.PATTERN_THEME_DOT);

        setTheme(theme == MainActivity.PATTERN_THEME_DOT ? R.style.AppThemeDot_NoActionBar : R.style.AppThemeDroid_NoActionBar);

        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_about);

        ActivityAboutBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_about);
        binding.setLibrary(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "rexmtorres@gmail.com", null));
                emailIntent.setType("text/html");
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Pattern-Lock");
                emailIntent.putExtra(Intent.EXTRA_TEXT, "Howdy!");

                startActivity(Intent.createChooser(emailIntent, "Send Email"));
            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView link = (TextView) findViewById(R.id.textViewLink);
        link.setMovementMethod(LinkMovementMethod.getInstance());

        PatternLockView patternLockView = (PatternLockView)findViewById(R.id.patternLock);
        patternLockView.setPattern(PatternLockView.DisplayMode.Animate, Arrays.asList(DEMO_PATTERN));
    }

    public String getVersion() {
        return "Library Version: " + com.rexmtorres.android.patternlock.BuildConfig.VERSION_NAME;
    }

    private static final PatternLockView.Cell[] DEMO_PATTERN = {
            PatternLockView.Cell.of(2, 0),
            PatternLockView.Cell.of(1, 0),
            PatternLockView.Cell.of(0, 1),
            PatternLockView.Cell.of(1, 2),
            PatternLockView.Cell.of(2, 2)
    };
}
