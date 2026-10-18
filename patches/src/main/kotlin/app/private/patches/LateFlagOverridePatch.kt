package app.private.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.private.patches.shared.Constants.COMPATIBILITY_BEREAL_PRIVATE

/**
 * Finds esc via its toString() which contains "MomentPost(".
 * Then gets the 17-param constructor from that class.
 */
object EscToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    strings = listOf("MomentPost("),
)

/**
 * Private late-flag override patch.
 *
 * Reads /sdcard/bereal_late.txt (single line: "true" or "false").
 * When the file exists, the patch substitutes its value for BeReal's
 * calculated isLate flag when constructing the post draft.
 *
 * When the file is absent, BeReal's normal late calculation is untouched.
 *
 * PRIVATE PATCH — not for public distribution.
 */
@Suppress("unused")
val lateFlagOverridePatch = bytecodePatch(
    name = "Remove late flag",
    description = "Reads /sdcard/bereal_late.txt and substitutes its value " +
        "for BeReal's late flag. Only active when the file exists.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_BEREAL_PRIVATE)

    execute {
        // Find esc via toString, then get the 17-param constructor
        val classDef = EscToStringFingerprint.classDef

        val constructors = classDef.methods
            .filter { it.name == "<init>" }

        println("Matched class: ${classDef.type}")
        println("Constructors found: ${constructors.size}")

        constructors.forEach { constructor ->
            println(
                "Constructor: ${constructor.parameterTypes.size} parameters; " +
                constructor.parameterTypes.joinToString()
            )
        }

        val details = constructors
            .filter { it.parameterTypes.size == 17 }
            .mapIndexed { index, constructor ->
                "Constructor #${index + 1}:\n" +
                    constructor.parameterTypes.mapIndexed { i, type ->
                        "  [$i] $type"
                    }.joinToString("\n")
            }
            .joinToString("\n---\n")

        throw Exception(
            "DEBUG: ${classDef.type}\n" +
                "17-parameter constructors:\n$details"
        )

        method.apply {
            addInstructions(
                0,
                """
                const-string v0, "/sdcard/bereal_late.txt"
                new-instance v1, Ljava/io/File;
                invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v1}, Ljava/io/File;->exists()Z
                move-result v0
                if-eqz v0, :no_override
                new-instance v0, Ljava/io/BufferedReader;
                new-instance v1, Ljava/io/FileReader;
                const-string v2, "/sdcard/bereal_late.txt"
                invoke-direct {v1, v2}, Ljava/io/FileReader;-><init>(Ljava/lang/String;)V
                invoke-direct {v0, v1}, Ljava/io/BufferedReader;-><init>(Ljava/io/Reader;)V
                invoke-virtual {v0}, Ljava/io/BufferedReader;->readLine()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :no_override
                invoke-virtual {v0}, Ljava/lang/String;->trim()Ljava/lang/String;
                move-result-object v0
                const-string v1, "true"
                invoke-virtual {v0, v1}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
                move-result v1
                if-nez v1, :set_true
                const-string v1, "false"
                invoke-virtual {v0, v1}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
                move-result v1
                if-eqz v1, :no_override
                const/4 p20, 0x0
                goto :no_override
                :set_true
                const/4 p20, 0x1
                :no_override
                nop
                """.trimIndent(),
            )
        }
    }
}
