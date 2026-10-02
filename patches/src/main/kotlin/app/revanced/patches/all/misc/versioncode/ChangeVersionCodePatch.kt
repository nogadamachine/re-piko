package app.revanced.patches.all.misc.versioncode

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.getNode
import org.w3c.dom.Element

@Suppress("unused")
val changeVersionCodePatch =
    resourcePatch(
        name = "버전 코드 변경",
        description = "앱 버전 코드를 바꾸어 스토어 자동 업데이트를 막거나 이전 앱 버전으로 설치할 수 있게 합니다.",
        default = true,
    ) {
        val versionCode by intOption(
            key = "versionCode",
            default = Int.MAX_VALUE,
            values =
                mapOf(
                    "최솟값" to 1,
                    "최댓값" to Int.MAX_VALUE,
                ),
            title = "버전 코드",
            description = "적용할 버전 코드입니다. 최댓값을 사용하면 스토어 업데이트를 막고 이전 앱 버전으로 설치할 수 있습니다.",
            required = true,
        ) { versionCode -> versionCode!! >= 1 }

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            document("AndroidManifest.xml").use { document ->
                val manifestElement = document.getNode("manifest") as Element
                manifestElement.setAttribute("android:versionCode", "$versionCode")
            }
        }
    }
