/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.makeEphemeralPermanent

import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object EphemeralMediaJsonParserFingerprint : Fingerprint(
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
    returnType = "Ljava/lang/Object;",
    strings = listOf("url_expire_at_secs", "view_mode", "seen_count", "tap_models"),
)

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "사라지는 미디어 다시 보기",
        description = "아직 만료되지 않은 1회 또는 2회 보기 미디어를 계속 볼 수 있도록 변경합니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, messageInfoEntity, saveAllMessagesPatch)
        execute {

            EphemeralMediaJsonParserFingerprint.apply {
                val expireAtStringIndex = stringMatches[0].index
                val viewModeStringIndex = stringMatches[1].index
                method.apply {
                    val viewModeIPutObjectInstruction =
                        getInstruction(
                            indexOfFirstInstruction(viewModeStringIndex, Opcode.IPUT_OBJECT),
                        )

                    val viewModeField = viewModeIPutObjectInstruction.getReference<FieldReference>()!!
                    val expireWrite = getInstruction(indexOfFirstInstruction(expireAtStringIndex, Opcode.IPUT_OBJECT))
                    val expireField = expireWrite.getReference<FieldReference>()!!
                    val modelRegister = viewModeIPutObjectInstruction.registersUsed[1]
                    val model = classDefBy { it.type == viewModeField.definingClass }
                    if (viewModeField.type != "Ljava/lang/String;" ||
                        expireField.type != "Ljava/lang/Long;" ||
                        expireField.definingClass != model.type ||
                        expireWrite.registersUsed[1] != modelRegister ||
                        modelRegister in 0..1 ||
                        listOf(viewModeField, expireField).any { ref ->
                            model.fields.none { it.name == ref.name && it.type == ref.type }
                        }
                    ) throw PatchException("Unexpected ephemeral media JSON field bindings")

                    val returnInstruction = instructions.filter {
                        it.opcode == Opcode.RETURN_OBJECT && it.registersUsed[0] == modelRegister
                    }.singleOrNull() ?: throw PatchException("Expected one parsed ephemeral model return")
                    val returnIndex = returnInstruction.location.index
                    // Replace the return at its existing label so the parser's completion
                    // branch also enters the hook. The null/error return is left intact.
                    replaceInstruction(returnIndex, "iget-object v0, v$modelRegister, $expireField")
                    addInstructions(
                        returnIndex + 1,
                        """
                        iget-object v1, v$modelRegister, $viewModeField
                        
                        invoke-static {v0, v1}, $PATCHES_DESCRIPTOR/dm/EphemeralMediaPatch;->makeEphemeralMediaPermanent(Ljava/lang/Long;Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1                        
                        
                        iput-object v1, v$modelRegister, $viewModeField
                        return-object v$modelRegister
                        """.trimIndent(),
                    )
                }
            }
            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }
