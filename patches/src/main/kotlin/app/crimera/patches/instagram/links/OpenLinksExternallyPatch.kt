/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object InAppBrowserFunctionFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("Tracking.ARG_CLICK_SOURCE", "TrackingInfo.ARG_MODULE_NAME"),
)

@Suppress("unused")
val openLinksExternallyPatch =
    bytecodePatch(
        name = "외부 브라우저로 링크 열기",
        description = "앱 내부 브라우저 대신 외부 브라우저에서 링크를 엽니다.",
        default = true,
    ) {

        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            InAppBrowserFunctionFingerprint.let {
                it.method.apply {
                    if (!AccessFlags.STATIC.isSet(accessFlags) ||
                        parameterTypes != listOf("Landroidx/fragment/app/Fragment;", definingClass, "I") ||
                        implementation!!.registerCount - 3 < 3
                    ) throw PatchException("Unexpected 448 in-app browser entry signature")

                    // Trace the first native URI parser input back to the URL field.
                    // The move after ARG_CLICK_SOURCE now carries a Bundle, not the URL.
                    val uriIndex = indexOfFirstInstructionOrThrow {
                        val ref = getReference<MethodReference>()
                        opcode == Opcode.INVOKE_STATIC_RANGE &&
                            ref?.returnType == "Landroid/net/Uri;" &&
                            ref.parameterTypes == listOf("Ljava/lang/String;")
                    }
                    val uriRegister = getInstruction(uriIndex).registersUsed.single()
                    val copyIndex = (0 until uriIndex).lastOrNull { index ->
                        val instruction = getInstruction(index)
                        instruction.opcode == Opcode.MOVE_OBJECT_FROM16 &&
                            instruction.registersUsed[0] == uriRegister
                    } ?: throw PatchException("Missing native browser URL copy")
                    val copy = getInstruction(copyIndex)
                    val read = getInstruction(copyIndex - 1)
                    val field = read.getReference<FieldReference>()
                    if (read.opcode != Opcode.IGET_OBJECT ||
                        field?.definingClass != definingClass || field.type != "Ljava/lang/String;" ||
                        read.registersUsed[0] != copy.registersUsed[1]
                    ) throw PatchException("Browser URI input is not the controller URL field")

                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p1
                        iget-object v0, v0, $field
                        invoke-static {v0}, ${Constants.LINKS_DESCRIPTOR}->openExternally(Ljava/lang/String;)Z
                        move-result v0
                        if-eqz v0, :piko
                        return v0
                    """,
                        ExternalLabel("piko", instructions[0]),
                    )

                    enableSettings("openLinksExternally")
                }
            }
        }
    }
