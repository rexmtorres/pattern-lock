/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    static final String PREF_NAME = "_" + String.valueOf("prefs".hashCode());
    static final String PATTERN_HASH = "_" + String.valueOf("pattern_hash".hashCode());
    static final String PATTERN_SET = "_" + String.valueOf("pattern_set".hashCode());
    static final String PATTERN_THEME = "_" + String.valueOf("pattern_theme".hashCode());

    static final int PATTERN_THEME_DOT = 0;
    //static final int PATTERN_THEME_DROID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mProgressBar = findViewById(R.id.progressBar);

        mAppListAdapter = new AppListAdapter(this, new AppListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(AppInfo item) {
                SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                boolean patternSet = preferences.getBoolean(PATTERN_SET, false);

                Log.d("LOCKED_LAUNCHER", "pattern set: " + patternSet);

                if (patternSet) {
                    ValidatePatternActivity.launchApp(MainActivity.this, ((BitmapDrawable) (item.appIcon)).getBitmap(), item.appLabel, item.launchIntent);
                } else {
                    startActivity(item.launchIntent);
                }
            }
        });

        String orientation = getString(R.string.orientation);

        mAppList = (RecyclerView) findViewById(R.id.appList);
        assert mAppList != null;
        mAppList.setHasFixedSize(true);
        mAppList.setLayoutManager(new GridLayoutManager(this, "landscape".equals(orientation) ? 5 : 3));
        mAppList.setAdapter(mAppListAdapter);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected Void doInBackground(Void... params) {
                List<AppInfo> launchableApps = getLaunchableApps();
                mAppListAdapter.setItems(launchableApps);

//                for(AppInfo appInfo : launchableApps) {
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) { }
//
//                    mAppListAdapter.addItem(appInfo);
//                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mProgressBar.setVisibility(View.GONE);
            }
        }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean patternSet = preferences.getBoolean(PATTERN_SET, false);

        menu.findItem(R.id.setPattern).setVisible(!patternSet);     // Don't show the "Set Pattern" menu anymore if a pattern has already been set.
        menu.findItem(R.id.changePattern).setVisible(patternSet);   // Allow the user to "Change Pattern" if a pattern has already been set.
        menu.findItem(R.id.clearPattern).setVisible(patternSet);    // Only allow user to "Clear Pattern" if a pattern has already been set.

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.setPattern:
                startActivity(new Intent(this, SetPatternActivity.class));
                return true;
            case R.id.changePattern:
                ValidatePatternActivity.validate(this, CODE_CHANGE_PATTERN);
                return true;
            case R.id.clearPattern:
                ValidatePatternActivity.validate(this, CODE_CLEAR_PATTERN);
                return true;
            case R.id.changePatternTheme:
                changePatternTheme();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            boolean patternCorrect = data != null && data.getBooleanExtra(ValidatePatternActivity.RESULT_PATTERN_CORRECT, false);

            if (requestCode == CODE_CHANGE_PATTERN) {
                if (patternCorrect) {
                    startActivity(new Intent(this, SetPatternActivity.class));
                } else {
                    Toast.makeText(MainActivity.this, R.string.pattern_not_changed, Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == CODE_CLEAR_PATTERN) {
                if (patternCorrect) {
                    clearPattern();
                } else {
                    Toast.makeText(MainActivity.this, R.string.pattern_not_cleared, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void clearPattern() {
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        preferences.edit().remove(PATTERN_HASH).remove(PATTERN_SET).apply();
        Toast.makeText(MainActivity.this, R.string.pattern_cleared, Toast.LENGTH_SHORT).show();
    }

    private void changePatternTheme() {
        final SharedPreferences preferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        new AlertDialog.Builder(this).setTitle(R.string.dialog_title)
            .setSingleChoiceItems(R.array.pattern_themes, preferences.getInt(PATTERN_THEME, PATTERN_THEME_DOT),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        preferences.edit().putInt(PATTERN_THEME, which).apply();
                        getApplication().setTheme(which == PATTERN_THEME_DOT ? R.style.AppThemeDot : R.style.AppThemeDroid);
                        dialog.dismiss();
                    }
                }).show();
    }

    private List<AppInfo> getLaunchableApps() {
        PackageManager manager = getPackageManager();

        List<ApplicationInfo> installedApps = manager.getInstalledApplications(0);
        List<AppInfo> launchableApps = new ArrayList<>();

        final int appCount = installedApps.size();

        for (int index = 0; index < appCount; index++) {
            ApplicationInfo applicationInfo = installedApps.get(index);
            Intent launchIntent = manager.getLaunchIntentForPackage(applicationInfo.packageName);

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchableApps.add(new AppInfo(applicationInfo.loadLabel(manager).toString(), applicationInfo.packageName, applicationInfo.loadIcon(manager), launchIntent));
            }
        }

        return launchableApps;
    }

    private static final int CODE_CHANGE_PATTERN = 14344;
    private static final int CODE_CLEAR_PATTERN = 14243;

    private RecyclerView mAppList;
    private AppListAdapter mAppListAdapter;

    private View mProgressBar;
}
