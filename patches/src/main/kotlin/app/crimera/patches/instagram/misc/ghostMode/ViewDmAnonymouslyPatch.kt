/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton.inboxActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider

@Suppress("unused")
val viewDmAnonymouslyPatch =
    bytecodePatch(
        name = "DM 읽음 숨기기",
        description = "DM을 열어도 상대방에게 읽음 상태를 보내지 않도록 설정할 수 있습니다.",
    ) {
        dependsOn(settingsPatch, chatActionBarButtonPatch, inboxActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            DMSeenFingerprint.method.apply {
                val shouldDisableRegister =
                    getFreeRegisterProvider(
                        index = 1,
                        numberOfFreeRegistersNeeded = 1,
                    ).getFreeRegister()

                addInstructionsWithLabels(
                    1,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->viewDmAnonymously()Z
                    move-result v$shouldDisableRegister
                    if-eqz v$shouldDisableRegister, :piko_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_continue", getInstruction(1)),
                )
            }
            // Stop before the native helper updates cached regular-message seen metadata.
            // The late dispatch guard above also protects other legacy callers.
            val nativeSeenHelper = mutableClassDefBy("LX/05Xl;").methods.singleOrNull {
                it.name == "A02" && it.returnType == "V" &&
                    it.parameterTypes == listOf("LX/0Uhg;", "LX/06i8;", "LX/07gZ;", "Z")
            } ?: throw PatchException("Instagram 449 native seen helper signature changed")
            nativeSeenHelper.apply {
                // Morphe's liveness walker rejects this entry branch. In this exact method,
                // the null branch returns immediately and the other branch first writes v0.
                val entry = getInstruction(0)
                val next = getInstruction(1)
                val targetOffset = (entry as? OffsetInstruction)?.codeOffset
                var offset = 0
                val target = instructions.firstOrNull { instruction ->
                    val matches = offset == targetOffset
                    offset += instruction.codeUnits
                    matches
                }
                if (entry.opcode != Opcode.IF_EQZ || next.opcode != Opcode.MOVE_OBJECT_FROM16 ||
                    (next as? TwoRegisterInstruction)?.registerA != 0 ||
                    target?.opcode != Opcode.RETURN_VOID || (implementation?.registerCount ?: 0) <= 5) {
                    throw PatchException("Instagram 449 seen helper entry no longer has a safe v0")
                }
                val register = 0
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static/range {p0 .. p4}, Lapp/morphe/extension/instagram/patches/dm/MarkChatAsRead;->shouldBlockNativeRead(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)Z
                    move-result v$register
                    if-eqz v$register, :piko_native_seen
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_native_seen", getInstruction(0)),
                )
            }
            // Visual media has a separate mutation, before the ordinary thread receipt.
            // Guard its entry before the native viewed set or unread count is changed.
            val controllerType = "Linstagram/features/direct/visual/internal/DirectVisualMessageViewerController;"
            val visualSeen = mutableClassDefBy(controllerType).methods.singleOrNull {
                it.name == "A0C" && it.returnType == "V" &&
                    it.parameterTypes == listOf("LX/0Y3J;", controllerType)
            } ?: throw PatchException("Instagram 449 visual seen helper signature changed")
            visualSeen.apply {
                if (getInstruction(0).opcode != Opcode.CONST_4 ||
                    (implementation?.registerCount ?: 0) != 19) {
                    throw PatchException("Instagram 449 visual seen entry changed")
                }
                // v0 is assigned on every original path before use. Parameter registers
                // p0/p1 remain untouched, and all native instructions and handlers remain.
                addInstructionsWithLabels(0, """
                    invoke-static/range {p0 .. p1}, Lapp/morphe/extension/instagram/patches/dm/MarkChatAsRead;->shouldBlockNativeVisualRead(Ljava/lang/Object;Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :piko_native_visual_seen
                    return-void
                """.trimIndent(), ExternalLabel("piko_native_visual_seen", getInstruction(0)))
            }
            enableSettings("viewDmAnonymously")
        }
    }
