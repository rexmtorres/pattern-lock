/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rexmtorres.android.patternlock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v4.widget.ExploreByTouchHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Displays and detects the user's unlock attempt, which is a drag of a finger
 * across 9 regions of the screen.
 * <p>
 * Is also capable of displaying a static pattern in "in progress", "wrong" or
 * "correct" states.
 */
public class PatternLockView extends View {
    /**
     * Represents a cell in the 3 X 3 matrix of the unlock pattern view.
     */
    public static final class Cell {
        final int column;
        final int row;

        public static Cell of(int row, int column) {
            checkRange(row, column);
            return sCells[row][column];
        }

        @Override
        public String toString() {
            return "(row=" + row + ",clmn=" + column + ")";
        }

        public int getColumn() {
            return column;
        }

        public int getRow() {
            return row;
        }

        private static void checkRange(int row, int column) {
            if (row < 0 || row > 2) {
                throw new IllegalArgumentException("row must be in range 0-2");
            }
            if (column < 0 || column > 2) {
                throw new IllegalArgumentException("column must be in range 0-2");
            }
        }

        private static Cell[][] createCells() {
            Cell[][] res = new Cell[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    res[i][j] = new Cell(i, j);
                }
            }
            return res;
        }

        /**
         * @param row    The row of the cell.
         * @param column The column of the cell.
         */
        private Cell(int row, int column) {
            checkRange(row, column);
            this.row = row;
            this.column = column;
        }

        // keep # objects limited to 9
        private static final Cell[][] sCells = createCells();
    }

    /**
     * How to display the current pattern.
     */
    public enum DisplayMode {
        /**
         * The pattern drawn is correct (i.e draw it in a friendly color)
         */
        Correct,
        /**
         * Animate the pattern (for demo, and help).
         */
        Animate,
        /**
         * The pattern is wrong (i.e draw a foreboding color)
         */
        Wrong
    }

    public static class CellState {
        public ValueAnimatorCompat lineAnimator;
        public float lineEndX = Float.MIN_VALUE;
        public float lineEndY = Float.MIN_VALUE;
        // [rexmtorres] If set, replaces the pattern dots with the specified bitmap.
        Bitmap bitmapDot;
        float alpha = 1f;
        float radius;
        float translationY;
        int col;
        int row;
    }

    /**
     * The call back interface for detecting patterns entered by the user.
     */
    public interface OnPatternListener {
        /**
         * The user extended the pattern currently being drawn by one cell.
         *
         * @param pattern The pattern with newly added cell.
         */
        void onPatternCellAdded(List<Cell> pattern);

        /**
         * The pattern was cleared.
         */
        void onPatternCleared();

        /**
         * A pattern was detected from the user.
         *
         * @param pattern The pattern.
         */
        void onPatternDetected(List<Cell> pattern);

        /**
         * A new pattern has begun.
         */
        void onPatternStart();
    }

    @SuppressWarnings("deprecation")
    public PatternLockView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = getContext();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PatternLockView, R.attr.patternLockViewStyle, 0);
        final String aspect = a.getString(R.styleable.PatternLockView_aspect);
        if ("square".equals(aspect)) {
            mAspect = ASPECT_SQUARE;
        } else if ("lock_width".equals(aspect)) {
            mAspect = ASPECT_LOCK_WIDTH;
        } else if ("lock_height".equals(aspect)) {
            mAspect = ASPECT_LOCK_HEIGHT;
        } else {
            mAspect = ASPECT_SQUARE;
        }
        setClickable(true);
        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRegularColor = context.getColor(R.color.lock_pattern_view_regular_color);
            mErrorColor = context.getColor(R.color.lock_pattern_view_error_color);
            mSuccessColor = context.getColor(R.color.lock_pattern_view_success_color);
        } else {
            Resources resources = context.getResources();

            mRegularColor = resources.getColor(R.color.lock_pattern_view_regular_color);
            mErrorColor = resources.getColor(R.color.lock_pattern_view_error_color);
            mSuccessColor = resources.getColor(R.color.lock_pattern_view_success_color);
        }

        mRegularColor = a.getColor(R.styleable.PatternLockView_regularColor, mRegularColor);
        mErrorColor = a.getColor(R.styleable.PatternLockView_errorColor, mErrorColor);
        mSuccessColor = a.getColor(R.styleable.PatternLockView_successColor, mSuccessColor);

        int pathColor = a.getColor(R.styleable.PatternLockView_pathColor, mRegularColor);

        // [START rexmtorres 20160401] If set, replaces the pattern dots with the specified bitmap.
        Drawable oDotDrawable = a.getDrawable(R.styleable.PatternLockView_dotBitmap);

        if (oDotDrawable != null) {
            if (oDotDrawable instanceof BitmapDrawable) {
                m_oDotBitmap = ((BitmapDrawable) oDotDrawable).getBitmap();
                m_oBigDotBitmap = Bitmap.createScaledBitmap(m_oDotBitmap, (int) (m_oDotBitmap.getWidth() * 1.25), (int) (m_oDotBitmap.getHeight() * 1.25), false);
            }
        }
        // [END rexmtorres 20160401]

        a.recycle();

        mPathPaint.setColor(pathColor);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);
        mPathWidth = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_line_width);
        mPathPaint.setStrokeWidth(mPathWidth);
        mDotSize = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_size);
        mDotSizeActivated = getResources().getDimensionPixelSize(R.dimen.lock_pattern_dot_size_activated);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mCellStates = new CellState[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mCellStates[i][j] = new CellState();
                mCellStates[i][j].radius = mDotSize / 2;
                mCellStates[i][j].row = i;
                mCellStates[i][j].col = j;
                mCellStates[i][j].bitmapDot = m_oDotBitmap;
            }
        }

        mFastOutSlowInInterpolator = new FastOutSlowInInterpolator();
        mLinearOutSlowInInterpolator = new LinearOutSlowInInterpolator();
        mExploreByTouchHelper = new PatternExploreByTouchHelper(this);
        ViewCompat.setAccessibilityDelegate(this, mExploreByTouchHelper);
        mAccessibilityManager = (AccessibilityManager) mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public PatternLockView(Context context) {
        this(context, null);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (mAccessibilityManager.isTouchExplorationEnabled()) {
                final int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        event.setAction(MotionEvent.ACTION_DOWN);
                        break;
                    case MotionEvent.ACTION_HOVER_MOVE:
                        event.setAction(MotionEvent.ACTION_MOVE);
                        break;
                    case MotionEvent.ACTION_HOVER_EXIT:
                        event.setAction(MotionEvent.ACTION_UP);
                        break;
                }
                onTouchEvent(event);
                event.setAction(action);
            }
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(event);
                return true;
            case MotionEvent.ACTION_UP:
                handleActionUp();
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mPatternInProgress) {
                    setPatternInProgress(false);
                    resetPattern();
                    notifyPatternCleared();
                }
                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
        }
        return false;
    }

    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    /**
     * Disable input (for instance when displaying a message that will
     * timeout so user doesn't get view into messy state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    public CellState[][] getCellStates() {
        return mCellStates;
    }

    /**
     * @return Whether the view is in stealth mode.
     */
    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    /**
     * @return Whether the view has tactile feedback enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return mEnableHapticFeedback;
    }

    /**
     * Set the display mode of the current pattern.  This can be useful, for
     * instance, after detecting a pattern to tell this view whether change the
     * in progress result to correct or wrong.
     *
     * @param displayMode The display mode.
     */
    public void setDisplayMode(DisplayMode displayMode) {
        mPatternDisplayMode = displayMode;
        if (displayMode == DisplayMode.Animate) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException("you must have a pattern to " + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Cell first = mPattern.get(0);
            mInProgressX = getCenterXForColumn(first.getColumn());
            mInProgressY = getCenterYForRow(first.getRow());
            clearPatternDrawLookup();
        }
        invalidate();
    }

    /**
     * Set whether the view is in stealth mode.  If true, there will be no
     * visible feedback as the user enters the pattern.
     *
     * @param inStealthMode Whether in stealth mode.
     */
    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    /**
     * Set the call back for pattern detection.
     *
     * @param onPatternListener The call back.
     */
    public void setOnPatternListener(OnPatternListener onPatternListener) {
        mOnPatternListener = onPatternListener;
    }

    /**
     * Set the pattern explicitely (rather than waiting for the user to input
     * a pattern).
     *
     * @param displayMode How to display the pattern.
     * @param pattern     The pattern.
     */
    public void setPattern(DisplayMode displayMode, List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Cell cell : pattern) {
            mPatternDrawLookup[cell.getRow()][cell.getColumn()] = true;
        }
        setDisplayMode(displayMode);
    }

    /**
     * Set whether the view will use tactile feedback.  If true, there will be
     * tactile feedback as the user enters the pattern.
     *
     * @param tactileFeedbackEnabled Whether tactile feedback is enabled
     */
    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    public void startCellStateAnimation(CellState cellState, float startAlpha, float endAlpha, float startTranslationY, float endTranslationY, float startScale, float endScale, long delay, long duration, Interpolator interpolator, Runnable finishRunnable) {
        startCellStateAnimationSw(cellState, startAlpha, endAlpha, startTranslationY, endTranslationY, startScale, endScale, delay, duration, interpolator, finishRunnable);
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        // Dispatch to onHoverEvent first so mPatternInProgress is up to date when the
        // helper gets the event.
        boolean handled = super.dispatchHoverEvent(event);
        handled |= mExploreByTouchHelper.dispatchHoverEvent(event);
        return handled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final ArrayList<Cell> pattern = mPattern;
        final int count = pattern.size();
        final boolean[][] drawLookup = mPatternDrawLookup;
        if (mPatternDisplayMode == DisplayMode.Animate) {
            // figure out which circles to draw
            // + 1 so we pause on complete pattern
            final int oneCycle = (count + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            final int spotInCycle = (int) (SystemClock.elapsedRealtime() - mAnimatingPeriodStart) % oneCycle;
            final int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;
            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                final Cell cell = pattern.get(i);
                drawLookup[cell.getRow()][cell.getColumn()] = true;
            }
            // figure out in progress portion of ghosting line
            final boolean needToUpdateInProgressPoint = numCircles > 0 && numCircles < count;
            if (needToUpdateInProgressPoint) {
                final float percentageOfNextCircle = ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING)) / MILLIS_PER_CIRCLE_ANIMATING;
                final Cell currentCell = pattern.get(numCircles - 1);
                final float centerX = getCenterXForColumn(currentCell.column);
                final float centerY = getCenterYForRow(currentCell.row);
                final Cell nextCell = pattern.get(numCircles);
                final float dx = percentageOfNextCircle * (getCenterXForColumn(nextCell.column) - centerX);
                final float dy = percentageOfNextCircle * (getCenterYForRow(nextCell.row) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            // TODO: Infinite loop here...
            invalidate();
        }
        final Path currentPath = mCurrentPath;
        currentPath.rewind();
        // draw the circles
        for (int i = 0; i < 3; i++) {
            float centerY = getCenterYForRow(i);
            for (int j = 0; j < 3; j++) {
                CellState cellState = mCellStates[i][j];
                float centerX = getCenterXForColumn(j);
                float translationY = cellState.translationY;
                drawDot(canvas, (int) centerX, (int) centerY + translationY, cellState.radius, drawLookup[i][j], cellState.alpha, cellState.bitmapDot);
            }
        }

        // TODO: the path should be created and cached every time we hit-detect a cell
        // only the last segment of the path should be computed here
        // draw the path of the pattern (unless we are in stealth mode)
        final boolean drawPath = !mInStealthMode;
        if (drawPath) {
            mPathPaint.setColor(getCurrentColor(true /* partOfPattern */));
            boolean anyCircles = false;
            float lastX = 0f;
            float lastY = 0f;
            for (int i = 0; i < count; i++) {
                Cell cell = pattern.get(i);
                // only draw the part of the pattern stored in
                // the lookup table (this is only different in the case
                // of animation).
                if (!drawLookup[cell.row][cell.column]) {
                    break;
                }
                anyCircles = true;
                float centerX = getCenterXForColumn(cell.column);
                float centerY = getCenterYForRow(cell.row);
                if (i != 0) {
                    CellState state = mCellStates[cell.row][cell.column];
                    currentPath.rewind();
                    currentPath.moveTo(lastX, lastY);
                    if (state.lineEndX != Float.MIN_VALUE && state.lineEndY != Float.MIN_VALUE) {
                        currentPath.lineTo(state.lineEndX, state.lineEndY);
                    } else {
                        currentPath.lineTo(centerX, centerY);
                    }
                    canvas.drawPath(currentPath, mPathPaint);
                }
                lastX = centerX;
                lastY = centerY;
            }
            // draw last in progress section
            if ((mPatternInProgress || mPatternDisplayMode == DisplayMode.Animate) && anyCircles) {
                currentPath.rewind();
                currentPath.moveTo(lastX, lastY);
                currentPath.lineTo(mInProgressX, mInProgressY);
                mPathPaint.setAlpha((int) (calculateLastSegmentAlpha(mInProgressX, mInProgressY, lastX, lastY) * 255f));
                canvas.drawPath(currentPath, mPathPaint);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);
        switch (mAspect) {
            case ASPECT_SQUARE:
                viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_WIDTH:
                viewHeight = Math.min(viewWidth, viewHeight);
                break;
            case ASPECT_LOCK_HEIGHT:
                viewWidth = Math.min(viewWidth, viewHeight);
                break;
        }
        // Log.v(TAG, "PatternLockView dimensions: " + viewWidth + "x" + viewHeight);
        setMeasuredDimension(viewWidth, viewHeight);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setPattern(DisplayMode.Correct, PatternLockUtils.stringToPattern(ss.getSerializedPattern()));
        mPatternDisplayMode = DisplayMode.values()[ss.getDisplayMode()];
        mInputEnabled = ss.isInputEnabled();
        mInStealthMode = ss.isInStealthMode();
        mEnableHapticFeedback = ss.isTactileFeedbackEnabled();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, PatternLockUtils.patternToString(mPattern), mPatternDisplayMode.ordinal(), mInputEnabled, mInStealthMode, mEnableHapticFeedback);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - getPaddingLeft() - getPaddingRight();
        mSquareWidth = width / 3.0f;
        if (DEBUG_A11Y) {
            Log.v(TAG, "onSizeChanged(" + w + "," + h + ")");
        }
        final int height = h - getPaddingTop() - getPaddingBottom();
        mSquareHeight = height / 3.0f;
        mExploreByTouchHelper.invalidateRoot();
    }

    private void addCellToPattern(Cell newCell) {
        mPatternDrawLookup[newCell.getRow()][newCell.getColumn()] = true;
        mPattern.add(newCell);
        if (!mInStealthMode) {
            startCellActivatedAnimation(newCell);
        }
        notifyCellAdded();
    }

    private float calculateLastSegmentAlpha(float x, float y, float lastX, float lastY) {
        float diffX = x - lastX;
        float diffY = y - lastY;
        float dist = (float) Math.sqrt(diffX * diffX + diffY * diffY);
        float frac = dist / mSquareWidth;
        return Math.min(1f, Math.max(0f, (frac - 0.3f) * 4f));
    }

    private void cancelLineAnimations() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                CellState state = mCellStates[i][j];
                if (state.lineAnimator != null) {
                    state.lineAnimator.cancel();
                    state.lineEndX = Float.MIN_VALUE;
                    state.lineEndY = Float.MIN_VALUE;
                }
            }
        }
    }

    // helper method to find which cell a point maps to
    private Cell checkForNewHit(float x, float y) {
        final int rowHit = getRowHit(y);
        if (rowHit < 0) {
            return null;
        }
        final int columnHit = getColumnHit(x);
        if (columnHit < 0) {
            return null;
        }
        if (mPatternDrawLookup[rowHit][columnHit]) {
            return null;
        }
        return Cell.of(rowHit, columnHit);
    }

    /**
     * Clear the pattern lookup table.
     */
    private void clearPatternDrawLookup() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }

    /**
     * Determines whether the point x, y will add a new point to the current
     * pattern (in addition to finding the cell, also makes heuristic choices
     * such as filling in gaps based on current pattern).
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    private Cell detectAndAddHit(float x, float y) {
        final Cell cell = checkForNewHit(x, y);
        if (cell != null) {
            // check for gaps in existing pattern
            Cell fillInGapCell = null;
            final ArrayList<Cell> pattern = mPattern;
            if (!pattern.isEmpty()) {
                final Cell lastCell = pattern.get(pattern.size() - 1);
                int dRow = cell.row - lastCell.row;
                int dColumn = cell.column - lastCell.column;
                int fillInRow = lastCell.row;
                int fillInColumn = lastCell.column;
                if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
                    fillInRow = lastCell.row + ((dRow > 0) ? 1 : -1);
                }
                if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
                    fillInColumn = lastCell.column + ((dColumn > 0) ? 1 : -1);
                }
                fillInGapCell = Cell.of(fillInRow, fillInColumn);
            }
            if (fillInGapCell != null && !mPatternDrawLookup[fillInGapCell.row][fillInGapCell.column]) {
                addCellToPattern(fillInGapCell);
            }
            addCellToPattern(cell);
            return cell;
        }
        return null;
    }

    /**
     * @param partOfPattern Whether this circle is part of the pattern.
     */
    private void drawDot(Canvas canvas, float centerX, float centerY, float radius, boolean partOfPattern, float alpha, Bitmap oDotBitmap) {
        mPaint.setColor(getCurrentColor(partOfPattern));
        mPaint.setAlpha((int) (alpha * 255));

        // If the bitmap is set, draw it.  If not, draw a circle.
        if (oDotBitmap != null) {
            float nBitmapCenterX = centerX - (oDotBitmap.getScaledHeight(canvas) / 2f);
            float nBitmapCenterY = centerY - (oDotBitmap.getScaledWidth(canvas) / 2f);

            canvas.drawBitmap(oDotBitmap, nBitmapCenterX, nBitmapCenterY, mPaint);
        } else {
            canvas.drawCircle(centerX, centerY, radius, mPaint);
        }
    }

    private float getCenterXForColumn(int column) {
        return getPaddingLeft() + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return getPaddingTop() + row * mSquareHeight + mSquareHeight / 2f;
    }

    /**
     * Helper method to find the column x fallis into.
     *
     * @param x The x coordinate.
     *
     * @return The column that x falls in, or -1 if it falls in no column.
     */
    private int getColumnHit(float x) {
        final float squareWidth = mSquareWidth;
        float hitSize = squareWidth * mHitFactor;
        float offset = getPaddingLeft() + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {
            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    // [rexmtorres 20160401]
    // Original name: drawCircle
    // Added support for drawing a Bitmap, if set, instead of a circle for the pattern dots.

    private int getCurrentColor(boolean partOfPattern) {
        if (!partOfPattern || mInStealthMode || mPatternInProgress) {
            // unselected circle
            return mRegularColor;
        } else if (mPatternDisplayMode == DisplayMode.Wrong) {
            // the pattern is wrong
            return mErrorColor;
        } else if (mPatternDisplayMode == DisplayMode.Correct || mPatternDisplayMode == DisplayMode.Animate) {
            return mSuccessColor;
        } else {
            throw new IllegalStateException("unknown display mode " + mPatternDisplayMode);
        }
    }

    /**
     * Helper method to find the row that y falls into.
     *
     * @param y The y coordinate
     *
     * @return The row that y falls in, or -1 if it falls in no row.
     */
    private int getRowHit(float y) {
        final float squareHeight = mSquareHeight;
        float hitSize = squareHeight * mHitFactor;
        float offset = getPaddingTop() + (squareHeight - hitSize) / 2f;
        for (int i = 0; i < 3; i++) {
            final float hitTop = offset + squareHeight * i;
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i;
            }
        }
        return -1;
    }

    private void handleActionDown(MotionEvent event) {
        resetPattern();
        final float x = event.getX();
        final float y = event.getY();
        final Cell hitCell = detectAndAddHit(x, y);
        if (hitCell != null) {
            setPatternInProgress(true);
            mPatternDisplayMode = DisplayMode.Correct;
            notifyPatternStarted();
        } else if (mPatternInProgress) {
            setPatternInProgress(false);
            notifyPatternCleared();
        }
        if (hitCell != null) {
            final float startX = getCenterXForColumn(hitCell.column);
            final float startY = getCenterYForRow(hitCell.row);
            final float widthOffset = mSquareWidth / 2f;
            final float heightOffset = mSquareHeight / 2f;
            invalidate((int) (startX - widthOffset), (int) (startY - heightOffset), (int) (startX + widthOffset), (int) (startY + heightOffset));
        }
        mInProgressX = x;
        mInProgressY = y;
        if (PROFILE_DRAWING) {
            if (!mDrawingProfilingStarted) {
                Debug.startMethodTracing("LockPatternDrawing");
                mDrawingProfilingStarted = true;
            }
        }
    }

    private void handleActionMove(MotionEvent event) {
        // Handle all recent motion events so we don't skip any cells even when the device
        // is busy...
        final float radius = mPathWidth;
        final int historySize = event.getHistorySize();
        mTmpInvalidateRect.setEmpty();
        boolean invalidateNow = false;
        for (int i = 0; i < historySize + 1; i++) {
            final float x = i < historySize ? event.getHistoricalX(i) : event.getX();
            final float y = i < historySize ? event.getHistoricalY(i) : event.getY();
            Cell hitCell = detectAndAddHit(x, y);
            final int patternSize = mPattern.size();
            if (hitCell != null && patternSize == 1) {
                setPatternInProgress(true);
                notifyPatternStarted();
            }
            // note current x and y for rubber banding of in progress patterns
            final float dx = Math.abs(x - mInProgressX);
            final float dy = Math.abs(y - mInProgressY);
            if (dx > DRAG_THRESHHOLD || dy > DRAG_THRESHHOLD) {
                invalidateNow = true;
            }
            if (mPatternInProgress && patternSize > 0) {
                final ArrayList<Cell> pattern = mPattern;
                final Cell lastCell = pattern.get(patternSize - 1);
                float lastCellCenterX = getCenterXForColumn(lastCell.column);
                float lastCellCenterY = getCenterYForRow(lastCell.row);
                // Adjust for drawn segment from last cell to (x,y). Radius accounts for line width.
                float left = Math.min(lastCellCenterX, x) - radius;
                float right = Math.max(lastCellCenterX, x) + radius;
                float top = Math.min(lastCellCenterY, y) - radius;
                float bottom = Math.max(lastCellCenterY, y) + radius;
                // Invalidate between the pattern's new cell and the pattern's previous cell
                if (hitCell != null) {
                    final float width = mSquareWidth * 0.5f;
                    final float height = mSquareHeight * 0.5f;
                    final float hitCellCenterX = getCenterXForColumn(hitCell.column);
                    final float hitCellCenterY = getCenterYForRow(hitCell.row);
                    left = Math.min(hitCellCenterX - width, left);
                    right = Math.max(hitCellCenterX + width, right);
                    top = Math.min(hitCellCenterY - height, top);
                    bottom = Math.max(hitCellCenterY + height, bottom);
                }
                // Invalidate between the pattern's last cell and the previous location
                mTmpInvalidateRect.union(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
            }
        }
        mInProgressX = event.getX();
        mInProgressY = event.getY();
        // To save updates, we only invalidate if the user moved beyond a certain amount.
        if (invalidateNow) {
            mInvalidate.union(mTmpInvalidateRect);
            invalidate(mInvalidate);
            mInvalidate.set(mTmpInvalidateRect);
        }
    }

    private void handleActionUp() {
        // report pattern detected
        if (!mPattern.isEmpty()) {
            setPatternInProgress(false);
            cancelLineAnimations();
            notifyPatternDetected();
            invalidate();
        }
        if (PROFILE_DRAWING) {
            if (mDrawingProfilingStarted) {
                Debug.stopMethodTracing();
                mDrawingProfilingStarted = false;
            }
        }
    }

    private void notifyCellAdded() {
        // sendAccessEvent(R.string.lockscreen_access_pattern_cell_added);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCellAdded(mPattern);
        }
        // Disable used cells for accessibility as they get added
        if (DEBUG_A11Y) {
            Log.v(TAG, "ivnalidating root because cell was added.");
        }
        mExploreByTouchHelper.invalidateRoot();
    }

    private void notifyPatternCleared() {
        sendAccessEvent(R.string.lockscreen_access_pattern_cleared);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCleared();
        }
    }

    private void notifyPatternDetected() {
        sendAccessEvent(R.string.lockscreen_access_pattern_detected);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternDetected(mPattern);
        }
    }

    private void notifyPatternStarted() {
        sendAccessEvent(R.string.lockscreen_access_pattern_start);
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternStart();
        }
    }

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        mPatternDisplayMode = DisplayMode.Correct;
        invalidate();
    }

    private int resolveMeasured(int measureSpec, int desired) {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.max(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }

    private void sendAccessEvent(int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mContext.getString(resId);
        }
    }

    private void setPatternInProgress(boolean progress) {
        mPatternInProgress = progress;
        mExploreByTouchHelper.invalidateRoot();
    }

    private void startCellActivatedAnimation(Cell cell) {
        final CellState cellState = mCellStates[cell.row][cell.column];
        startDotAnimation(mDotSize / 2, mDotSizeActivated / 2, 96, mLinearOutSlowInInterpolator, cellState, new Runnable() {
            @Override
            public void run() {
                startDotAnimation(mDotSizeActivated / 2, mDotSize / 2, 192, mFastOutSlowInInterpolator, cellState, null, m_oDotBitmap);
            }
        }, m_oBigDotBitmap);
        startLineEndAnimation(cellState, mInProgressX, mInProgressY, getCenterXForColumn(cell.column), getCenterYForRow(cell.row));
    }

    private void startCellStateAnimationSw(final CellState cellState, final float startAlpha, final float endAlpha, final float startTranslationY, final float endTranslationY, final float startScale, final float endScale, long delay, long duration, Interpolator interpolator, final Runnable finishRunnable) {
        cellState.alpha = startAlpha;
        cellState.translationY = startTranslationY;
        cellState.radius = mDotSize / 2 * startScale;

        ValueAnimatorCompat animator = ValueAnimatorCompat.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setStartDelay(delay);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(new ValueAnimatorCompat.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimatorCompat animation) {
                float t = (float) animation.getAnimatedValue();
                cellState.alpha = (1 - t) * startAlpha + t * endAlpha;
                cellState.translationY = (1 - t) * startTranslationY + t * endTranslationY;
                cellState.radius = mDotSize / 2 * ((1 - t) * startScale + t * endScale);
                invalidate();
            }
        });
        animator.addListener(new ValueAnimatorCompat.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(ValueAnimatorCompat animation) {
                if (finishRunnable != null) {
                    finishRunnable.run();
                }
            }
        });
        animator.start();

//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
//            animator.setDuration(duration);
//            animator.setStartDelay(delay);
//            animator.setInterpolator(interpolator);
//            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
//                @Override
//                public void onAnimationUpdate(ValueAnimator animation) {
//                    float t = (float) animation.getAnimatedValue();
//                    cellState.alpha = (1 - t) * startAlpha + t * endAlpha;
//                    cellState.translationY = (1 - t) * startTranslationY + t * endTranslationY;
//                    cellState.radius = mDotSize/2 * ((1 - t) * startScale + t * endScale);
//                    invalidate();
//                }
//            });
//            animator.addListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(Animator animation) {
//                    if (finishRunnable != null) {
//                        finishRunnable.run();
//                    }
//                }
//            });
//            animator.start();
//        } else {
//            com.nineoldandroids.animation.ValueAnimator animator =
//                    com.nineoldandroids.animation.ValueAnimator.ofFloat(0f, 1f);
//            animator.setDuration(duration);
//            animator.setStartDelay(delay);
//            animator.setInterpolator(interpolator);
//            animator.addUpdateListener(new com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener() {
//                @Override
//                public void onAnimationUpdate(com.nineoldandroids.animation.ValueAnimator animation) {
//                    float t = (float) animation.getAnimatedValue();
//                    cellState.alpha = (1 - t) * startAlpha + t * endAlpha;
//                    cellState.translationY = (1 - t) * startTranslationY + t * endTranslationY;
//                    cellState.radius = mDotSize/2 * ((1 - t) * startScale + t * endScale);
//                    invalidate();
//                }
//            });
//            animator.addListener(new com.nineoldandroids.animation.ValueAnimator.AnimatorListener() {
//                @Override
//                public void onAnimationStart(com.nineoldandroids.animation.Animator animation) { }
//
//                @Override
//                public void onAnimationEnd(com.nineoldandroids.animation.Animator animation) {
//                    if (finishRunnable != null) {
//                        finishRunnable.run();
//                    }
//                }
//
//                @Override
//                public void onAnimationCancel(com.nineoldandroids.animation.Animator animation) { }
//
//                @Override
//                public void onAnimationRepeat(com.nineoldandroids.animation.Animator animation) { }
//            });
//            animator.start();
//        }
    }

    // [rexmtorres 20160401]
    // Original name: startRadiusAnimation
    // Added support for drawing a Bitmap, if set, instead of a circle for the pattern dots.
    private void startDotAnimation(float start, float end, long duration, Interpolator interpolator, final CellState state, final Runnable endRunnable, final Bitmap oBitmap) {
        ValueAnimatorCompat valueAnimator = ValueAnimatorCompat.ofFloat(start, end);
        valueAnimator.addUpdateListener(new ValueAnimatorCompat.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimatorCompat animation) {
                state.bitmapDot = oBitmap;
                state.radius = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        if (endRunnable != null) {
            valueAnimator.addListener(new ValueAnimatorCompat.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(ValueAnimatorCompat animation) {
                    endRunnable.run();
                }
            });
        }
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }

    private void startLineEndAnimation(final CellState state, final float startX, final float startY, final float targetX, final float targetY) {
        ValueAnimatorCompat valueAnimator = ValueAnimatorCompat.ofFloat(0, 1);
        valueAnimator.addUpdateListener(new ValueAnimatorCompat.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimatorCompat animation) {
                float t = (float) animation.getAnimatedValue();
                state.lineEndX = (1 - t) * startX + t * targetX;
                state.lineEndY = (1 - t) * startY + t * targetY;
                invalidate();
            }
        });
        valueAnimator.addListener(new ValueAnimatorCompat.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(ValueAnimatorCompat animation) {
                state.lineAnimator = null;
            }
        });
        valueAnimator.setInterpolator(mFastOutSlowInInterpolator);
        valueAnimator.setDuration(100);
        valueAnimator.start();
        state.lineAnimator = valueAnimator;
    }

    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {
        @SuppressWarnings({"unused", "hiding"}) // Found using reflection
        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isTactileFeedbackEnabled() {
            return mTactileFeedbackEnabled;
        }

        /**
         * Constructor called from {@link PatternLockView#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, String serializedPattern, int displayMode, boolean inputEnabled, boolean inStealthMode, boolean tactileFeedbackEnabled) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        private final String mSerializedPattern;
        private final boolean mInStealthMode;
        private final boolean mInputEnabled;
        private final boolean mTactileFeedbackEnabled;

        private final int mDisplayMode;
    }

    private final class PatternExploreByTouchHelper extends ExploreByTouchHelper {
        class VirtualViewContainer {
            CharSequence description;

            public VirtualViewContainer(CharSequence description) {
                this.description = description;
            }
        }

        public PatternExploreByTouchHelper(View forView) {
            super(forView);
        }

        ;

        @Override
        public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onPopulateAccessibilityEvent(host, event);
            if (!mPatternInProgress) {
                CharSequence contentDescription = getContext().getText(R.string.lockscreen_access_pattern_area);
                event.setContentDescription(contentDescription);
            }
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            // This must use the same hit logic for the screen to ensure consistency whether
            // accessibility is on or off.
            return getVirtualViewIdForHit(x, y);
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            if (DEBUG_A11Y) {
                Log.v(TAG, "getVisibleVirtualViews(len=" + virtualViewIds.size() + ")");
            }
            if (!mPatternInProgress) {
                return;
            }
            for (int i = VIRTUAL_BASE_VIEW_ID; i < VIRTUAL_BASE_VIEW_ID + 9; i++) {
                if (!mItems.containsKey(i)) {
                    VirtualViewContainer item = new VirtualViewContainer(getTextForVirtualView(i));
                    mItems.put(i, item);
                }
                // Add all views. As views are added to the pattern, we remove them
                // from notification by making them non-clickable below.
                virtualViewIds.add(i);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, Bundle arguments) {
            if (DEBUG_A11Y) {
                Log.v(TAG, "onPerformActionForVirtualView(id=" + virtualViewId + ", action=" + action);
            }
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_CLICK:
                    // Click handling should be consistent with
                    // onTouchEvent(). This ensures that the view works the
                    // same whether accessibility is turned on or off.
                    return onItemClicked(virtualViewId);
                default:
                    if (DEBUG_A11Y) {
                        Log.v(TAG, "*** action not handled in " + "onPerformActionForVirtualView(viewId=" + virtualViewId + "action=" + action + ")");
                    }
            }
            return false;
        }

        @Override
        protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
            if (DEBUG_A11Y) {
                Log.v(TAG, "onPopulateEventForVirtualView(" + virtualViewId + ")");
            }
            // Announce this view
            if (mItems.containsKey(virtualViewId)) {
                CharSequence contentDescription = mItems.get(virtualViewId).description;
                event.getText().add(contentDescription);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat node) {
            if (DEBUG_A11Y) {
                Log.v(TAG, "onPopulateNodeForVirtualView(view=" + virtualViewId + ")");
            }
            // Node and event text and content descriptions are usually
            // identical, so we'll use the exact same string as before.
            node.setText(getTextForVirtualView(virtualViewId));
            node.setContentDescription(getTextForVirtualView(virtualViewId));
            if (mPatternInProgress) {
                node.setFocusable(true);
                if (isClickable(virtualViewId)) {
                    // Mark this node of interest by making it clickable.
                    node.addAction(AccessibilityActionCompat.ACTION_CLICK);
                    node.setClickable(isClickable(virtualViewId));
                }
            }
            // Compute bounds for this object
            final Rect bounds = getBoundsForVirtualView(virtualViewId);
            if (DEBUG_A11Y) {
                Log.v(TAG, "bounds:" + bounds.toString());
            }
            node.setBoundsInParent(bounds);
        }

        boolean onItemClicked(int index) {
            if (DEBUG_A11Y) {
                Log.v(TAG, "onItemClicked(" + index + ")");
            }
            // Since the item's checked state is exposed to accessibility
            // services through its AccessibilityNodeInfo, we need to invalidate
            // the item's virtual view. At some point in the future, the
            // framework will obtain an updated version of the virtual view.
            invalidateVirtualView(index);
            // We need to let the framework know what type of event
            // happened. Accessibility services may use this event to provide
            // appropriate feedback to the user.
            sendEventForVirtualView(index, AccessibilityEvent.TYPE_VIEW_CLICKED);
            return true;
        }

        private Rect getBoundsForVirtualView(int virtualViewId) {
            int ordinal = virtualViewId - VIRTUAL_BASE_VIEW_ID;
            final Rect bounds = mTempRect;
            final int row = ordinal / 3;
            final int col = ordinal % 3;
            final CellState cell = mCellStates[row][col];
            float centerX = getCenterXForColumn(col);
            float centerY = getCenterYForRow(row);
            float cellheight = mSquareHeight * mHitFactor * 0.5f;
            float cellwidth = mSquareWidth * mHitFactor * 0.5f;
            bounds.left = (int) (centerX - cellwidth);
            bounds.right = (int) (centerX + cellwidth);
            bounds.top = (int) (centerY - cellheight);
            bounds.bottom = (int) (centerY + cellheight);
            return bounds;
        }

        private CharSequence getTextForVirtualView(int virtualViewId) {
            final Resources res = getResources();
            return shouldSpeakPassword() ? res.getString(R.string.lockscreen_access_pattern_cell_added_verbose, virtualViewId) : res.getString(R.string.lockscreen_access_pattern_cell_added);
        }

        /**
         * Helper method to find which cell a point maps to
         * <p>
         * if there's no hit.
         *
         * @param x touch position x
         * @param y touch position y
         *
         * @return VIRTUAL_BASE_VIEW_ID+id or 0 if no view was hit
         */
        private int getVirtualViewIdForHit(float x, float y) {
            final int rowHit = getRowHit(y);
            if (rowHit < 0) {
                return ExploreByTouchHelper.INVALID_ID;
            }
            final int columnHit = getColumnHit(x);
            if (columnHit < 0) {
                return ExploreByTouchHelper.INVALID_ID;
            }
            boolean dotAvailable = mPatternDrawLookup[rowHit][columnHit];
            int dotId = (rowHit * 3 + columnHit) + VIRTUAL_BASE_VIEW_ID;
            int view = dotAvailable ? dotId : ExploreByTouchHelper.INVALID_ID;
            if (DEBUG_A11Y) {
                Log.v(TAG, "getVirtualViewIdForHit(" + x + "," + y + ") => " + view + "avail =" + dotAvailable);
            }
            return view;
        }

        private boolean isClickable(int virtualViewId) {
            // Dots are clickable if they're not part of the current pattern.
            if (virtualViewId != ExploreByTouchHelper.INVALID_ID) {
                int row = (virtualViewId - VIRTUAL_BASE_VIEW_ID) / 3;
                int col = (virtualViewId - VIRTUAL_BASE_VIEW_ID) % 3;
                return !mPatternDrawLookup[row][col];
            }
            return false;
        }

        private boolean shouldSpeakPassword() {
            // START: Copied from https://github.com/DreaminginCodeZH/PatternLock
            // HACK: Settings.Secure.getIntForUser() is hidden, so we can only use
            // Settings.Secure.getInt() instead.
            //final boolean speakPassword = Settings.Secure.getIntForUser(
            //        getContext().getContentResolver(),
            //        Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0,
            //        UserHandle.USER_CURRENT_OR_SELF) != 0;
            // Settings.Secure.getInt() will return the supplied default value 0 if not found, so it
            // is OK to use the Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD from future.
            @SuppressLint("InlinedApi") final boolean speakPassword = Settings.Secure.getInt(getContext().getContentResolver(), Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0) != 0;
            @SuppressWarnings("deprecation") final boolean hasHeadphones = mAudioManager != null && (mAudioManager.isWiredHeadsetOn() || mAudioManager.isBluetoothA2dpOn());
            return speakPassword || hasHeadphones;
            // END: Copied from https://github.com/DreaminginCodeZH/PatternLock
        }

        private HashMap<Integer, VirtualViewContainer> mItems = new HashMap<>();
        private Rect mTempRect = new Rect();
    }

    private static final String TAG = "PatternLockView";

    private static final boolean DEBUG_A11Y = false;
    private static final boolean PROFILE_DRAWING = false;
    /**
     * This can be used to avoid updating the display for very small motions or noisy panels.
     * It didn't seem to have much impact on the devices tested, so currently set to 0.
     */
    private static final float DRAG_THRESHHOLD = 0.0f;

    private static final int ASPECT_LOCK_HEIGHT = 2; // Fixed height; width will be minimum of (w,h)
    private static final int ASPECT_LOCK_WIDTH = 1; // Fixed width; height will be minimum of (w,h)
    // Aspect to use when rendering this view
    private static final int ASPECT_SQUARE = 0; // View will be the minimum of width/height
    /**
     * How many milliseconds we spend animating each circle of a lock pattern
     * if the animating mode is set.  The entire animation should take this
     * constant * the length of the pattern to complete.
     */
    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;
    private static final int VIRTUAL_BASE_VIEW_ID = 1;
    private final ArrayList<Cell> mPattern = new ArrayList<>(9);
    private final CellState[][] mCellStates;
    private final Interpolator mFastOutSlowInInterpolator;
    private final Interpolator mLinearOutSlowInInterpolator;
    private final Paint mPaint = new Paint();
    private final Paint mPathPaint = new Paint();
    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();
    private final Rect mTmpInvalidateRect = new Rect();
    /**
     * Lookup table for the circles of the pattern we are currently drawing.
     * This will be the cells of the complete pattern unless we are animating,
     * in which case we use this to hold the cells we are drawing for the in
     * progress animation.
     */
    private final boolean[][] mPatternDrawLookup = new boolean[3][3];
    private final int mDotSize;
    private final int mDotSizeActivated;
    private final int mPathWidth;
    private AccessibilityManager mAccessibilityManager;
    private AudioManager mAudioManager;
    private Bitmap m_oBigDotBitmap;
    // [rexmtorres 20160401] If set, replaces the pattern dots with the specified bitmap.
    private Bitmap m_oDotBitmap;
    private Context mContext;
    private DisplayMode mPatternDisplayMode = DisplayMode.Correct;
    private OnPatternListener mOnPatternListener;
    private PatternExploreByTouchHelper mExploreByTouchHelper;
    private boolean mDrawingProfilingStarted = false;
    private boolean mEnableHapticFeedback = true;
    private boolean mInStealthMode = false;
    private boolean mInputEnabled = true;
    private boolean mPatternInProgress = false;
    private float mHitFactor = 0.6f;
    /**
     * the in progress point:
     * - during interaction: where the user's finger is
     * - during animation: the current tip of the animating line
     */
    private float mInProgressX = -1;
    private float mInProgressY = -1;
    private float mSquareHeight;
    private float mSquareWidth;
    private int mAspect;
    private int mErrorColor;
    private int mRegularColor;
    private int mSuccessColor;
    private long mAnimatingPeriodStart;
}
