/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton.userProfileActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val profileMoreOptionsPatch =
    bytecodePatch(
        name = "프로필 추가 메뉴",
        description = "사용자 이름 복사, 프로필 사진 저장 등의 추가 메뉴를 제공합니다.",
    ) {
        dependsOn(settingsPatch, userProfileButtonPatch, userProfileActionBarButtonPatch, userDataEntity, instagramDialogBoxEntity)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("moreOptionsOnProfile")
        }
    }
