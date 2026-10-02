/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

object BindMainFeedActionBarFingerprint : Fingerprint(
    strings = listOf("BindMainFeedActionBar"),
    definingClass = "LX/08tE;",
    name = "A08",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "LX/0FwO;", "I"),
    returnType = "Ljava/lang/Object;",
)

private object HomeActionModelFingerprint : Fingerprint(
    name = "<init>",
    strings = listOf("share", "news", "quick_snap", "manage_feeds"),
)

val hideHomeActionButtonsPatch = bytecodePatch {
    execute {
        val method = HomeActionModelFingerprint.matchAll(0..Int.MAX_VALUE)
            .singleOrNull()?.method
            ?: throw PatchException("Expected one home action model builder")
        val callIndex = method.instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.parameterTypes?.map(CharSequence::toString) ==
                listOf("Lcom/instagram/common/session/UserSession;", "I") &&
                reference.returnType == "Ljava/lang/String;"
        }.singleOrNull()?.index
            ?: throw PatchException("Expected one home action name lookup")
        val result = method.getInstruction(callIndex + 1)
        val add = method.getInstruction(callIndex + 2)
        val addRef = (add as? ReferenceInstruction)?.reference as? MethodReference
        val loopTail = method.getInstruction(callIndex + 3)
        if (result.opcode != Opcode.MOVE_RESULT_OBJECT ||
            result.registersUsed.size != 1 ||
            addRef?.toString() != "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z" ||
            add.registersUsed != listOf(5, result.registersUsed.single()) ||
            loopTail.opcode != Opcode.GOTO
        ) throw PatchException("Expected 449 action-name collection append and loop tail")
        val register = result.registersUsed.single()
        method.addInstructionsWithLabels(
            callIndex + 2,
            """
            invoke-static/range {v$register .. v$register}, $ACTIONBAR_DESCRIPTOR->filterHomeAction(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$register
            if-eqz v$register, :piko_skip_action
            """.trimIndent(),
            ExternalLabel("piko_skip_action", loopTail),
        )
    }
}

val mainFeedActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on main feed action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val method = BindMainFeedActionBarFingerprint.matchAll(1..1).single().method
            val actionBar = "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;"
            val callIndex = method.instructions.withIndex().filter { (_, instruction) ->
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    ref?.definingClass == actionBar && ref.name == "A08" &&
                    ref.parameterTypes.map(CharSequence::toString) == listOf(
                        "LX/000X;", "Lcom/instagram/common/session/UserSession;", "LX/02gU;"
                    ) && ref.returnType == "V"
            }.singleOrNull()?.index ?: throw PatchException("Expected one 449 action bar bind")
            // Original DEX: A08 binds the controls, then v0 is overwritten by instance-of.
            // A0G is the LinearLayout for action_bar_buttons_container_right (0x7f0b00b4).
            val call = method.getInstruction(callIndex)
            if (call.registersUsed != listOf(3, 0, 5, 12) ||
                method.getInstruction(callIndex + 1).opcode != Opcode.IGET_OBJECT ||
                method.getInstruction(callIndex + 2).opcode != Opcode.INSTANCE_OF ||
                method.getInstruction(callIndex + 2).registersUsed.firstOrNull() != 0
            ) throw PatchException("Unexpected 449 action bar register liveness")
            val actionBarClass = mutableClassDefBy(actionBar)
            if (actionBarClass.fields.none { it.name == "A0G" && it.type == "Landroid/widget/LinearLayout;" }) {
                throw PatchException("Missing 449 right action button container")
            }
            method.addInstructions(callIndex + 1, """
                iget-object v0, v3, $actionBar->A0G:Landroid/widget/LinearLayout;
                invoke-static {v0}, $ACTIONBAR_DESCRIPTOR->mainFeedActionBarButton(Landroid/view/ViewGroup;)V
            """.trimIndent())
            addFlags("mainFeedActionBarFlags")
        }
    }
