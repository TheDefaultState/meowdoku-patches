package app.template.patches.meowdoku

import app.morphe.patcher.Fingerprint

/**
 * Rewarded ad entry points of the mediation SDKs that are plausible in this app.
 * SDK class names are stable, so they can be declared literally.
 * Everything is matched with matchOrNull(), so a missing SDK is not an error.
 */

// Unity Ads: static void show(Activity, String placementId, IUnityAdsShowListener)
internal object UnityAdsShowFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/ads/UnityAds;",
    name = "show",
    isStatic = true,
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        "Lcom/unity3d/ads/IUnityAdsShowListener;"
    )
)

// AppLovin MAX: void show(Activity, String, MaxRewardedAdListener[, MaxAdRevenueListener])
internal object MaxRewardedShowFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/ads/MaxRewardedAd;",
    name = "show",
    isStatic = false,
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterTypes
        parameters.size in 3..4 &&
            parameters[0] == "Landroid/app/Activity;" &&
            parameters[1] == "Ljava/lang/String;"
    }
)

// ironSource / LevelPlay: the listener is registered once, so capture it.
internal object IronSourceListenerFingerprint : Fingerprint(
    definingClass = "Lcom/ironsource/mediationsdk/IronSource;",
    name = "setRewardedVideoListener",
    isStatic = true,
    returnType = "V"
)

// ironSource / LevelPlay: static void showRewardedVideo() or showRewardedVideo(String).
internal object IronSourceShowRewardedFingerprint : Fingerprint(
    definingClass = "Lcom/ironsource/mediationsdk/IronSource;",
    name = "showRewardedVideo",
    isStatic = true,
    returnType = "V",
    custom = { method, _ -> method.parameterTypes.size <= 1 }
)

// Google AdMob: void show(Activity, OnUserEarnedRewardListener)
internal object AdmobRewardedShowFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/gms/ads/rewarded/RewardedAd;",
    name = "show",
    isStatic = false,
    returnType = "V",
    custom = { method, _ -> method.parameterTypes.size == 2 }
)
