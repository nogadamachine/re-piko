/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.validateLinks

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val validateLinksPatch =
    bytecodePatch(
        name = "외부 링크 오류 수정",
        description = "다른 앱에서 전달한 링크를 열 때 발생하는 종료 오류를 방지합니다.",
        default = true,
    ) {

        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // The following handles the signature check while sharing a link externally and opening a link.
            UriTrustingMethodFingerprint.classDef.methods
                .first {
                    it.returnType == "Z" && it.parameters.size == 2 &&
                        it.parameterTypes.last() == "Z"
                }.apply {
                    addInstructionsWithLabels(
                        0,
                        """
                        invoke-static {p1}, ${LINKS_DESCRIPTOR}->signatureCheck(Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :piko
                        return v0
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }

            AppIdentityToStringFingerprint.method.apply {
                val strIndex = AppIdentityToStringFingerprint.stringMatches[1].index

                val firstIGetObject = getInstruction(indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT))
                SignatureCheckExtensionFingerprint.changeFirstString(firstIGetObject.fieldExtractor().name)
            }
        }
    }
