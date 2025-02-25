/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.graphics.drawable.Drawable;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextClock; 
import android.widget.AnalogClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.policy.SystemBarUtils;
import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.battery.BatteryMeterView;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.VariableDateView;
import com.android.systemui.util.LargeScreenUtils;

import java.util.List;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout implements
        View.OnClickListener, View.OnLongClickListener {

    public static final String SEARCH_PROVIDER_SETTINGS_KEY = "SEARCH_PROVIDER_PACKAGE_NAME";
    public static final String GSA_PACKAGE = "com.google.android.googlequicksearchbox";

    private boolean mExpanded;
    private boolean mQsDisabled;

    public boolean mQsTileTint;
    public boolean mBlurStyleEnabled;
    public boolean mIsBlurCombinedEnabled;

    @Nullable
    private TouchAnimator mAlphaAnimator;
    @Nullable
    private TouchAnimator mTranslationAnimator;
    @Nullable
    private TouchAnimator mIconsAlphaAnimator;
    private TouchAnimator mIconsAlphaAnimatorFixed;

    protected QuickQSPanel mHeaderQsPanel;
    private View mDatePrivacyView;
    // DateView next to clock. Visible on QQS
    private View mStatusIconsView;
    private View mContainer;
    
    // jr clock
    private TextClock mJrClock;
    private LinearLayout mJrDateContainer;

    private LinearLayout mSystemIconContainer;

    //private View mQSCarriers;
    private ViewGroup mClockContainer;
    private Space mDatePrivacySeparator;
    private View mClockIconsSeparator;
    private boolean mShowClockIconsSeparator;
    private View mRightLayout;
    private View mDateContainer;
    private View mPrivacyContainer;
    private View mNadContainer;
    private ImageView mNadShortcut;
    private ImageView mSearch;

    private BatteryMeterView mBatteryRemainingIcon;
    private StatusIconContainer mIconContainer;
    private View mPrivacyChip;

    @Nullable
    private TintedIconManager mTintedIconManager;
    @Nullable
    private QSExpansionPathInterpolator mQSExpansionPathInterpolator;
    private StatusBarContentInsetsProvider mInsetsProvider;

    private int mRoundedCornerPadding = 0;
    private int mWaterfallTopInset;
    private int mCutOutPaddingLeft;
    private int mCutOutPaddingRight;
    private float mKeyguardExpansionFraction;
    private int mTextColorPrimary = Color.TRANSPARENT;
    private int mTopViewMeasureHeight;

    @NonNull
    private List<String> mRssiIgnoredSlots = List.of();
    private boolean mIsSingleCarrier;

    private boolean mHasCenterCutout;
    private boolean mConfigShowBatteryEstimate;

    private boolean mUseCombinedQSHeader;
    private final ActivityStarter mActivityStarter;
    private final Vibrator mVibrator;
    
    private int colorActive, colorInactive, colorActiveAccent, colorActiveAlpha, colorInactiveAlpha, colorNonActive, colorBlurInactiveAlpha, colorCombinedBlurInactiveAlpha;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivityStarter = Dependency.get(ActivityStarter.class);
        mVibrator = context.getSystemService(Vibrator.class);

    }

    /**
     * How much the view containing the clock and QQS will translate down when QS is fully expanded.
     *
     * This matches the measured height of the view containing the date and privacy icons.
     */
    public int getOffsetTranslation() {
        return mTopViewMeasureHeight;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mDatePrivacyView = findViewById(R.id.quick_status_bar_date_privacy);
        mStatusIconsView = findViewById(R.id.quick_qs_status_icons);
        //mQSCarriers = findViewById(R.id.carrier_group);
        mContainer = findViewById(R.id.qs_container);
        mIconContainer = findViewById(R.id.statusIcons);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mClockIconsSeparator = findViewById(R.id.separator);
        mRightLayout = findViewById(R.id.rightLayout);
        mDateContainer = findViewById(R.id.date_container);
        mPrivacyContainer = findViewById(R.id.privacy_container);
        
        // jr clock
        mJrDateContainer = findViewById(R.id.jr_date_container);
        mJrDateContainer.setOnClickListener(this);
        mClockContainer = findViewById(R.id.clock_container);
        mClockContainer.setOnClickListener(this);
        mClockContainer.setOnLongClickListener(this);
        mJrClock = findViewById(R.id.jr_clock);

        mSearch = findViewById(R.id.search_shortcut);
        mSearch.setOnClickListener(this);
        mNadContainer = findViewById(R.id.nad_container);
        mNadContainer.setOnClickListener(this);
        mNadShortcut = findViewById(R.id.nad_shortcut);
        mSystemIconContainer = findViewById(R.id.icon_container);
        
        mDatePrivacySeparator = findViewById(R.id.space);
        // Tint for the battery icons are handled in setupHost()
        mBatteryRemainingIcon = findViewById(R.id.batteryRemainingIcon);

        updateResources();
        Configuration config = mContext.getResources().getConfiguration();
        setDatePrivacyContainersWidth(config.orientation == Configuration.ORIENTATION_LANDSCAPE);

        mBatteryRemainingIcon.setIsQsHeader(true);
        // QS will always show the estimate, and BatteryMeterView handles the case where
        // it's unavailable or charging
        mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);

        //Identify color source
        colorActive = Utils.getColorAttrDefaultColor(mContext,
            com.android.internal.R.attr.colorAccentPrimary);
        colorInactive = Utils.getColorAttrDefaultColor(mContext,
            R.attr.offStateColor);
        colorActiveAccent = Utils.getColorAttrDefaultColor(mContext,
            com.android.internal.R.attr.colorAccent);
        colorActiveAlpha = Utils.applyAlpha(0.2f, Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorAccent));
        colorInactiveAlpha = Utils.applyAlpha(0.2f, Utils.getColorAttrDefaultColor(mContext, R.attr.offStateColor));
        colorNonActive =
            Utils.getColorAttrDefaultColor(mContext, com.android.internal.R.attr.textColorPrimaryInverse);
        colorBlurInactiveAlpha = Utils.applyAlpha(0.6f, Utils.getColorAttrDefaultColor(mContext, R.attr.offStateColor));
        colorCombinedBlurInactiveAlpha = Utils.applyAlpha(0.4f, Utils.getColorAttrDefaultColor(mContext, R.attr.offStateColor));

        mIconsAlphaAnimatorFixed = new TouchAnimator.Builder()
                //.addFloat(mIconContainer, "alpha", 0, 1)
                .addFloat(mNadContainer, "alpha", 0, 1)
                .build();
    }

    void onAttach(TintedIconManager iconManager,
            QSExpansionPathInterpolator qsExpansionPathInterpolator,
            List<String> rssiIgnoredSlots,
            StatusBarContentInsetsProvider insetsProvider,
            boolean useCombinedQSHeader) {
        mUseCombinedQSHeader = useCombinedQSHeader;
        mTintedIconManager = iconManager;
        mRssiIgnoredSlots = rssiIgnoredSlots;
        mInsetsProvider = insetsProvider;
        int fillColor = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.textColorPrimary);

        // Set the correct tint for the status icons so they contrast
        iconManager.setTint(fillColor);

        mQSExpansionPathInterpolator = qsExpansionPathInterpolator;
        updateAnimators();
    }

    void setIsSingleCarrier(boolean isSingleCarrier) {
        mIsSingleCarrier = isSingleCarrier;
        updateAlphaAnimator();
    }

    public QuickQSPanel getHeaderQsPanel() {
        return mHeaderQsPanel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mStatusIconsView.getMeasuredHeight() != mTopViewMeasureHeight) {
            mTopViewMeasureHeight = mStatusIconsView.getMeasuredHeight();
            updateAnimators();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
        setDatePrivacyContainersWidth(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    @Nullable
    public static String getSearchWidgetPackageName(@NonNull Context context) {
        String providerPkg = Settings.Global.getString(context.getContentResolver(),
                SEARCH_PROVIDER_SETTINGS_KEY);
        if (providerPkg == null) {
            SearchManager searchManager = context.getSystemService(SearchManager.class);
            ComponentName componentName = searchManager.getGlobalSearchActivity();
            if (componentName != null) {
                providerPkg = searchManager.getGlobalSearchActivity().getPackageName();
            }
            if (providerPkg == null && isGSAEnabled(context)) {
                providerPkg = GSA_PACKAGE;
            }
        }
        return providerPkg;
    }

    public static boolean isGSAEnabled(Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(GSA_PACKAGE, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mClockContainer) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mJrDateContainer) {
            final Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(System.currentTimeMillis()));
            final Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            mActivityStarter.postStartActivityDismissingKeyguard(todayIntent, 0);
        } else if (v == mSearch) {
        	String searchPackage = getSearchWidgetPackageName(mContext);
            mActivityStarter.startActivity(new Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK).setPackage(searchPackage), true);
        } else if (v == mNadContainer) {
            final Intent nIntent = new Intent();
            nIntent.setComponent(new ComponentName("com.android.settings",
                    "com.android.settings.Settings$NusantaraWingsActivity"));
            mActivityStarter.startActivity(nIntent, true /* dismissShade */);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == mClockContainer || v == mJrDateContainer) {
            final Intent nIntent = new Intent(Intent.ACTION_MAIN);
            nIntent.setClassName("com.android.settings",
                    "com.android.settings.Settings$DateTimeSettingsActivity");
            mActivityStarter.startActivity(nIntent, true /* dismissShade */);
            mVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            return true;
        }
        return false;
    }

    private void setDatePrivacyContainersWidth(boolean landscape) {
    	/**
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mDateContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mDateContainer.setLayoutParams(lp);
        */

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mPrivacyContainer.getLayoutParams();
        lp.width = landscape ? WRAP_CONTENT : 0;
        lp.weight = landscape ? 0f : 1f;
        mPrivacyContainer.setLayoutParams(lp);
    }

    private void updateBatteryMode() {
        if (mConfigShowBatteryEstimate) {
            mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);
        } else {
            mBatteryRemainingIcon.setPercentShowMode(BatteryMeterView.MODE_ON);
        }
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        boolean gone = largeScreenHeaderActive || mUseCombinedQSHeader || mQsDisabled;
        mStatusIconsView.setVisibility(gone ? View.GONE : View.VISIBLE);
        mDatePrivacyView.setVisibility(gone ? View.GONE : View.VISIBLE);

        mConfigShowBatteryEstimate = resources.getBoolean(R.bool.config_showBatteryEstimateQSBH);

        mRoundedCornerPadding = resources.getDimensionPixelSize(
                R.dimen.rounded_corner_content_padding);

        int qsOffsetHeight = SystemBarUtils.getQuickQsOffsetHeight(mContext);

        /**
        mDatePrivacyView.getLayoutParams().height =
                Math.max(qsOffsetHeight, mDatePrivacyView.getMinimumHeight());
        mDatePrivacyView.setLayoutParams(mDatePrivacyView.getLayoutParams());
        */

        mStatusIconsView.getLayoutParams().height =
                Math.max(qsOffsetHeight, mStatusIconsView.getMinimumHeight());
        mStatusIconsView.setLayoutParams(mStatusIconsView.getLayoutParams());

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = mStatusIconsView.getLayoutParams().height;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        int textColor = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
        if (textColor != mTextColorPrimary) {
            int textColorSecondary = Utils.getColorAttrDefaultColor(mContext,
                    android.R.attr.textColorSecondary);
            mTextColorPrimary = textColor;
            if (mTintedIconManager != null) {
                mTintedIconManager.setTint(textColor);
            }
            mBatteryRemainingIcon.updateColors(mTextColorPrimary, textColorSecondary,
                    mTextColorPrimary);
        }

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        qqsLP.topMargin = largeScreenHeaderActive || !mUseCombinedQSHeader ? mContext.getResources()
                .getDimensionPixelSize(R.dimen.qqs_layout_margin_top) : qsOffsetHeight;
        mHeaderQsPanel.setLayoutParams(qqsLP);

        updateBatteryMode();
        updateHeadersPadding();
        updateAnimators();

    }

    private void updateAnimators() {
        if (mUseCombinedQSHeader) {
            mTranslationAnimator = null;
            return;
        }
        updateAlphaAnimator();
        int offset = mTopViewMeasureHeight;

        mTranslationAnimator = new TouchAnimator.Builder()
                .addFloat(mHeaderQsPanel, "translationY", 0, offset)
                .setInterpolator(mQSExpansionPathInterpolator != null
                        ? mQSExpansionPathInterpolator.getYInterpolator()
                        : null)
                .build();
    }

    private void updateAlphaAnimator() {
        if (mUseCombinedQSHeader) {
            mAlphaAnimator = null;
            return;
        }
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mJrDateContainer, "translationY", 0, 30f)
                .addFloat(mJrClock, "scaleX", 1f , 1.5f)
                .addFloat(mJrClock, "scaleY", 1f, 1.5f)
                .addFloat(mJrClock, "translationX", 1f, 35f)
                .addFloat(mSearch, "alpha", 0, 1f)
                .addFloat(mSystemIconContainer, "translationY", 0, 30f)
                .setListener(new TouchAnimator.ListenerAdapter() {
                    @Override
                    public void onAnimationAtEnd() {
                        super.onAnimationAtEnd();
                        if (!mIsSingleCarrier) {
                        }
                    }

                    @Override
                    public void onAnimationStarted() {
                        setSeparatorVisibility(false);
                        if (!mIsSingleCarrier) {
                        }    
                    }

                    @Override
                    public void onAnimationAtStart() {
                        super.onAnimationAtStart();
                        setSeparatorVisibility(mShowClockIconsSeparator);
                    }
                });
        mAlphaAnimator = builder.build();
    }

    void setChipVisibility(boolean visibility) {
        if (visibility) {
            // Animates the icons and battery indicator from alpha 0 to 1, when the chip is visible
            mIconsAlphaAnimator = mIconsAlphaAnimatorFixed;
            mIconsAlphaAnimator.setPosition(mKeyguardExpansionFraction);
        } else {
            mIconsAlphaAnimator = null;
            //mIconContainer.setAlpha(1);
            mNadContainer.setAlpha(1);
        }

    }

    /** */
    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        quickQSPanelController.setExpanded(expanded);
        updateEverything();
        if (expanded && mPrivacyChip.getVisibility() == View.VISIBLE) {
            mNadContainer.setAlpha(0);
        }
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;

        if (mAlphaAnimator != null) {
            mAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mTranslationAnimator != null) {
            mTranslationAnimator.setPosition(keyguardExpansionFraction);
        }
        if (mIconsAlphaAnimator != null && mPrivacyChip.getVisibility() == View.GONE) {
            mIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
        // If forceExpanded (we are opening QS from lockscreen), the animators have been set to
        // position = 1f.
        if (forceExpanded) {
            setTranslationY(panelTranslationY);
        } else {
            setTranslationY(0);
        }

        mKeyguardExpansionFraction = keyguardExpansionFraction;
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mStatusIconsView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    public void updateColors(boolean qsTileTint) {
        mQsTileTint = qsTileTint;
    	boolean isBlurEnable = mBlurStyleEnabled;
        int alphaBlur = mIsBlurCombinedEnabled ? 100 : 153;
        int alpha = qsTileTint || isBlurEnable ? (isBlurEnable ? alphaBlur : 51) : 255;
        int colorAlphaBlur = mIsBlurCombinedEnabled ? colorCombinedBlurInactiveAlpha : colorBlurInactiveAlpha;
        mNadShortcut.setColorFilter(qsTileTint ? colorActiveAccent : colorNonActive);
        Drawable nadBg = mNadContainer.getBackground();
        if (nadBg != null) nadBg.setAlpha(qsTileTint || isBlurEnable ? (isBlurEnable ? alphaBlur : 51) : 255);
        mSearch.setBackground(mContext.getDrawable(R.drawable.qs_clock_bg));
        int tintColor = qsTileTint || isBlurEnable ? (isBlurEnable ? colorAlphaBlur : colorInactiveAlpha) : colorInactive;
        final Drawable bg = mSearch.getBackground();
        if (bg != null) bg.setTint(tintColor);
    }

    public void updateAlpha(boolean isBlurEnable) {
    	mBlurStyleEnabled = isBlurEnable;
        updateColors(mQsTileTint);
    }

    public void updateIsCombined(boolean isCombineEnable) {
        mIsBlurCombinedEnabled = isCombineEnable;
        updateAlpha(mBlurStyleEnabled);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        // Handle padding of the views
        DisplayCutout cutout = insets.getDisplayCutout();

        Pair<Integer, Integer> sbInsets = mInsetsProvider
                .getStatusBarContentInsetsForCurrentRotation();
        boolean hasCornerCutout = mInsetsProvider.currentRotationHasCornerCutout();

        mDatePrivacyView.setPadding(sbInsets.first, 0, sbInsets.second, 0);
        mStatusIconsView.setPadding(sbInsets.first, 0, sbInsets.second, 0);
        
        LinearLayout.LayoutParams lpC =
                (LinearLayout.LayoutParams) mClockContainer.getLayoutParams();
        lpC.width = 0;
        lpC.weight = 1f;
        mClockContainer.setLayoutParams(lpC);
        
        LinearLayout.LayoutParams lpR=
                (LinearLayout.LayoutParams) mRightLayout.getLayoutParams();
        lpR.width = 0;
        lpR.weight = 1f;
        mRightLayout.setLayoutParams(lpR);
        
        LinearLayout.LayoutParams datePrivacySeparatorLayoutParams =
                (LinearLayout.LayoutParams) mDatePrivacySeparator.getLayoutParams();
        LinearLayout.LayoutParams mClockIconsSeparatorLayoutParams =
                (LinearLayout.LayoutParams) mClockIconsSeparator.getLayoutParams();
        if (cutout != null) {
            Rect topCutout = cutout.getBoundingRectTop();
            if (topCutout.isEmpty() || hasCornerCutout) {
                datePrivacySeparatorLayoutParams.width = 0;
                mDatePrivacySeparator.setVisibility(View.GONE);
                mClockIconsSeparatorLayoutParams.width = topCutout.width();
                setSeparatorVisibility(false);
                mShowClockIconsSeparator = false;
                mHasCenterCutout = false;
            } else {
                datePrivacySeparatorLayoutParams.width = topCutout.width();
                mDatePrivacySeparator.setVisibility(View.VISIBLE);
                mClockIconsSeparatorLayoutParams.width = topCutout.width();
                mShowClockIconsSeparator = true;
                setSeparatorVisibility(mKeyguardExpansionFraction == 0f);
                mHasCenterCutout = true;
            }
        }
        mDatePrivacySeparator.setLayoutParams(datePrivacySeparatorLayoutParams);
        mClockIconsSeparator.setLayoutParams(mClockIconsSeparatorLayoutParams);
        mCutOutPaddingLeft = sbInsets.first;
        mCutOutPaddingRight = sbInsets.second;
        mWaterfallTopInset = cutout == null ? 0 : cutout.getWaterfallInsets().top;

        updateBatteryMode();
        updateHeadersPadding();
        return super.onApplyWindowInsets(insets);
    }

    /**
     * Sets the visibility of the separator between clock and icons.
     *
     * This separator is "visible" when there is a center cutout, to block that space. In that
     * case, the clock and the layout on the right (containing the icons and the battery meter) are
     * set to weight 1 to take the available space.
     * @param visible whether the separator between clock and icons should be visible.
     */
    private void setSeparatorVisibility(boolean visible) {
        int newVisibility = visible ? View.VISIBLE : View.GONE;
        if (mClockIconsSeparator.getVisibility() == newVisibility) return;

        mClockIconsSeparator.setVisibility(visible ? View.VISIBLE : View.GONE);
        
        /**
        mQSCarriers.setVisibility(visible ? View.GONE : View.VISIBLE);

        LinearLayout.LayoutParams lp =
                (LinearLayout.LayoutParams) mClockContainer.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mClockContainer.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mRightLayout.getLayoutParams();
        lp.width = visible ? 0 : WRAP_CONTENT;
        lp.weight = visible ? 1f : 0f;
        mRightLayout.setLayoutParams(lp);
        */
    }
    
    private void updateHeadersPadding() {
        setContentMargins(mDatePrivacyView, 0, 0);
        setContentMargins(mStatusIconsView, 0, 0);
        int paddingLeft = 0;
        int paddingRight = 0;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        int leftMargin = lp.leftMargin;
        int rightMargin = lp.rightMargin;

        // The clock might collide with cutouts, let's shift it out of the way.
        // We only do that if the inset is bigger than our own padding, since it's nicer to
        // align with
        if (mCutOutPaddingLeft > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingLeft, mRoundedCornerPadding);
            paddingLeft = Math.max(cutoutPadding - leftMargin, 0);
        }
        if (mCutOutPaddingRight > 0) {
            // if there's a cutout, let's use at least the rounded corner inset
            int cutoutPadding = Math.max(mCutOutPaddingRight, mRoundedCornerPadding);
            paddingRight = Math.max(cutoutPadding - rightMargin, 0);
        }

        mDatePrivacyView.setPadding(paddingLeft,
                mWaterfallTopInset,
                paddingRight,
                0);
        mStatusIconsView.setPadding(paddingLeft,
                mWaterfallTopInset,
                paddingRight,
                0);
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    /**
     * Scroll the headers away.
     *
     * @param scrollY the scroll of the QSPanel container
     */
    public void setExpandedScrollAmount(int scrollY) {
        mStatusIconsView.setScrollY(scrollY);
        mDatePrivacyView.setScrollY(scrollY);
    }
}
