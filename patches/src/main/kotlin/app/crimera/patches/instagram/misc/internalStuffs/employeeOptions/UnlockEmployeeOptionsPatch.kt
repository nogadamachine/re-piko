/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.internalStuffs.employeeOptions

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.internalStuffs.checkMappingsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val unlockEmployeeOptionsPatch =
    bytecodePatch(
        name = "직원용 디버깅 옵션 열기",
        description = "직원용 디버깅 옵션에 접근할 수 있게 합니다.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            hookFlagsPatch,
            checkMappingsPatch,
        )
        execute {

            addFlags("employeeOptionsFlags")
            enableSettings("unlockEmployeeOptions")
        }
    }
