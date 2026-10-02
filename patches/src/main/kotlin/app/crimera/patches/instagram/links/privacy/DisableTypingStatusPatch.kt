/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton.inboxActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object DMAfterTextChangedFingerprint : Fingerprint(
    name = "afterTextChanged",
    filters =
        listOf(
            literal(8388659L), // 0x800032
            literal(8388627L), // 0x800013
        ),
)

internal object DMOnTextChangedFingerprint : Fingerprint(
    name = "onTextChanged",
    classFingerprint = DMAfterTextChangedFingerprint,
)

// Thanks to MyInsta.
@Suppress("unused")
val disableTypingStatusPatch =
    bytecodePatch(
        name = "입력 중 상태 숨기기",
        description = "DM에서 메시지를 입력 중이라는 상태를 보내지 않도록 설정할 수 있습니다.",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch, chatActionBarButtonPatch, inboxActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            DMOnTextChangedFingerprint.method.apply {

                val originalRead = instructions.filterIndexed { index, instruction ->
                    instruction.opcode == Opcode.IGET_BOOLEAN &&
                        instructions.getOrNull(index + 1)?.opcode == Opcode.IF_NEZ
                }.singleOrNull() ?: throw PatchException("Expected one native composer suppression guard")
                val index = originalRead.location.index
                val register = getInstruction(index + 1).registersUsed.single()
                if (originalRead.registersUsed.first() != register || returnType != "V") {
                    throw PatchException("Unexpected native composer suppression guard shape")
                }
                addInstructionsWithLabels(
                    index,
                    """
                    $PREF_CALL_DESCRIPTOR->disableTypingStatus()Z
                    move-result v$register
                    if-eqz v$register, :piko
                    return-void
                    """.trimIndent(),
                    // OFF must execute the original field read. Branching to IF_NEZ
                    // reused our false result and bypassed Instagram's own guard.
                    ExternalLabel("piko", originalRead),
                )
            }

            enableSettings("disableTypingStatus")
        }
    }
