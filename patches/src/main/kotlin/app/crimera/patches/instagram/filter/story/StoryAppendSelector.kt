/*
 * IG448 Port: experimental changes based on crimera/piko, 2026-09-28.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Preserve the upstream NOTICE. This is not an official Piko release.
 */
package app.crimera.patches.instagram.filter.story

internal enum class StoryInstructionKind {
    OTHER, STRING, PARSER_INSTANCE, PARSE_OBJECT, OBJECT_RESULT,
    NULL_GUARD, COLLECTION_ADD, GOTO,
}

internal data class StoryInstruction(
    val kind: StoryInstructionKind,
    val register: Int = -1,
    val targetIndex: Int = -1,
    val receiverRegister: Int = -1,
)

internal data class StoryAppendHook(
    val guardIndex: Int,
    val itemRegister: Int,
    val resumeIndex: Int,
)

/**
 * ONLY for the audited 448 target. No legacy/pre-tray fallback is permitted.
 * Matching names alone do not establish a safe hook. Check the parser receiver,
 * object result, guard/add registers, and the shared backwards loop destination.
 * The caller normalizes only the 448 ReelResponseItem parser as PARSER_INSTANCE.
 * Unknown or ambiguous layouts fail closed. This does not verify Android types.
 */
internal fun selectStoryAppendHook(
    instructions: List<StoryInstruction>,
    trayIndex: Int,
): StoryAppendHook {
    require(trayIndex in instructions.indices &&
        instructions[trayIndex].kind == StoryInstructionKind.STRING) {
        "Invalid tray anchor"
    }
    val end = ((trayIndex + 1) until instructions.size).firstOrNull {
        instructions[it].kind == StoryInstructionKind.STRING
    } ?: instructions.size

    fun isGuardedAppend(index: Int): Boolean {
        if (index - 3 <= trayIndex || index + 2 >= end) return false
        val instance = instructions[index - 3]
        val parse = instructions[index - 2]
        val result = instructions[index - 1]
        val guard = instructions[index]
        val add = instructions[index + 1]
        val resume = instructions[index + 2]
        return instance.kind == StoryInstructionKind.PARSER_INSTANCE &&
            instance.register in 0..255 &&
            parse.kind == StoryInstructionKind.PARSE_OBJECT &&
            parse.receiverRegister == instance.register &&
            result.kind == StoryInstructionKind.OBJECT_RESULT &&
            guard.kind == StoryInstructionKind.NULL_GUARD &&
            guard.register in 0..255 && result.register == guard.register &&
            add.kind == StoryInstructionKind.COLLECTION_ADD &&
            add.register == guard.register &&
            add.receiverRegister in 0..65535 &&
            add.receiverRegister != guard.register &&
            resume.kind == StoryInstructionKind.GOTO &&
            guard.targetIndex in (trayIndex + 1) until (index - 3) &&
            resume.targetIndex == guard.targetIndex &&
            // No existing explicit branch may bypass the new filter and enter add.
            instructions.none { it.targetIndex == index + 1 }
    }
    val candidates = ((trayIndex + 1) until end).filter(::isGuardedAppend)
    require(candidates.size == 1) {
        "Expected one verified 448 story append, found ${candidates.size}; refusing to guess"
    }
    val index = candidates.single()
    return StoryAppendHook(index, instructions[index].register, index + 2)
}
