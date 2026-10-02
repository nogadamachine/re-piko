/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

internal const val DOUBLE_TAP_PREF_DESCRIPTOR = """
                        $PREF_CALL_DESCRIPTOR->%s()Z
                        move-result v0
                        if-eqz v0, :piko
                        const v0,0x1
                        return v0
                        :piko
                        nop"""

@Suppress("unused")
val disableDoubleTapPatch =
    bytecodePatch(
        name = "두 번 눌러 좋아요 차단",
        description = "게시물, 릴스, 댓글, 메시지를 두 번 눌러 좋아요를 보내는 동작을 차단합니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            disableDoubleTapOnPostPatch,
            disableDoubleTapOnReelPatch,
            disableDoubleTapOnCommentPatch,
            disableDoubleTapOnMessagePatch,
        )
        execute {
            enableSettings("disableDoubleTapLike")
        }
    }
