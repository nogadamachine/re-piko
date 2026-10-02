/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.buildExpiredPopup

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object SnoozeExpLockoutManagerFlagFingerprint : Fingerprint(
    strings = listOf("snooze_expiration_lockout_manager"),
    returnType = "Z",
)

@Suppress("unused")
val removeBuildExpiredPopupPatch =
    bytecodePatch(
        name = "오래된 버전 알림 제거",
        description = "앱 버전이 오래되었을 때 표시되는 알림을 제거합니다.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // Get the constructor.
            SnoozeExpLockoutManagerFlagFingerprint.classDef.methods.first().apply {
                val lastIPut = instructions.last { it.opcode == Opcode.IPUT }
                val appAgeRegister = lastIPut.registersUsed[0]

                addInstructions(
                    lastIPut.location.index,
                    """
                     invoke-static/range {v$appAgeRegister .. v$appAgeRegister}, $PREF_DESCRIPTOR->buildAge(I)I
                    move-result v$appAgeRegister
                    """.trimIndent(),
                )

                enableSettings("removeBuildExpirePopup")
            }
        }
    }
