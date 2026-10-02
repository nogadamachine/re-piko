/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton.inboxActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val viewLiveAnonymouslyPatch =
    bytecodePatch(
        name = "라이브 익명 시청",
        description = "라이브 시청 알림을 차단하는 설정을 추가합니다.",
    ) {
        dependsOn(settingsPatch, interceptUriPatch, chatActionBarButtonPatch, inboxActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("viewLiveAnonymously")
        }
    }
