/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.privacy

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object ScreenshotDetectorFingerprint : Fingerprint(
    strings = listOf("ig_android_story_screenshot_directory", "screenshot_detector"),
)

internal object AddFlagsToWindowFingerprint : Fingerprint(
    strings = listOf("Inconsistency in window FLAG_SECURE state detected! window state: "),
)

internal object DirectScreenshotCaptureTriggerFingerprint : Fingerprint(
    strings = listOf("igd_screenshot_capture"),
)

internal object ChatRecyclerViewRelatedFingerprint : Fingerprint(
    strings = listOf("Removed holder should be bound and it should come here only in pre-layout. Holder: "),
)

@Suppress("unused")
val disableScreenshotDetection =
    bytecodePatch(
        name = "스크린샷 감지 차단",
        description = "DM의 스크린샷 감지를 차단합니다.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val PREF_CALL =
                """
                invoke-static {}, $PREF_DESCRIPTOR->disableScreenshotDetection()Z
                move-result
                """.trimIndent()

            // Thanks to MyInsta.
            ScreenshotDetectorFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {
                    val observerStartInvokeInstruction =
                        instructions.last {
                            it.location.index < strIndex &&
                                it.opcode == Opcode.INVOKE_VIRTUAL
                        }

                    val index = observerStartInvokeInstruction.location.index

                    val nextConstInstruction = getInstruction(index + 1)
                    val freeRegister = nextConstInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        index,
                        """
                        $PREF_CALL v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", nextConstInstruction),
                    )

                    // Thanks to InstaPro
                    DirectScreenshotCaptureTriggerFingerprint.method.apply {
                        addInstructionsWithLabels(
                            0,
                            """
                            $PREF_CALL v0
                            if-eqz v0, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(0)),
                        )
                    }

                    // Thanks to InstaPro
                    ChatRecyclerViewRelatedFingerprint.method.apply {
                        val firstIntAdderIndex = indexOfFirstInstruction(Opcode.AND_INT_2ADDR)
                        val flagIntIndex = firstIntAdderIndex - 2
                        val freeRegister = getInstruction(flagIntIndex).registersUsed[0]

                        val firstIGetObjectAfterFlagIntIndex = indexOfFirstInstruction(firstIntAdderIndex, Opcode.IGET_OBJECT)

                        addInstructionsWithLabels(
                            flagIntIndex,
                            """
                            $PREF_CALL v$freeRegister
                            if-nez v$freeRegister, :piko
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(firstIGetObjectAfterFlagIntIndex)),
                        )
                    }

                    enableSettings("disableScreenshotDetection")
                }
            }

            // 448 sets FLAG_SECURE both in the manager's acquire/reconcile paths
            // and directly in ReelViewerFragment. Skipping only the reconcile
            // setter leaves native view-once messages impossible to capture.
            val secureManager = AddFlagsToWindowFingerprint.classDef
            val reelViewer = mutableClassDefBy("Linstagram/features/stories/fragment/ReelViewerFragment;")
            for ((owner, expectedCalls) in listOf(secureManager to 2, reelViewer to 1)) {
                var patchedCalls = 0
                for (method in owner.methods) {
                    val setters = method.instructions.mapIndexedNotNull { index, instruction ->
                        val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        if (ref?.definingClass == "Landroid/view/Window;" && ref.name == "setFlags" &&
                            ref.parameterTypes.map { it.toString() } == listOf("I", "I") && ref.returnType == "V"
                        ) index else null
                    }
                    for (index in setters) {
                        val setter = method.instructions[index]
                        val registers = setter.registersUsed
                        if (setter.opcode != Opcode.INVOKE_VIRTUAL || registers.size != 3 ||
                            registers[1] != registers[2] ||
                            method.instructions.take(index).none {
                                it.opcode == Opcode.CONST_16 &&
                                    it.registersUsed == listOf(registers[1]) &&
                                    (it as? WideLiteralInstruction)?.wideLiteral == 0x2000L
                            }
                        ) throw PatchException("Unexpected 448 secure-window setter in ${method.definingClass}->${method.name}")

                        method.replaceInstruction(index,
                            "invoke-static {${registers.joinToString(", ") { "v$it" }}}, " +
                                "$PATCHES_DESCRIPTOR/privacy/ScreenshotPatch;->setWindowFlags(Landroid/view/Window;II)V")
                        patchedCalls++
                    }
                }
                if (patchedCalls != expectedCalls) {
                    throw PatchException("Expected $expectedCalls secure-window setters in ${owner.type}, found $patchedCalls")
                }
            }
        }
    }
