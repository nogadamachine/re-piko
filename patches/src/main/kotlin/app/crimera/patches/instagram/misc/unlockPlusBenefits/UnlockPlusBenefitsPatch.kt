/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.unlockPlusBenefits

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object ActiveBenefitCheckerFingerprint : Fingerprint(
    definingClass = "LX/07jl;",
    name = "A0F",
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Z",
    // 448 moved the logging string into A04; the public checker returns set membership.
    custom = { method, _ ->
        method.implementation?.instructions?.count {
            it.getReference<MethodReference>()?.toString() ==
                "Ljava/util/Set;->contains(Ljava/lang/Object;)Z"
        } == 1
    },
)

@Suppress("unused")
val unlockPlusBenefitsPatch =
    bytecodePatch(
        name = "Plus 로컬 기능 활성화",
        description = "앱 내부에서 확인하는 Plus 기능을 활성화합니다. 서버에서 확인하는 구독 기능의 이용을 보장하지 않습니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            ActiveBenefitCheckerFingerprint.method.apply {

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->unlockPlusBenefits()Z
                    move-result v0
                    if-eqz v0, :piko_continue
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko_continue", getInstruction(0)),
                )

                enableSettings("unlockPlusBenefits")
            }
        }
    }
