/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.crimera.patches.instagram.entity.mediadata.AslSessionRelatedFingerprint
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.AccessFlags
import kotlin.properties.Delegates

var MEDIA_CLASS_NAME: String by Delegates.notNull()
    private set

var MEDIAEXT_CLASS_NAME: String by Delegates.notNull()
    private set

var USER_MODEL_CLASS_NAME: String by Delegates.notNull()
    private set

var MEDIA_ADD_INFO_CLASS_NAME: String by Delegates.notNull()
    private set

var CURRENT_MEDIA_FIELD: FieldReference by Delegates.notNull()
    private set

var COMMENT_BUTTON_CLASS: String by Delegates.notNull()
    private set

val decoderEntity =
    bytecodePatch(
        description = "This patch is used hold class and field names that are commonly used",
    ) {
        execute {
            MEDIA_CLASS_NAME = AslSessionRelatedFingerprint.method.parameters[0].type

            EditMediaInfoGetCurrentMediaIdFingerprint.matchAll(1..1).single().method.apply {
                // 448 moved this carousel accessor to a static helper. The previous
                // parameterless match selected MVLinkInfo rather than the current item.
                val indexReads = instructions.filter { it.opcode == Opcode.IGET }
                val indexField = indexReads.singleOrNull()?.getReference<FieldReference>()
                    ?: throw PatchException("Expected one 448 carousel index read")
                if (!AccessFlags.STATIC.isSet(accessFlags) ||
                    indexField.definingClass != "LX/04nR;" ||
                    indexField.name != "A0A" || indexField.type != "I" ||
                    instructions.none {
                        it.getReference<MethodReference>()?.let { ref ->
                            ref.definingClass == "Ljava/util/List;" && ref.name == "get" &&
                                ref.parameterTypes.map(CharSequence::toString) == listOf("I")
                        } == true
                    }
                ) throw PatchException("Unexpected 448 carousel index contract")
                CURRENT_MEDIA_FIELD = indexField
                MEDIA_ADD_INFO_CLASS_NAME = CURRENT_MEDIA_FIELD.definingClass
            }

            COMMENT_BUTTON_CLASS = CommentButtonOnClickFingerprint.method.parameters[0].type

            USER_MODEL_CLASS_NAME = UserTagInfoDictInitFingerprint.method.parameters[0].type

            MEDIAEXT_CLASS_NAME = MediaExtOriginalSoundFingerprint.classDef.type
        }
    }
