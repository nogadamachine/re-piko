/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.focusLock

import app.crimera.patches.instagram.links.distractionFree.disableExplorePatch
import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.navigation.navigationBarPatch
import app.crimera.patches.instagram.misc.reels.disableReelsScrollingPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val focusLockPatch =
    bytecodePatch(
        name = "집중 모드 잠금",
        description = "릴스 또는 탐색을 차단할 기간을 지정합니다. 잠금 중에는 해당 차단을 해제하거나 설정을 초기화하고 가져올 수 없습니다. 공유받은 릴스는 열 수 있습니다. 조기 해제에는 24시간의 대기 시간이 필요합니다.",
    ) {
        dependsOn(
            settingsPatch,
            interceptUriPatch,
            navigationBarPatch,
            disableReelsScrollingPatch,
            disableExplorePatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("focusLock")
        }
    }
