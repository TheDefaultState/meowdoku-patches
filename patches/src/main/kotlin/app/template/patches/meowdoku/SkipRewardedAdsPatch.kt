package app.template.patches.meowdoku

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.method.addInstructions

private const val HELPER = "Lapp/template/extension/RewardAdSkipper;"

val COMPATIBILITY_MEOWDOKU = Compatibility(
    name = "Meowdoku",
    packageName = "com.oakever.meowdoku",
    appIconColor = 0xFFF2C879,
    targets = listOf(AppTarget(version = "1.22.1"))
)

/**
 * Rewarded ads are never shown, but the app receives the callbacks it gets after a
 * fully watched ad, so "watch an ad to retry / to get a reward" resolves instantly.
 *
 * Only parameter registers are referenced, so .registers never has to be changed.
 */
val skipRewardedAdsPatch = bytecodePatch(
    name = "Skip rewarded ads",
    description = "Skips rewarded ads and reports them as watched, so retries and rewards are granted instantly.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MEOWDOKU)

    extendWith("extensions/extension.mpe")

    val skipUnityAds by booleanOption(name = "Skip Unity Ads", default = true)
    val skipAppLovin by booleanOption(name = "Skip AppLovin MAX", default = true)
    val skipIronSource by booleanOption(name = "Skip ironSource / LevelPlay", default = true)
    val skipAdMob by booleanOption(name = "Skip AdMob", default = true)

    execute {
        val matched = mutableListOf<String>()

        if (skipUnityAds) {
            UnityAdsShowFingerprint.matchOrNull()?.let {
                it.method.addInstructions(
                    0,
                    """
                        invoke-static {p1, p2}, $HELPER->unityAds(Ljava/lang/String;Ljava/lang/Object;)V
                        return-void
                    """
                )
                matched += "Unity Ads"
            }
        }

        if (skipAppLovin) {
            MaxRewardedShowFingerprint.matchOrNull()?.let {
                it.method.addInstructions(
                    0,
                    """
                        invoke-static {p0, p2, p3}, $HELPER->maxAd(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V
                        return-void
                    """
                )
                matched += "AppLovin MAX"
            }
        }

        if (skipIronSource) {
            IronSourceListenerFingerprint.matchOrNull()?.let {
                it.method.addInstructions(
                    0,
                    "invoke-static {p0}, $HELPER->captureIronSourceListener(Ljava/lang/Object;)V"
                )
                matched += "ironSource listener"
            }

            IronSourceShowRewardedFingerprint.matchOrNull()?.let { match ->
                val call = if (match.method.parameterTypes.isEmpty()) {
                    "invoke-static { }, $HELPER->ironSource()V"
                } else {
                    "invoke-static {p0}, $HELPER->ironSource(Ljava/lang/String;)V"
                }
                match.method.addInstructions(0, "$call\nreturn-void")
                matched += "ironSource show"
            }
        }

        if (skipAdMob) {
            AdmobRewardedShowFingerprint.matchOrNull()?.let {
                it.method.addInstructions(
                    0,
                    """
                        invoke-static {p0, p2}, $HELPER->admob(Ljava/lang/Object;Ljava/lang/Object;)V
                        return-void
                    """
                )
                matched += "AdMob"
            }
        }

        if (matched.isEmpty()) {
            throw PatchException(
                "No rewarded ad entry point matched. Enable the SDK options your build actually uses, " +
                    "or add a fingerprint for the network found in the app."
            )
        }
        println("Skip rewarded ads: hooked ${matched.joinToString()}.")
    }
}
