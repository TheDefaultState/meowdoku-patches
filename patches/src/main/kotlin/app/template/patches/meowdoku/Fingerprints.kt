package app.template.patches.meowdoku

import app.morphe.patcher.Fingerprint

/**
 * The rewarded ad entry point of this app.
 *
 * Every rewarded ad is shown through AppLovin MAX, whichever network fills it: the app's own
 * `MaxAdapter.doShowRewardedAd` calls `MaxRewardedAd.showAd(Activity)`, and Unity Ads,
 * ironSource and AdMob are only present as MAX mediation adapters. Hooking that one call
 * therefore covers all of them, and the MAX listener is the callback the app itself waits on.
 *
 * `showAd(Activity)` is public, concrete and has a body (access flag 0x1). Do not confuse it
 * with `com.google.android.gms.ads.rewarded.RewardedAd.show`, which is abstract (0x401) with
 * no body — adding instructions to that one fails with a NullPointerException.
 */
internal object MaxRewardedShowFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/ads/MaxRewardedAd;",
    name = "showAd",
    isStatic = false,
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;")
)
