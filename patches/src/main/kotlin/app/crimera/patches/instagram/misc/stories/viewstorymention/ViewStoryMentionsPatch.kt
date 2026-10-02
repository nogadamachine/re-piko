/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.viewstorymention

import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val viewStoryMentionsPatch =
    bytecodePatch(
        name = "스토리 언급 확인",
        description = "스토리의 보이는 언급과 숨겨진 언급을 확인하는 메뉴를 추가합니다.",
    ) {
        dependsOn(settingsPatch, handleStoryButtonPatch, userDataEntity, mediaDataEntity, instagramDialogBoxEntity, videoDataEntity)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            enableSettings("viewStoryMentions")
        }
    }
