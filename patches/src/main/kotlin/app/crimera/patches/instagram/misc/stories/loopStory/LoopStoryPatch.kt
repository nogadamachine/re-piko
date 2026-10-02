/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.loopStory

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.indexOfFirstStringInstructionOrThrow
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException

internal object StoryProgressCompletedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;"),
)

@Suppress("unused")
val loopStoryPatch =
    bytecodePatch(
        name = "스토리 반복 재생",
        description = "현재 스토리가 끝나면 다시 재생합니다.",
    ) {
        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val method = StoryProgressCompletedFingerprint.method
            val owner = StoryProgressCompletedFingerprint.classDef
            val code = method.instructions
            val entryCast = code.firstOrNull { it.opcode == Opcode.CHECK_CAST }
                ?: throw PatchException("Missing story completion item cast")
            val itemType = entryCast.getReference<TypeReference>()?.type
            if (itemType != "Lcom/instagram/model/reels/ReelItem;" ||
                entryCast.registersUsed != listOf(method.p0Register + 1) || method.p0Register < 4
            ) throw PatchException("Unexpected 448 story completion callback registers")
            val photoTimer = owner.fields.singleOrNull { it.name == "photoTimerController" }
                ?: throw PatchException("Missing native story photo timer")
            val videoPlayer = owner.fields.singleOrNull { it.name == "mVideoPlayer" }
                ?: throw PatchException("Missing native story video player")
            val playerClass = mutableClassDefBy(videoPlayer.type)
            val seek = playerClass.methods.singleOrNull {
                it.name == "Gmz" && it.parameterTypes == listOf("I") && it.returnType == "V"
            } ?: throw PatchException("Missing verified 448 absolute story seek method")
            val resume = playerClass.methods.singleOrNull {
                it.name == "Gk2" && it.parameterTypes == listOf("Ljava/lang/String;", "Z") && it.returnType == "V"
            } ?: throw PatchException("Missing verified 448 story resume method")
            // GkC is a relative seek in 448. GkN forwards the absolute position to the player.
            val timerReset = code.indices.singleOrNull { index ->
                code[index].opcode == Opcode.IPUT &&
                    code[index].getReference<FieldReference>()?.type == "F" &&
                    code.getOrNull(index - 1)?.getReference<MethodReference>()?.parameterTypes == emptyList<String>() &&
                    code.getOrNull(index + 1)?.getReference<MethodReference>()?.parameterTypes == emptyList<String>()
            } ?: throw PatchException("Missing native story timer reset block")
            val timerPosition = code[timerReset].getReference<FieldReference>()!!
            val timerStop = code[timerReset - 1].getReference<MethodReference>()!!
            val timerStart = code[timerReset + 1].getReference<MethodReference>()!!
            if (timerStop.returnType != "V" || timerStart.returnType != "V" ||
                timerStop.definingClass != timerPosition.definingClass ||
                timerStart.definingClass != timerPosition.definingClass
            ) throw PatchException("Unexpected native photo timer reset methods")
            val progressCallIndex = (timerReset + 2 until code.size).firstOrNull {
                val ref = code[it].getReference<MethodReference>()
                code[it].opcode == Opcode.INVOKE_INTERFACE && ref?.parameterTypes == listOf(itemType)
            } ?: throw PatchException("Missing native story progress resolver")
            val progressGetter = code[progressCallIndex].getReference<MethodReference>()!!
            val progressField = owner.fields.singleOrNull { it.type == progressGetter.definingClass }
                ?: throw PatchException("Expected one native story progress adapter")
            val progressReset = code[progressCallIndex + 2].getReference<MethodReference>()
            if (code[progressCallIndex + 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
                progressReset?.definingClass != progressGetter.returnType ||
                progressReset.parameterTypes != listOf("F") || progressReset.returnType != "V"
            ) throw PatchException("Unexpected native story progress reset")
            method.addInstructionsWithLabels(0, """
                ${PREF_CALL_DESCRIPTOR}->loopStory()Z
                move-result v0
                if-eqz v0, :piko_original_story_end
                check-cast p1, $itemType
                const/4 v1, 0x0
                iget-object v2, p0, $photoTimer
                if-eqz v2, :piko_loop_video
                invoke-virtual {v2}, $timerStop
                iput v1, v2, $timerPosition
                invoke-virtual {v2}, $timerStart
                :piko_loop_video
                iget-object v2, p0, $videoPlayer
                if-eqz v2, :piko_loop_progress
                invoke-interface {v2, v1}, $seek
                const-string v3, "resume"
                invoke-interface {v2, v3, v1}, $resume
                :piko_loop_progress
                iget-object v2, p0, $progressField
                if-eqz v2, :piko_loop_done
                invoke-interface {v2, p1}, $progressGetter
                move-result-object v2
                if-eqz v2, :piko_loop_done
                invoke-virtual {v2, v1}, $progressReset
                :piko_loop_done
                return-void
            """.trimIndent(), ExternalLabel("piko_original_story_end", code[0]))
            enableSettings("loopStory")
        }
    }
