/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val improveImageViewingPatch =
    bytecodePatch(
        name = "고해상도 이미지 보기",
        description = "서버에서 제공하는 최대 해상도 이미지를 불러옵니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            var PREF_CALL =
                """
                invoke-static {p2}, $PREF_DESCRIPTOR->improveImageSelectionTarget(I)I
                    move-result p2
                """.trimIndent()

            ReturnExtendedImageUrlFingerprint.method.apply {
                addInstructions(0, PREF_CALL)
            }

            PREF_CALL =
                """
                invoke-static {v#reg}, $PREF_DESCRIPTOR->improveImageViewing(Ljava/lang/Integer;)Ljava/lang/Integer;
                    move-result-object v#reg
                """.trimIndent()

            SetDPIMetricsFingerprint.method.apply {
                val filledNewArrayIndex = indexOfFirstInstruction(Opcode.FILLED_NEW_ARRAY)

                val registers = getInstruction(filledNewArrayIndex).registersUsed

                val heightRegister = registers[1].toString()
                val widthRegister = registers[2].toString()

                addInstructions(
                    filledNewArrayIndex,
                    """
                    ${PREF_CALL.replace("#reg",heightRegister)}
                    ${PREF_CALL.replace("#reg", widthRegister)}
                    """.trimIndent(),
                )
            }

            enableSettings("improveImageViewing")
        }
    }
