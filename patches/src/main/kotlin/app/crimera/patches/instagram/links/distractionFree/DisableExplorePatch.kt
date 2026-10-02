/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableExplorePatch =
    bytecodePatch(
        name = "탐색 화면 차단",
        description = "탐색 콘텐츠를 새로 불러오는 요청을 차단합니다. 이미 불러온 콘텐츠는 남을 수 있습니다.",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("disableExplore")
        }
    }
