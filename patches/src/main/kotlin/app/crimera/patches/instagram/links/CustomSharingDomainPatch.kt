/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.AUDIO
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.HIGHLIGHT
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.PROFILE
import app.crimera.patches.instagram.links.shareLinks.hookShareLinks
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "공유 도메인 변경",
        description = "게시물, 릴스, 스토리를 공유할 때 사용할 도메인을 지정합니다.",
    ) {

        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            hookShareLinks("changeDomain", skip = setOf(PROFILE, AUDIO, HIGHLIGHT))

            enableSettings("customSharingDomain")
        }
    }
