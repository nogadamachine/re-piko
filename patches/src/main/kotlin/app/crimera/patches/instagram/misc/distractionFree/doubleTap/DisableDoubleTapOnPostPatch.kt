/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

object PostOnSingleTapConfirmedFingerprint : Fingerprint(
    definingClass = "LX/06Cc;",
    name = "onSingleTapConfirmed",
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
    // 448 moved interstitial logging into the single-tap helper.
    custom = { method, _ ->
        method.implementation?.instructions?.any {
            it.getReference<MethodReference>()?.toString() ==
                "LX/0AsP;->A00(Landroid/view/MotionEvent;LX/06Cb;)V"
        } == true
    },
)

private object FeedMediaDoubleTapFingerprint : Fingerprint(
    definingClass = "LX/06rQ;",
    name = "onDoubleTap",
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
    custom = { method, _ ->
        method.implementation?.instructions?.count {
            it.getReference<MethodReference>()?.toString() ==
                "LX/0Ftm;->FG8(Lcom/instagram/feed/media/Media;LX/09AU;LX/04nR;I)V"
        } == 1
    },
)

@Suppress("unused")
val disableDoubleTapOnPostPatch =
    bytecodePatch(
        description = "Disable double tap like on post",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // 448 dispatches feed and carousel double taps through this media listener.
            FeedMediaDoubleTapFingerprint.matchAll(1..1).single().method.addInstructions(
                0,
                DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapPost"),
            )

            PostOnSingleTapConfirmedFingerprint.apply {
                classDef.methods.first { it.name == "onDoubleTap" }.apply {
                    addInstructions(
                        0,
                        DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapPost"),
                    )
                }
            }
        }
    }
