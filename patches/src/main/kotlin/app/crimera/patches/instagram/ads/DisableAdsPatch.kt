/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object DisableAdsFingerprint : Fingerprint(
    strings = listOf("Is ad pod"),
)

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "광고 차단",
        description = "피드, 스토리, 릴스의 광고를 차단합니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            hookFlagsPatch,
        )
        execute {

            DisableAdsFingerprint.method.apply {
                // OFF must preserve the native ad-pod validation and processing callbacks.
                // An unconditional false return bypasses them and cannot serve as a control.
                addInstructionsWithLabels(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    if-eqz v0, :piko_original_ads
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko_original_ads", getInstruction(0)),
                )

                enableSettings("disableAds")
                addFlags("adsFlags")
            }
        }
    }
