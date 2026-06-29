/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.rendering.views.interstitial;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.core.R;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w390dp-h844dp-mdpi")
public class DaroFullscreenChromeViewTest {

    private Activity activity;
    private DaroFullscreenChromeView chromeView;

    @Before
    public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        chromeView = new DaroFullscreenChromeView(activity);
        activity.setContentView(chromeView);
        chromeView.layout(0, 0, dp(390), dp(844));
    }

    @Test
    public void init_HidesActionControlsUntilRendererStateArrives() {
        assertEquals(View.GONE, chromeView.findViewById(R.id.iv_close_interstitial).getVisibility());
        assertEquals(View.GONE, chromeView.findViewById(R.id.iv_skip).getVisibility());
        assertEquals(View.GONE, chromeView.findViewById(R.id.tv_learn_more).getVisibility());
    }

    @Test
    public void setSafeAreaInsets_UsesFigmaTransformOffsets() {
        chromeView.setSafeAreaInsets(dp(54), dp(34));

        View soundButton = chromeView.findViewById(R.id.iv_sound_interstitial);
        FrameLayout.LayoutParams soundParams = (FrameLayout.LayoutParams) soundButton.getLayoutParams();
        assertEquals(dp(16), soundParams.leftMargin);
        assertEquals(dp(70), soundParams.topMargin);
        assertEquals(dp(36), soundParams.width);
        assertEquals(dp(36), soundParams.height);

        View progress = chromeView.findViewById(R.id.rl_count_down);
        FrameLayout.LayoutParams progressParams = (FrameLayout.LayoutParams) progress.getLayoutParams();
        assertEquals(dp(16), progressParams.leftMargin);
        assertEquals(dp(16), progressParams.rightMargin);
        assertEquals(dp(122), progressParams.topMargin);
        assertEquals(dp(4), progressParams.height);

        View cta = chromeView.findViewById(R.id.tv_learn_more);
        FrameLayout.LayoutParams ctaParams = (FrameLayout.LayoutParams) cta.getLayoutParams();
        assertEquals(dp(151), ctaParams.width);
        assertEquals(dp(48), ctaParams.height);
        assertEquals(dp(148), ctaParams.bottomMargin);

        FrameLayout.LayoutParams footerParams = (FrameLayout.LayoutParams) chromeView.getFooterBadge().getLayoutParams();
        assertEquals(dp(34), footerParams.bottomMargin);
    }

    @Test
    public void setSafeAreaInsets_UsesSideInsetsForFullscreenControls() {
        chromeView.setSafeAreaInsets(dp(54), dp(11), dp(34), dp(7));

        View soundButton = chromeView.findViewById(R.id.iv_sound_interstitial);
        FrameLayout.LayoutParams soundParams = (FrameLayout.LayoutParams) soundButton.getLayoutParams();
        assertEquals(dp(23), soundParams.leftMargin);

        chromeView.showSkipCountdown(5);

        View skip = chromeView.findViewById(R.id.iv_skip);
        FrameLayout.LayoutParams skipParams = (FrameLayout.LayoutParams) skip.getLayoutParams();
        assertEquals(dp(27), skipParams.rightMargin);

        View progress = chromeView.findViewById(R.id.rl_count_down);
        FrameLayout.LayoutParams progressParams = (FrameLayout.LayoutParams) progress.getLayoutParams();
        assertEquals(dp(23), progressParams.leftMargin);
        assertEquals(dp(27), progressParams.rightMargin);

        FrameLayout.LayoutParams footerParams = (FrameLayout.LayoutParams) chromeView.getFooterBadge().getLayoutParams();
        assertEquals(dp(7), footerParams.leftMargin);
        assertEquals(dp(11), footerParams.rightMargin);

        FrameLayout.LayoutParams adChoiceParams = (FrameLayout.LayoutParams) chromeView.getAdChoiceButton().getLayoutParams();
        assertEquals(dp(11), adChoiceParams.rightMargin);
    }

    @Test
    public void showSkipCountdown_UsesDaroPillStates() {
        chromeView.showSkipCountdown(5);

        View skip = chromeView.findViewById(R.id.iv_skip);
        FrameLayout.LayoutParams skipParams = (FrameLayout.LayoutParams) skip.getLayoutParams();
        assertEquals(dp(91), skipParams.width);
        assertEquals(dp(36), skipParams.height);
        assertEquals(false, skip.isEnabled());
        assertEquals("Skip in", chromeView.getSkipPrimaryText().getText().toString());
        assertEquals("5s", chromeView.getSkipSecondaryText().getText().toString());

        chromeView.showSkipCountdown(30);

        skipParams = (FrameLayout.LayoutParams) skip.getLayoutParams();
        assertEquals(dp(101), skipParams.width);
        assertEquals("30s", chromeView.getSkipSecondaryText().getText().toString());
    }

    @Test
    public void showSkipAvailable_UsesDaroAvailableState() {
        chromeView.showSkipAvailable();

        View skip = chromeView.findViewById(R.id.iv_skip);
        FrameLayout.LayoutParams skipParams = (FrameLayout.LayoutParams) skip.getLayoutParams();
        assertEquals(dp(93), skipParams.width);
        assertEquals(true, skip.isEnabled());
        assertEquals("Skip Ad", chromeView.getSkipPrimaryText().getText().toString());
        assertEquals(View.GONE, chromeView.getSkipSecondaryText().getVisibility());
        assertEquals(View.VISIBLE, chromeView.getSkipChevron().getVisibility());
        assertEquals(dp(16), chromeView.getSkipChevron().getLayoutParams().width);
        assertEquals(dp(16), chromeView.getSkipChevron().getLayoutParams().height);
    }

    @Test
    public void showEndCardLayout_UsesFigmaEndCardComponents() {
        chromeView.setSafeAreaInsets(dp(54), dp(34));

        chromeView.showEndCardLayout();

        View close = chromeView.findViewById(R.id.iv_close_interstitial);
        FrameLayout.LayoutParams closeParams = (FrameLayout.LayoutParams) close.getLayoutParams();
        assertEquals(View.VISIBLE, close.getVisibility());
        assertEquals(dp(36), closeParams.width);
        assertEquals(dp(36), closeParams.height);
        assertEquals(dp(16), closeParams.leftMargin);
        assertEquals(dp(70), closeParams.topMargin);

        assertEquals(View.GONE, chromeView.findViewById(R.id.iv_sound_interstitial).getVisibility());

        assertEquals(View.GONE, chromeView.findViewById(R.id.iv_skip).getVisibility());
        assertEquals(View.GONE, chromeView.findViewById(R.id.rl_count_down).getVisibility());

        View cta = chromeView.findViewById(R.id.tv_learn_more);
        assertEquals(View.GONE, cta.getVisibility());

        FrameLayout.LayoutParams footerParams = (FrameLayout.LayoutParams) chromeView.getFooterBadge().getLayoutParams();
        assertEquals(dp(34), footerParams.bottomMargin);
        FrameLayout.LayoutParams adChoiceParams = (FrameLayout.LayoutParams) chromeView.getAdChoiceButton().getLayoutParams();
        assertEquals(dp(34), adChoiceParams.bottomMargin);
    }

    @Test
    public void setSoundMuted_UsesExistingPrebidSoundTags() {
        View sound = chromeView.findViewById(R.id.iv_sound_interstitial);

        chromeView.setSoundMuted(true);

        assertEquals("on", sound.getTag());

        chromeView.setSoundMuted(false);

        assertEquals("off", sound.getTag());
    }

    @Test
    public void showRewardUnlocked_UsesSafeAreaToastPosition() {
        chromeView.setSafeAreaInsets(dp(54), dp(34));
        chromeView.showRewardUnlocked(true);

        View toast = chromeView.findViewById(R.id.daro_reward_toast);
        FrameLayout.LayoutParams toastParams = (FrameLayout.LayoutParams) toast.getLayoutParams();
        assertEquals(View.VISIBLE, toast.getVisibility());
        assertEquals(dp(166), toastParams.width);
        assertEquals(dp(36), toastParams.height);
        assertEquals(dp(142), toastParams.topMargin);
    }

    @Test
    public void setProgressFraction_ClampsProgressState() {
        chromeView.setProgressFraction(1.5f);

        assertEquals(1f, chromeView.getProgressFraction(), 0.001f);

        chromeView.setProgressFraction(-1f);

        assertEquals(0f, chromeView.getProgressFraction(), 0.001f);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
