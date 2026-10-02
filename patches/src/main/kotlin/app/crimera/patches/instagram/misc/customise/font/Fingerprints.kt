/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.font

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The typeface repository method every classic text component goes through to resolve
 * a font, matched on the systrace section name it opens.
 */
internal object TypefaceRepositoryLoadFingerprint : Fingerprint(
    returnType = "Landroid/graphics/Typeface;",
    strings = listOf("TypefaceRepository:load_typeface"),
)

/**
 * Compose resolves fonts on its own, so nothing drawn by Compose - the settings screens,
 * the direct inbox - reaches the typeface repository. This is the platform adapter that
 * produces the typeface for every font family, resource backed or not, and wraps it in a
 * resolution result rather than returning it.
 */
internal object ComposePlatformTypefacesFingerprint : Fingerprint(
    strings = listOf("null cannot be cast to non-null type androidx.compose.ui.text.platform.AndroidTypeface"),
)

/**
 * The story text styles of the older story editor, which resolve their typeface from an enum
 * of style names rather than from a font descriptor.
 */
internal object LegacyStoryFontFingerprint : Fingerprint(
    strings = listOf("SIGNATURE", "TYPEWRITER", "LITERATURE"),
)

/** React Native's registration of the "Optimistic VF App Lite" variable font. */
internal object ReactNativeFontRegistrationFingerprint : Fingerprint(
    strings = listOf("Optimistic VF App Lite "),
    custom = { method, _ -> method.parameters.isEmpty() && method.returnType.startsWith("L") },
)

/** Native Prism classification compares three cached platform fonts before resolving its font. */
internal object NativeFontClassificationFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf(TYPEFACE_CLASS, "Z"),
    returnType = "Ljava/lang/Integer;",
    custom = { method, cls ->
        val code = method.implementation?.instructions?.toList().orEmpty()
        code.count { it.opcode == Opcode.SGET_OBJECT &&
            it.getReference<FieldReference>()?.type == TYPEFACE_CLASS } == 3 &&
            code.count { it.getReference<MethodReference>()?.let { ref ->
                ref.name == "areEqual" && ref.parameterTypes ==
                    listOf("Ljava/lang/Object;", "Ljava/lang/Object;") && ref.returnType == "Z"
            } == true } == 3 &&
            cls.methods.any { candidate -> candidate.implementation?.instructions?.any {
                it.getReference<StringReference>()?.string == "sans-serif-medium"
            } == true }
    },
)
