/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val profileInfoEntity =
    bytecodePatch(
        description = "Used to decode profile info",
    ) {
        dependsOn(decoderEntity)

        execute {

            ProfileUserInfoViewBinderFingerprint.method.apply {
                mutableClassDefBy(parameters[1].type).apply {
                    val profileRelatedDetailsClass = ProfileRelatedDetailsFingerprint.classDef

                    val profileRelatedDetailsFieldName = fields.single { it.type == profileRelatedDetailsClass.type }.name
                    GetProfileRelatedDetailsExtensionFingerprint.changeFirstString(profileRelatedDetailsFieldName)

                    val userDetailViewModelFieldName =
                        fields
                            .single { it.type == USER_DETAIL_VIEW_MODEL_CLASS }
                            .name
                    GetUserDetailViewModelExtensionFingerprint.changeFirstString(userDetailViewModelFieldName)

                    // 448's username getter reads launch state; it no longer reads User
                    // after INVALID_USER_NAME. Resolve the unique typed model field.
                    val userObjectFieldName = mutableClassDefBy(USER_DETAIL_VIEW_MODEL_CLASS)
                        .fields.singleOrNull { it.type == USER_MODEL_CLASS_NAME }?.name
                        ?: throw PatchException("Expected one User field in the 448 profile view model")
                    GetUserDataExtensionFingerprint.changeFirstString(userObjectFieldName)

                    val selfKeyIndex = ProfileRelatedDetailsFingerprint.stringMatches
                        .single { it.string == "is_self" }.index
                    val selfRead = ProfileRelatedDetailsFingerprint.method.getInstruction(selfKeyIndex + 1)
                    val selfField = selfRead.getReference<FieldReference>()
                    if (selfRead.opcode != Opcode.IGET_BOOLEAN || selfField?.type != "Z" ||
                        selfField.definingClass != profileRelatedDetailsClass.type
                    ) throw PatchException("Unexpected 448 is_self field contract")
                    IsSelfProfileExtensionFingerprint.changeFirstString(selfField.name)
                }
            }
        }
    }
