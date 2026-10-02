/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.util.registersUsed
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

private const val PREF_CLASS_DESCRIPTOR = "Lapp/morphe/extension/instagram/utils/Pref;"

// 448 inlines AuthorInfoUseCase.shouldShowFollowButton into the UI-state builder.
private object ReelsShouldShowFollowButtonFingerprint : Fingerprint(
    definingClass = "LX/06TL;",
    name = "A0V",
    returnType = "LX/0IAz;",
    strings = listOf("android_purge_26_q3_AuthorInfoUseCase_shouldShowFollowButton",
        "android_purge_26_q3_FollowUseCase_getUiState"),
)

@Suppress("unused")
val hideReelsFollowButtonPatch =
    bytecodePatch(
        name = "릴스 팔로우 버튼 숨기기",
        description = "릴스의 팔로우 버튼을 숨깁니다.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(settingsPatch)

        execute {
            ReelsShouldShowFollowButtonFingerprint.matchAll(1..1).single().method.apply {
                val nextSection = ReelsShouldShowFollowButtonFingerprint.stringMatches
                    .single { it.string == "android_purge_26_q3_FollowUseCase_getUiState" }.index
                val falseIndex = nextSection - 3
                val joinIndex = falseIndex + 1
                val falseValue = instructions[falseIndex]
                val trueValue = instructions[falseIndex - 2]
                val branch = instructions[falseIndex - 1] as? BuilderOffsetInstruction
                val register = falseValue.registersUsed.singleOrNull()
                if (falseValue.opcode != Opcode.CONST_16 || trueValue.opcode != Opcode.CONST_16 ||
                    (falseValue as? WideLiteralInstruction)?.wideLiteral != 0L ||
                    (trueValue as? WideLiteralInstruction)?.wideLiteral != 1L || register == null ||
                    trueValue.registersUsed != listOf(register) || branch?.opcode != Opcode.IF_EQZ ||
                    branch.target.location.index != joinIndex ||
                    instructions[joinIndex].opcode != Opcode.MOVE_OBJECT_FROM16
                ) throw PatchException("Unexpected 448 inlined follow-button predicate")

                addInstructionsAtControlFlowLabel(
                    joinIndex,
                    """
                        invoke-static/range {v$register .. v$register}, $PREF_CLASS_DESCRIPTOR->showReelsFollowButton(Z)Z
                        move-result v$register
                    """,
                )
            }

            enableSettings("hideReelsFollowButton")
        }
    }
