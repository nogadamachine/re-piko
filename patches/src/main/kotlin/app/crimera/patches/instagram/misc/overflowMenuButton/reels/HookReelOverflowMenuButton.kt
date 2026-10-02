/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.reels

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.download.AddReelButtonFingerprint
import app.crimera.patches.instagram.utils.Constants.ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal object ClipsItemStateToStringFingerprint : Fingerprint(
    name = "toString",
    strings = listOf("ClipsItemState(lastUserPausedPositionMs="),
)

@Suppress("unused")
val hookReelOverflowMenuButton =
    bytecodePatch(
        description = "This patch hooks reel overflow button list adder",
    ) {
        dependsOn(reelsOverflowMenuButtonEntity, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            AddReelButtonFingerprint.method.apply {
                val classDef = AddReelButtonFingerprint.classDef
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == FRAGMENT_ACTIVITY }

                // Find the field that holds the MEDIA_ADD_INFO object on the reel controller.
                // This is the same class used by the feed hook to get CURRENT_MEDIA_FIELD.
                val mediaExtraDataField = ClipsItemStateToStringFingerprint.classDef.fields.first { it.type == MEDIA_ADD_INFO_CLASS_NAME }

                val builderIndex = indexOfFirstInstruction(Opcode.NEW_INSTANCE)
                val builderInstruction = getInstruction(builderIndex)
                val builderType = builderInstruction.getReference<TypeReference>()?.type
                val builderRegister = builderInstruction.registersUsed.single()
                val fenceIndex = indexOfFirstInstruction(Opcode.SPUT)
                // 448 moved this path into a static helper and keeps the state in v40.
                // Use dead low locals, pass the actual Media parameter, and leave live session state alone.
                if (!AccessFlags.STATIC.isSet(accessFlags) || parameterTypes.size != 8 ||
                    parameterTypes[2] != ClipsItemStateToStringFingerprint.classDef.type ||
                    parameterTypes[3] != "Lcom/instagram/feed/media/Media;" ||
                    parameterTypes[4] != classDef.type || builderRegister != 2 ||
                    builderType != "LX/0DYB;" ||
                    getInstruction(fenceIndex + 1).registersUsed != listOf(42, 45) ||
                    getInstruction(fenceIndex + 2).registersUsed != listOf(1, 42)
                ) throw PatchException("Unexpected 448 reels menu entry data flow")
                addInstructions(
                    fenceIndex + 1,
                    """
                    move-object/from16 v1, p2
                    iget-object v1, v1, $mediaExtraDataField
                    iget v1, v1, $CURRENT_MEDIA_FIELD
                    move-object/from16 v4, p4
                    iget-object v4, v4, $appActivityField
                    move-object/from16 v5, p3
                    invoke-static {v4,v2,v5,v1},$ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS->includeCustomReelOverflowButtons(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;I)V
                    """.trimIndent(),
                )
            }
        }
    }
