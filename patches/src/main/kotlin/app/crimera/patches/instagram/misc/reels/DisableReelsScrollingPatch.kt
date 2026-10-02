package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object ClipsViewPagerImplGetViewAtIndexFingerprint : Fingerprint(
    strings = listOf("ClipsViewPagerImpl_getViewAtIndex")
)

private object ClipsSwipeRefreshLayoutOnInterceptTouchEventFingerprint : Fingerprint (
    parameters = listOf("Landroid/view/MotionEvent;"),
    definingClass = "Linstagram/features/clips/viewer/ui/ClipsSwipeRefreshLayout;"
)

private object ViewPagerInputSetterFingerprint : Fingerprint(
    definingClass = "Landroidx/viewpager2/widget/ViewPager2;",
    name = "setUserInputEnabled",
    parameters = listOf("Z"),
    returnType = "V",
)

@Suppress("unused")
val disableReelsScrollingPatch = bytecodePatch(
    name = "릴스 연속 넘기기 차단",
    description = "다음 릴스로 계속 넘기는 동작을 차단합니다. 새로 설치한 앱에서는 안내 애니메이션이 잠시 나타날 수 있습니다.",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        val inputField = ViewPagerInputSetterFingerprint.matchAll(1..1).single()
            .method.instructions.first().let {
                if (it.opcode == Opcode.IPUT_BOOLEAN) it.getReference<FieldReference>() else null
            }
        if (inputField?.name != "A0A" || inputField.type != "Z" ||
            inputField.definingClass != "Landroidx/viewpager2/widget/ViewPager2;") {
            throw PatchException("Unexpected 448 ViewPager2 input field")
        }
        val viewPagerField = ClipsViewPagerImplGetViewAtIndexFingerprint.classDef.fields.singleOrNull {
            it.type == "Landroidx/viewpager2/widget/ViewPager2;"
        } ?: throw PatchException("Expected one Reels ViewPager2 field")

        // Track the native input state so a timed Focus Lock can restore an existing pager.
        ClipsViewPagerImplGetViewAtIndexFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $viewPagerField
                invoke-static { v0 }, Lapp/morphe/extension/instagram/patches/reels/ReelsScrolling;->update(Landroidx/viewpager2/widget/ViewPager2;)V
            """
        )

        // Return false in onInterceptTouchEvent to disable pull-to-refresh.
        ClipsSwipeRefreshLayoutOnInterceptTouchEventFingerprint.method.addInstructions(
            0,
            """
                ${PREF_CALL_DESCRIPTOR}->disableReelsScrolling()Z
                move-result v0
                if-eqz v0, :piko
                const/4 v0, 0x0
                return v0
                :piko
                nop
            """
        )

        enableSettings("disableReelsScrolling")
    }
}
