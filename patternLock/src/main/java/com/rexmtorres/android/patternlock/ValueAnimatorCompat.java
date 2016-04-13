/*
 * Copyright (c) 2016.
 *
 * Rex M. Torres <rexmtorres@gmail.com>
 */

package com.rexmtorres.android.patternlock;

import android.os.Build;
import android.view.animation.Interpolator;

/**
 * This is a hack on <code>ValueAnimator</code> to provide support prior to Honeycomb (API level 11),
 * up to as early as Eclair (API level 7).
 * <p>
 * This simply wraps {@link android.animation.ValueAnimator} and
 * {@link com.nineoldandroids.animation.ValueAnimator} (from
 * <a href='http://nineoldandroids.com/'>NineOldAndroids</a> by Jake Wharton) together,
 * calling the appropriate implementation based on the API level:
 * <ul>
 * <li>{@link com.nineoldandroids.animation.ValueAnimator} for use with API level 7 - 10
 * <li>{@link android.animation.ValueAnimator} for use with API level 11 and up
 * </ul>
 * <p>
 * This is NOT an implementation of {@link android.support.v4.animation.ValueAnimatorCompat}.
 * <p>
 * Created by Rex M. Torres on 2016.04.03.
 */
class ValueAnimatorCompat {
    /**
     * <p>An animation listener receives notifications from an animation.
     * Notifications indicate animation related events, such as the end or the
     * repetition of the animation.</p>
     */
    public interface AnimatorListener {
        /**
         * <p>Notifies the cancellation of the animation. This callback is not invoked
         * for animations with repeat count set to INFINITE.</p>
         *
         * @param animation The animation which was canceled.
         */
        void onAnimationCancel(ValueAnimatorCompat animation);

        /**
         * <p>Notifies the end of the animation. This callback is not invoked
         * for animations with repeat count set to INFINITE.</p>
         *
         * @param animation The animation which reached its end.
         */
        void onAnimationEnd(ValueAnimatorCompat animation);

        /**
         * <p>Notifies the repetition of the animation.</p>
         *
         * @param animation The animation which was repeated.
         */
        void onAnimationRepeat(ValueAnimatorCompat animation);
        /**
         * <p>Notifies the start of the animation.</p>
         *
         * @param animation The started animation.
         */
        void onAnimationStart(ValueAnimatorCompat animation);
    }

    /**
     * Implementors of this interface can add themselves as update listeners
     * to an <code>ValueAnimatorCompat</code> instance to receive callbacks on every animation
     * frame, after the current frame's values have been calculated for that
     * <code>ValueAnimatorCompat</code>.
     */
    public interface AnimatorUpdateListener {
        /**
         * <p>Notifies the occurrence of another frame of the animation.</p>
         *
         * @param animation The animation which was repeated.
         */
        void onAnimationUpdate(ValueAnimatorCompat animation);
    }

    /**
     * This is a no-op implementation of {@link AnimatorListener} to be used as a convenience
     * class if you don't need to implement all the methods of {@link AnimatorListener}.
     */
    public abstract static class AnimatorListenerAdapter implements AnimatorListener {
        @Override
        public void onAnimationCancel(ValueAnimatorCompat animation) {
        }

        @Override
        public void onAnimationEnd(ValueAnimatorCompat animation) {
        }

        @Override
        public void onAnimationRepeat(ValueAnimatorCompat animation) {
        }

        @Override
        public void onAnimationStart(ValueAnimatorCompat animation) {
        }
    }

    /**
     * Constructs and returns a ValueAnimatorCompat that animates between float values. A single
     * value implies that that value is the one being animated to. However, this is not typically
     * useful in a ValueAnimatorCompat object because there is no way for the object to determine the
     * starting value for the animation (unlike ObjectAnimator, which can derive that value
     * from the target object and property being animated). Therefore, there should typically
     * be two or more values.
     *
     * @param values A set of values that the animation will animate between over time.
     *
     * @return A ValueAnimatorCompat object that is set up to animate between the given values.
     */
    public static ValueAnimatorCompat ofFloat(float... values) {
        ValueAnimatorCompat anim = new ValueAnimatorCompat();
        anim.setFloatValues(values);
        return anim;
    }

    public ValueAnimatorCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator = new android.animation.ValueAnimator();
        } else {
            mOldAnimator = new com.nineoldandroids.animation.ValueAnimator();
        }
    }

    /**
     * Adds a listener to the set of listeners that are sent events through the life of an
     * animation, such as start, repeat, and end.
     *
     * @param listener the listener to be added to the current set of listeners for this animation.
     */
    public void addListener(final AnimatorListener listener) {
        if (listener == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            android.animation.ValueAnimator.AnimatorListener newListener = new android.animation.ValueAnimator.AnimatorListener() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {
                    listener.onAnimationStart(ValueAnimatorCompat.this);
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    listener.onAnimationEnd(ValueAnimatorCompat.this);
                }

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    listener.onAnimationCancel(ValueAnimatorCompat.this);
                }

                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {
                    listener.onAnimationRepeat(ValueAnimatorCompat.this);
                }
            };

            mNewAnimator.addListener(newListener);
        } else {
            com.nineoldandroids.animation.ValueAnimator.AnimatorListener oldListener = new com.nineoldandroids.animation.ValueAnimator.AnimatorListener() {
                @Override
                public void onAnimationStart(com.nineoldandroids.animation.Animator animation) {
                    listener.onAnimationStart(ValueAnimatorCompat.this);
                }

                @Override
                public void onAnimationEnd(com.nineoldandroids.animation.Animator animation) {
                    listener.onAnimationEnd(ValueAnimatorCompat.this);
                }

                @Override
                public void onAnimationCancel(com.nineoldandroids.animation.Animator animation) {
                    listener.onAnimationCancel(ValueAnimatorCompat.this);
                }

                @Override
                public void onAnimationRepeat(com.nineoldandroids.animation.Animator animation) {
                    listener.onAnimationRepeat(ValueAnimatorCompat.this);
                }
            };

            mOldAnimator.addListener(oldListener);
        }
    }

    /**
     * Adds a listener to the set of listeners that are sent update events through the life of
     * an animation. This method is called on all listeners for every frame of the animation,
     * after the values for the animation have been calculated.
     *
     * @param listener the listener to be added to the current set of listeners for this animation.
     */
    public void addUpdateListener(final AnimatorUpdateListener listener) {
        if (listener == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            android.animation.ValueAnimator.AnimatorUpdateListener newListener = new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                    listener.onAnimationUpdate(ValueAnimatorCompat.this);
                }
            };

            mNewAnimator.addUpdateListener(newListener);
        } else {
            com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener oldListener = new com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(com.nineoldandroids.animation.ValueAnimator animation) {
                    listener.onAnimationUpdate(ValueAnimatorCompat.this);
                }
            };

            mOldAnimator.addUpdateListener(oldListener);
        }
    }

    public void cancel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator.cancel();
        } else {
            mOldAnimator.cancel();
        }
    }

    /**
     * The most recent value calculated by this <code>ValueAnimatorCompat</code> when there is just one
     * property being animated. This value is only sensible while the animation is running. The main
     * purpose for this read-only property is to retrieve the value from the <code>ValueAnimatorCompat</code>
     * during a call to {@link AnimatorUpdateListener#onAnimationUpdate(ValueAnimatorCompat)}, which
     * is called during each animation frame, immediately after the value is calculated.
     *
     * @return animatedValue The value most recently calculated by this <code>ValueAnimatorCompat</code> for
     * the single property being animated. If there are several properties being animated
     * (specified by several PropertyValuesHolder objects in the constructor), this function
     * returns the animated value for the first of those objects.
     */
    public Object getAnimatedValue() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return mNewAnimator.getAnimatedValue();
        } else {
            return mOldAnimator.getAnimatedValue();
        }
    }

    /**
     * Sets the length of the animation. The default duration is 300 milliseconds.
     *
     * @param duration The length of the animation, in milliseconds. This value cannot
     *                 be negative.
     */
    public void setDuration(long duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator.setDuration(duration);
        } else {
            mOldAnimator.setDuration(duration);
        }
    }

    /**
     * Sets float values that will be animated between. A single
     * value implies that that value is the one being animated to. However, this is not typically
     * useful in a ValueAnimatorCompat object because there is no way for the object to determine the
     * starting value for the animation (unlike ObjectAnimator, which can derive that value
     * from the target object and property being animated). Therefore, there should typically
     * be two or more values.
     * <p/>
     * <p>If there are already multiple sets of values defined for this ValueAnimatorCompat via more
     * than one PropertyValuesHolder object, this method will set the values for the first
     * of those objects.</p>
     *
     * @param values A set of values that the animation will animate between over time.
     */
    public void setFloatValues(float... values) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator.setFloatValues(values);
        } else {
            mOldAnimator.setFloatValues(values);
        }
    }

    /**
     * The time interpolator used in calculating the elapsed fraction of this animation. The
     * interpolator determines whether the animation runs with linear or non-linear motion,
     * such as acceleration and deceleration. The default value is
     * {@link android.view.animation.AccelerateDecelerateInterpolator}
     *
     * @param value the interpolator to be used by this animation. A value of <code>null</code>
     *              will result in linear interpolation.
     */
    public void setInterpolator(/*Time*/Interpolator value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator.setInterpolator(value);
        } else {
            mOldAnimator.setInterpolator(value);
        }
    }

    /**
     * The amount of time, in milliseconds, to delay starting the animation after
     * {@link #start()} is called.
     *
     * @param startDelay The amount of the delay, in milliseconds
     */
    public void setStartDelay(long startDelay) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator.setStartDelay(startDelay);
        } else {
            mOldAnimator.setStartDelay(startDelay);
        }
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mNewAnimator.start();
        } else {
            mOldAnimator.start();
        }
    }

    private android.animation.ValueAnimator mNewAnimator;
    private com.nineoldandroids.animation.ValueAnimator mOldAnimator;
}
