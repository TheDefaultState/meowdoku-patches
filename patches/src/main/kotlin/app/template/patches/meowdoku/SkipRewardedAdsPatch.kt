package app.template.patches.meowdoku

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch

private const val HELPER = "Lapp/template/extension/RewardAdSkipper;"

val COMPATIBILITY_MEOWDOKU = Compatibility(
    name = "Meowdoku",
    packageName = "com.oakever.meowdoku",
    // Plain 0xRRGGBB: Compatibility rejects any alpha byte, so 0xFFF2C879 throws.
    appIconColor = 0xF2C879,
    targets = listOf(AppTarget(version = "1.22.1"))
)

/**
 * Rewarded ads are never shown, but the app receives the callbacks it gets after a
 * fully watched ad, so "watch an ad to retry / to get a reward" resolves instantly.
 *
 * One hook is enough: every rewarded ad of this app is shown through the same MAX call,
 * including the ones Unity Ads, ironSource or AdMob fill as MAX adapters.
 *
 * Only the parameter register is referenced, so .registers never has to be changed.
 */
val skipRewardedAdsPatch = bytecodePatch(
    name = "Block rewarded ads",
    description = "Blocks rewarded ads and reports them as watched, so retries and rewards are granted instantly.",
    default = true
) {
    compatibleWith(COMPATIBILITY_MEOWDOKU)

    extendWith("extensions/extension.mpe")

    val blockRewardedAds by booleanOption(
        key = "Block rewarded ads",
        description = "Skip the ad and grant the reward right away.",
        default = true
    )

    execute {
        // With the option off the app keeps its stock behaviour.
        if (blockRewardedAds != true) return@execute

        val match = MaxRewardedShowFingerprint.matchOrNull()
            ?: throw PatchException(
                "MaxRewardedAd.showAd(Activity) was not found. The AppLovin MAX version in this build " +
                    "probably changed; re-check how the app shows rewarded ads."
            )

        match.method.addInstructions(
            0,
            """
                invoke-static {p0}, $HELPER->maxAd(Ljava/lang/Object;)V
                return-void
            """
        )

        println("Block rewarded ads: hooked MaxRewardedAd.showAd(Activity).")
    }
}
