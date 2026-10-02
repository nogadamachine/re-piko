/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val SIMPLE_ON_GESTURE_LISTENER_CLASS = "Landroid/view/GestureDetector\$SimpleOnGestureListener;"

internal object MessageGestureDetectorInitFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    parameters = listOf(SIMPLE_ON_GESTURE_LISTENER_CLASS, "Landroid/view/View;", "Landroid/widget/TextView;", "Z"),
    custom = { _, classDef ->
        classDef.superclass == SIMPLE_ON_GESTURE_LISTENER_CLASS
    },
)

internal object MessageGestureOnDoubleTapFingerprint : Fingerprint(
    classFingerprint = MessageGestureDetectorInitFingerprint,
    name = "onDoubleTap",
)

// 448 has reaction paths that bypass the TextView gesture wrapper.
// At the dispatcher, parameter 3 distinguishes double_tap from action_menu.
internal object MessageReactionDispatchFingerprint : Fingerprint(
    definingClass = "LX/0Qjy;",
    name = "Ff8",
    parameters = listOf(
        "LX/07lV;", "Lcom/instagram/model/direct/messageid/MessageIdentifier;",
        "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/util/List;", "Z",
    ),
    returnType = "V",
)

@Suppress("unused")
val disableDoubleTapOnMessagePatch =
    bytecodePatch(
        description = "Disable double tap like on messages",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            MessageGestureOnDoubleTapFingerprint.method
                .addInstructions(
                    0,
                    DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapMessage"),
                )

            MessageReactionDispatchFingerprint.matchAll(1..1).single().method.apply {
                val callbacks = instructions.mapNotNull { it.getReference<MethodReference>() }
                    .count {
                        it.definingClass == "LX/0Qjy;" && it.name == "A00" &&
                            it.parameterTypes.size == 10 && it.returnType == "V"
                    }
                if (callbacks != 1 || implementation!!.registerCount != 24) {
                    throw PatchException("Unexpected 448 message reaction dispatcher")
                }
                // v0 is a native local initialized before every original use.
                // Keep p3 intact, and enter the original first instruction when allowed.
                addInstructions(0, """
                    invoke-static/range {p3 .. p3}, Lapp/morphe/extension/instagram/utils/Pref;->shouldBlockMessageReaction(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :original_reaction
                    return-void
                    :original_reaction
                    nop
                """)
            }
        }
    }
