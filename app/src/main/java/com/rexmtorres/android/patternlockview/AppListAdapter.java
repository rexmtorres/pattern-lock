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
        holder.appLabel.setText(appInfo.appLabel);
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
        Collections.sort(mAppInfos, mAppInfoComparator);
        notifyDataSetChangedOnUi();
    }

    synchronized void addItem(AppInfo appInfo) {
        Log.v("LOCKED_LAUNCHER", "+ addItem");
        int index = Collections.binarySearch(mAppInfos, appInfo, mAppInfoComparator);

        if (index < 0) {
            // Binary search returns a negative integer specifying the location at which
            // the new item (i.e. item is not found in the list) can be inserted to maintain
            // an ordered list.

            final int position = -index - 1;
            Log.d("LOCKED_LAUNCHER", "  addItem> Inserting " + appInfo + " at position " + position);

            mAppInfos.add(position, appInfo);
            notifyItemInsertedOnUi(position);
        } else {
            // Binary search returns a positive integer specifying the location at which
            // the item is found.

            final int position = index;
            Log.d("LOCKED_LAUNCHER", "  addItem> Setting " + appInfo + " at position " + position);

            mAppInfos.set(index, appInfo);
            notifyItemChangedOnUi(position);
        }
        Log.v("LOCKED_LAUNCHER", "- addItem");
    }

    synchronized void removeItem(AppInfo appInfo) {
        final int index = Collections.binarySearch(mAppInfos, appInfo, mAppInfoComparator);

        if (index > -1) {
            mAppInfos.remove(index);
            notifyItemRemovedOnUi(index);
        }
    }

    synchronized void removeAll() {
        mAppInfos.clear();
        notifyDataSetChangedOnUi();
    }

    private void notifyDataSetChangedOnUi() {
        Log.v("LOCKED_LAUNCHER", "+ notifyDataSetChangedOnUi");
        mLauncherActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v("LOCKED_LAUNCHER", "+ notifyDataSetChanged");
                notifyDataSetChanged();
                Log.v("LOCKED_LAUNCHER", "- notifyDataSetChanged");
            }
        });
        Log.v("LOCKED_LAUNCHER", "- notifyDataSetChangedOnUi");
    }

    private void notifyItemChangedOnUi(final int position) {
        Log.v("LOCKED_LAUNCHER", "+ notifyItemChangedOnUi");
        mLauncherActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v("LOCKED_LAUNCHER", "+ notifyItemChanged");
                notifyItemChanged(position);
                Log.v("LOCKED_LAUNCHER", "- notifyItemChanged");
            }
        });
        Log.v("LOCKED_LAUNCHER", "- notifyItemChangedOnUi");
    }

    private void notifyItemInsertedOnUi(final int position) {
        Log.v("LOCKED_LAUNCHER", "+ notifyItemInsertedOnUi");
        mLauncherActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v("LOCKED_LAUNCHER", "+ notifyItemInserted");
                notifyItemInserted(position);
                Log.v("LOCKED_LAUNCHER", "- notifyItemInserted");
            }
        });
        Log.v("LOCKED_LAUNCHER", "- notifyItemInsertedOnUi");
    }

    private void notifyItemRemovedOnUi(final int position) {
        Log.v("LOCKED_LAUNCHER", "+ notifyItemRemovedOnUi");
        mLauncherActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.v("LOCKED_LAUNCHER", "+ notifyItemRemoved");
                notifyItemRemoved(position);
                Log.v("LOCKED_LAUNCHER", "- notifyItemRemoved");
            }
        });
        Log.v("LOCKED_LAUNCHER", "- notifyItemRemovedOnUi");
    }

    private AppCompatActivity mLauncherActivity;
    private List<AppInfo> mAppInfos;
    private OnItemClickListener mOnItemClickListener;

    private final Comparator<AppInfo> mAppInfoComparator = new Comparator<AppInfo>() {
        @Override
        public int compare(AppInfo lhs, AppInfo rhs) {
            return lhs.appLabel.concat(lhs.packageName).compareTo(rhs.appLabel.concat(rhs.packageName));
        }
    };
}
