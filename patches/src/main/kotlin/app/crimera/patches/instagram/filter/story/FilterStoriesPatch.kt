/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.filter.story

import app.crimera.patches.instagram.entity.reelResponseItem.reelResponseItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Heavily based on @brosssh work.
// https://github.com/brosssh/instagram-morphe-patches-library/blob/dev/patch-library/src/main/kotlin/app/morphe/library/instagram/patches/FilterStoriesListPatch.kt

@Suppress("unused")
val filterStoriesPatch =
    bytecodePatch(
        name = "스토리 필터",
        description = "선택한 분류에 해당하는 스토리를 숨깁니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, reelResponseItemEntity, userDataEntity)
        execute {
            val displayReels = object : Fingerprint(
                definingClass = "LX/0AEg;",
                name = "HDw",
                returnType = "V",
                parameters = listOf("Ljava/util/List;", "Lcom/instagram/common/session/UserSession;"),
                strings = listOf("add_to_story"),
            ) {}.method
            val reelClass = classDefBy { it.type == "LX/03wi;" }
            val expectedFields = mapOf("A2C" to "Ljava/lang/String;", "A0p" to "Ljava/lang/Integer;", "A0V" to "LX/03wo;")
            if (expectedFields.any { (name, type) -> reelClass.fields.none { it.name == name && it.type == type } } ||
                reelClass.methods.none { it.name == "A0M" && it.parameterTypes.isEmpty() && it.returnType == "Lcom/instagram/user/model/User;" } ||
                reelClass.methods.none { it.name == "A0A" && it.parameterTypes.map { p -> p.toString() } == listOf("Lcom/instagram/common/session/UserSession;") && it.returnType == "I" }) {
                throw PatchException("Unexpected 448 displayed Reel model; refusing an unverified filter")
            }
            val displayInstructions = displayReels.instructions.toList()
            if (displayInstructions.count { it.getReference<MethodReference>()?.toString() == "Ljava/util/List;->iterator()Ljava/util/Iterator;" } != 1 ||
                displayInstructions.count { it.getReference<MethodReference>()?.toString() == "LX/0AEh;-><init>(LX/03wi;Ljava/lang/String;ZZ)V" } != 1) {
                throw PatchException("Unexpected 448 displayed story adapter")
            }
            displayReels.addInstructions(0, """
                invoke-static/range {p1 .. p2}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->filterDisplayReels(Ljava/util/List;Lcom/instagram/common/session/UserSession;)Ljava/util/List;
                move-result-object p1
            """.trimIndent())
            val trayBinder = object : Fingerprint(
                definingClass = "LX/01gX;",
                name = "bindView",
                returnType = "V",
                parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
                strings = listOf("MainFeedStoryTrayBinderGroup.bindView", "litho_main_feed_stories_tray"),
            ) {}.method
            trayBinder.addInstructions(0, "invoke-static {}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->traceDisplay()V")
            // 448 materializes both JSON and Pando/cached trays through this method.
            // Hook the finished list so the filter is not limited to network JSON.
            val trayMethod = object : Fingerprint(
                definingClass = "LX/03rW;",
                name = "A02",
                returnType = "Ljava/util/ArrayList;",
                parameters = listOf("Lcom/instagram/common/session/UserSession;", "LX/0Fa9;"),
            ) {}.method
            val trayInstructions = trayMethod.instructions.toList()
            val returns = trayInstructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
            val trayReads = trayInstructions.count {
                it.getReference<MethodReference>()?.toString() == "LX/0Fa9;->Dk6()Ljava/util/List;"
            }
            val materializations = trayInstructions.count {
                it.getReference<MethodReference>()?.toString() == "LX/0Fen;->HXx(LX/0EAK;)Ljava/lang/Object;"
            }
            if (returns.size != 1 || trayReads != 1 || materializations != 1) {
                throw PatchException("Unexpected 448 materialized story tray; refusing an unverified hook")
            }
            val result = returns.single()
            val register = result.value.registersUsed.single()
            // Keep existing loop-exit labels on this location so they cannot
            // jump over the filter to the original return instruction.
            trayMethod.replaceInstruction(result.index, "nop")
            trayMethod.addInstructions(result.index + 1, """
                invoke-static/range {v$register .. v$register}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->filterTray(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v$register
                return-object v$register
            """.trimIndent())

            StoryResponseJsonParserFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {

                    // Port restricted to 448; this parser identity comes from the supplied APKM.
                    if (definingClass != "LX/03vx;" || name != "unsafeParseFromJson") {
                        throw PatchException("Unexpected 448 story parser; refusing an unverified hook")
                    }
                    val snapshot = instructions.toList()
                    val pcs = IntArray(snapshot.size)
                    var pc = 0
                    snapshot.forEachIndexed { i, instruction ->
                        pcs[i] = pc
                        pc += instruction.codeUnits
                    }
                    val pcToIndex = pcs.withIndex().associate { it.value to it.index }
                    val invokeOpcodes = setOf(
                        Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE,
                        Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE,
                    )
                    val normalized = snapshot.mapIndexed { i, instruction ->
                        val op = instruction.opcode
                        val reference = if (op in invokeOpcodes) {
                            instruction.getReference<MethodReference>()
                        } else null
                        val field = if (op == Opcode.SGET_OBJECT) {
                            instruction.getReference<FieldReference>()
                        } else null
                        val target = (instruction as? OffsetInstruction)?.let {
                            pcToIndex[pcs[i] + it.codeOffset] ?: -2
                        } ?: -1
                        when {
                            op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO ->
                                StoryInstruction(StoryInstructionKind.STRING)
                            field != null && field.definingClass == "LX/02vz;" &&
                                field.type == "LX/02vz;" && field.name == "A00" ->
                                StoryInstruction(StoryInstructionKind.PARSER_INSTANCE,
                                    instruction.registersUsed[0])
                            reference != null && reference.definingClass == "LX/03l8;" &&
                                reference.name == "parseFromJsonParser" &&
                                reference.returnType == "Ljava/lang/Object;" &&
                                reference.parameterTypes.map { it.toString() } == listOf("LX/03Yf;") &&
                                instruction.registersUsed.size == 2 ->
                                StoryInstruction(StoryInstructionKind.PARSE_OBJECT,
                                    receiverRegister = instruction.registersUsed[0])
                            op == Opcode.MOVE_RESULT_OBJECT ->
                                StoryInstruction(StoryInstructionKind.OBJECT_RESULT,
                                    instruction.registersUsed[0])
                            op == Opcode.IF_EQZ ->
                                StoryInstruction(StoryInstructionKind.NULL_GUARD,
                                    instruction.registersUsed[0], targetIndex = target)
                            op in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32) ->
                                StoryInstruction(StoryInstructionKind.GOTO, targetIndex = target)
                            reference != null &&
                                reference.definingClass == "Ljava/util/AbstractCollection;" &&
                                reference.name == "add" && reference.returnType == "Z" &&
                                reference.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;") &&
                                instruction.registersUsed.size == 2 ->
                                StoryInstruction(StoryInstructionKind.COLLECTION_ADD,
                                    instruction.registersUsed[1],
                                    receiverRegister = instruction.registersUsed[0])
                            else -> StoryInstruction(StoryInstructionKind.OTHER, targetIndex = target)
                        }
                    }
                    val hook = try {
                        selectStoryAppendHook(normalized, strIndex)
                    } catch (exception: IllegalArgumentException) {
                        throw PatchException(exception.message ?: "448 story hook did not match")
                    }
                    val index = hook.guardIndex
                    val reelResponseItemRegister = hook.itemRegister

                    addInstructionsWithLabels(
                        index + 1,
                        """
                        invoke-static/range {v$reelResponseItemRegister .. v$reelResponseItemRegister}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->filter(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$reelResponseItemRegister
                        if-eqz v$reelResponseItemRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(hook.resumeIndex)),
                    )

                    enableSettings("storyFilters")
                }
            }
        }
    }
