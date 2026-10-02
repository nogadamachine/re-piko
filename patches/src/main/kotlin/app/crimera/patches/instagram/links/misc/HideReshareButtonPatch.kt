/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Credits: brosssh
// https://github.com/brosssh/morphe-patches/commit/6a781ef8e0951ad5aa898fa17d094cfbfa5dd9fb

// The hash code of the field of interest. It is used as the key of a hashmap
internal const val notesTag = "enable_media_notes_production"
internal val hashedFieldInteger = notesTag.hashCode()

internal object FeedResponseMediaParserFingerprint : Fingerprint(
    filters =
        listOf(
            string(notesTag),
            literal(hashedFieldInteger),
        ),
    returnType = "Ljava/lang/Boolean;",
)

internal object LiveTreeGetOptionalBooleanFingerprint : Fingerprint(
    name = "getOptionalBooleanValueByHashCode",
    definingClass = "Lcom/instagram/pando/livetree/LiveTreeJNI;",
)

@Suppress("unused")
val hideReshareButtonPatch =
    bytecodePatch(
        name = "다시 공유 버튼 숨기기",
        description = "게시물과 릴스의 다시 공유 버튼을 숨깁니다.",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val PREF_CALL = "$PREF_CALL_DESCRIPTOR->hideReshareButton()Z"

            // The native feed and both reels variants also render dedicated
            // Litho components outside the newer UI state path. A null render
            // is the framework's normal representation of an absent component.
            for (component in listOf("LX/016e;", "LX/0Pw5;", "LX/061b;", "LX/0PtV;")) {
                val render = object : Fingerprint(
                    definingClass = component,
                    name = "A0m",
                    parameters = listOf("LX/05dH;"),
                    returnType = "LX/03Fb;",
                ) {}.method
                if (render.instructions.none {
                    (it as? WideLiteralInstruction)?.wideLiteral == 0x7f136d26L
                }) {
                    throw PatchException("Unexpected 448 repost component: $component")
                }
                render.addInstructionsWithLabels(
                    0,
                    """
                        $PREF_CALL
                        move-result v0
                        if-eqz v0, :nativeRepostComponent
                        const/4 v0, 0x0
                        return-object v0
                    """.trimIndent(),
                    ExternalLabel("nativeRepostComponent", render.getInstruction(0)),
                )
            }

            // The reels count is rendered independently of its icon. Use its
            // existing null result to remove the count and its layout space.
            val repostCount = object : Fingerprint(
                definingClass = "LX/060c;",
                name = "A0C",
                parameters = listOf("LX/0D0b;", "J", "J"),
                returnType = "LX/004H;",
            ) {}.method
            if (repostCount.instructions.none {
                (it as? WideLiteralInstruction)?.wideLiteral == 0x7f0b35b1L
            } || repostCount.instructions.none {
                it.getReference<MethodReference>()?.toString() == "LX/0Lco;->DCS()LX/0Iai;"
            }) {
                throw PatchException("Unexpected 448 repost count renderer")
            }
            repostCount.addInstructionsWithLabels(
                0,
                """
                    $PREF_CALL
                    move-result v0
                    if-eqz v0, :nativeRepostCount
                    const/4 v0, 0x0
                    return-object v0
                """.trimIndent(),
                ExternalLabel("nativeRepostCount", repostCount.getInstruction(0)),
            )

            // 448 builds the current repost control from a UI state use case.
            // The legacy media-notes flag below does not govern this path.
            val repostState = object : Fingerprint(
                definingClass = "LX/0AOW;",
                name = "invoke",
                parameters = emptyList(),
                returnType = "Ljava/lang/Object;",
                strings = listOf(
                    "android_purge_26_q3_RepostButtonUseCase_getUiState",
                    "android_purge_26_q3_RepostButtonUseCase_isEligibleForRepostButton",
                ),
            ) {}.method
            val nativeInstructions = repostState.instructions.toList()
            val eligibility = nativeInstructions.indices.single { index ->
                nativeInstructions[index].getReference<StringReference>()?.string ==
                    "android_purge_26_q3_RepostButtonUseCase_isEligibleForRepostButton"
            }
            val emptyState = nativeInstructions.indices.single { index ->
                nativeInstructions[index].getReference<FieldReference>()?.toString() ==
                    "LX/09FH;->A00:LX/09FH;"
            }
            if (nativeInstructions[eligibility + 1].getReference<MethodReference>()?.toString() !=
                "LX/03ad;->A00(Ljava/lang/String;)V" ||
                nativeInstructions[eligibility + 2].getReference<FieldReference>()?.toString() !=
                "LX/06QC;->A04:Lcom/instagram/common/session/UserSession;" ||
                nativeInstructions[emptyState + 1].opcode != Opcode.RETURN_OBJECT) {
                throw PatchException("Unexpected 448 repost eligibility path")
            }
            // v0 is the consumed trace string, overwritten by native code next.
            // Use the existing empty state so all unrelated lambda cases survive.
            repostState.addInstructionsWithLabels(
                eligibility + 2,
                """
                    $PREF_CALL
                    move-result v0
                    if-eqz v0, :repostNative
                    goto/16 :repostEmpty
                """.trimIndent(),
                ExternalLabel("repostNative", nativeInstructions[eligibility + 2]),
                ExternalLabel("repostEmpty", nativeInstructions[emptyState]),
            )

            FeedResponseMediaParserFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    $PREF_CALL
                    move-result v0
                    if-eqz v0, :piko
                    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    return-object v0
                    """.trimMargin(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            // If it's trying to get the value for our field of interest via the Pando native library,
            // force the value to false instead
            LiveTreeGetOptionalBooleanFingerprint.method.addInstructions(
                0,
                """
                const v0, $hashedFieldInteger
                if-ne p1, v0, :nopatch
                $PREF_CALL
                move-result v0
                if-eqz v0, :nopatch
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
                :nopatch
                nop
        """,
            )

            enableSettings("hideReshareButton")
        }
    }
