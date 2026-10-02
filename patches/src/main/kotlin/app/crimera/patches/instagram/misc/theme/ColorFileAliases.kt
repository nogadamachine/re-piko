package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.patch.ResourcePatchContext
import org.w3c.dom.Document
import org.w3c.dom.Element

// The decoder exposes shared binary color files as path-valued <color> entries.
// Re-encoding a modified values file leaves those paths pointing at pre-rename
// filenames. Give each entry its own XML file, retaining its exact qualifiers.
internal fun Document.materializeColorFileAliases(
    valuesDirectory: String,
    copyFile: (source: String, target: String) -> Unit,
): Int {
    require(valuesDirectory == "values" || valuesDirectory.startsWith("values-"))
    val colors = getElementsByTagName("color")
    val aliases = (0 until colors.length).mapNotNull { colors.item(it) as? Element }
        .filter { it.textContent.trim().startsWith("res/") }
    aliases.forEach { color ->
        val source = color.textContent.trim()
        val name = color.getAttribute("name")
        require(Regex("res/color(?:-[A-Za-z0-9+-]+)?/[A-Za-z0-9_.]+\\.xml").matches(source)) {
            "Unsupported color file alias: $source"
        }
        require(Regex("[A-Za-z0-9_]+").matches(name))
        val qualifiers = valuesDirectory.removePrefix("values")
        val target = "res/color$qualifiers/$name.xml"
        copyFile(source, target)
        color.parentNode.removeChild(color)
    }
    return aliases.size
}

internal fun ResourcePatchContext.preserveColorFileAliases() {
    listOf("values", "values-night").forEach { directory ->
        val path = "res/$directory/colors.xml"
        if (!get(path).isFile) return@forEach
        document(path).use { document ->
            document.materializeColorFileAliases(directory) { sourcePath, targetPath ->
                val source = get(sourcePath)
                check(source.isFile) { "Missing source color file: $sourcePath" }
                val target = get(targetPath, copy = false)
                check(!target.exists()) { "Conflicting color file: $targetPath" }
                target.parentFile.mkdirs()
                source.copyTo(target)
            }
        }
    }
}
