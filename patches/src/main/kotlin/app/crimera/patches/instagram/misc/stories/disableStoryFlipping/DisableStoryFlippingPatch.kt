/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.disableStoryFlipping

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object StoryFlippingMethodFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;"),
)

@Suppress("unused")
val disableStoryFlippingPatch =
    bytecodePatch(
        name = "스토리 자동 넘기기 차단",
        description = "현재 스토리가 끝나도 다음 스토리로 자동 이동하지 않도록 설정할 수 있습니다.",
    ) {
        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            StoryFlippingMethodFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    ${PREF_CALL_DESCRIPTOR}->disableStoryFlipping()Z
                    move-result v0
                    if-eqz v0, :piko
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }
            enableSettings("disableStoryFlipping")
        }
    }
