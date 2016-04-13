/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Rex on 2016.04.12.
 */
public class AppListAdapter extends RecyclerView.Adapter<AppViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(AppInfo item);
    }

    public AppListAdapter(AppCompatActivity launcherActivity, OnItemClickListener listener) {
        mLauncherActivity = launcherActivity;
        mAppInfos = new ArrayList<>();
        mOnItemClickListener = listener;
    }

    @Override
    public AppViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.app_item, parent, false));
    }

    @Override
    public void onBindViewHolder(AppViewHolder holder, int position) {
        final AppInfo appInfo = mAppInfos.get(position);

        holder.appIcon.setImageDrawable(appInfo.appIcon);
        holder.appName.setText(appInfo.appName);
        holder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mOnItemClickListener != null) {
                    mOnItemClickListener.onItemClick(appInfo);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mAppInfos.size();
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
    }

    synchronized void setItems(List<AppInfo> appInfos) {
        mAppInfos.clear();
        mAppInfos.addAll(appInfos);

        Collections.sort(mAppInfos, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                return lhs.appName.compareToIgnoreCase(rhs.appName);
            }
        });

        mLauncherActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    synchronized void addItem(AppInfo appInfo) {
        int index = Collections.binarySearch(mAppInfos, appInfo, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                return lhs.appName.compareToIgnoreCase(rhs.appName);
            }
        });

        if (index < 0) {
            // Binary search returns a negative integer specifying the location at which
            // the new item (i.e. item is not found in the list) can be inserted to maintain
            // an ordered list.

            final int position = -index - 1;
            Log.d("LOCKED_LAUNCHER", "Inserting " + appInfo + " at position " + position);

            mAppInfos.add(position, appInfo);

            mLauncherActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyItemInserted(position);
                }
            });
        } else {
            // Binary search returns a positive integer specifying the location at which
            // the item is found.

            final int position = index;
            Log.d("LOCKED_LAUNCHER", "Setting " + appInfo + " at position " + position);

            mAppInfos.set(index, appInfo);

            mLauncherActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyItemChanged(position);
                }
            });
        }
    }

    synchronized void removeItem(AppInfo appInfo) {
        final int index = Collections.binarySearch(mAppInfos, appInfo, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                return lhs.appName.compareToIgnoreCase(rhs.appName);
            }
        });

        if (index > -1) {
            mAppInfos.remove(index);

            mLauncherActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyItemRemoved(index);
                }
            });
        }
    }

    synchronized void removeAll() {
        mAppInfos.clear();

        mLauncherActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    private AppCompatActivity mLauncherActivity;
    private List<AppInfo> mAppInfos;
    private OnItemClickListener mOnItemClickListener;
}
