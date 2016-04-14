/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlockview;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Rex on 2016.04.12.
 */
public class AppViewHolder extends RecyclerView.ViewHolder {
    TextView appLabel;
    ImageView appIcon;

    public AppViewHolder(View itemView) {
        super(itemView);
        appLabel = (TextView) (itemView.findViewById(R.id.appLabel));
        appIcon = (ImageView) (itemView.findViewById(R.id.appIcon));
        mContainer = (LinearLayout) (itemView.findViewById(R.id.appButton));
    }

    void setOnClickListener(View.OnClickListener listener) {
        mContainer.setOnClickListener(listener);
        mContainer.setClickable(true);
    }

    private LinearLayout mContainer;
}
