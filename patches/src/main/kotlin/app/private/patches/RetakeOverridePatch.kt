package app.private.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.private.patches.shared.Constants.COMPATIBILITY_BEREAL_PRIVATE

/**
 * BeReal's PostDraftEntity constructor — the local database entity that holds
 * the retake count before the post is finalized and uploaded.
 *
 * Verified against BeReal 3.97.1: Ldefpackage/esc; is the PostDraftEntity
 * (Room table with retakeCounter column). The constructor takes 17 parameters (verified from user APK).
 */
// Verified against user APK 3.97.1 (3arch_1dpi): esc is PostDraft
object PostDraftEntityFingerprint : Fingerprint(
    definingClass = "Ldefpackage/esc;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "J",
        "J",
        "Ldefpackage/t2j;",
        "I",
        "Ljava/util/List;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "J",
        "Ldefpackage/s2j;",
        "Ldefpackage/s2j;",
        "Ldefpackage/s2j;",
        "Ldefpackage/s2j;",
        "Ldefpackage/teb;",
        "I",
        "Z",
    ),
)

/**
 * Private retake count override patch.
 *
 * Reads /sdcard/bereal_retakes.txt (single integer on one line, e.g. "0").
 * When the file exists, the patch substitutes its value for BeReal's retake
 * counter when constructing the PostDraftEntity.
 *
 * When the file is absent, BeReal's normal retake counting is untouched.
 *
 * PRIVATE PATCH — not for public distribution.
 */
@Suppress("unused")
val retakeOverridePatch = bytecodePatch(
    name = "Set retake count",
    description = "Reads /sdcard/bereal_retakes.txt and substitutes its integer value " +
        "for BeReal's retake counter. Only active when the file exists.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_BEREAL_PRIVATE)

    execute {
        PostDraftEntityFingerprint.method.apply {
            addInstructions(
                0,
                """
                const-string v0, "/sdcard/bereal_retakes.txt"
                new-instance v1, Ljava/io/File;
                invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v1}, Ljava/io/File;->exists()Z
                move-result v0
                if-eqz v0, :no_override
                new-instance v0, Ljava/io/BufferedReader;
                new-instance v1, Ljava/io/FileReader;
                const-string v2, "/sdcard/bereal_retakes.txt"
                invoke-direct {v1, v2}, Ljava/io/FileReader;-><init>(Ljava/lang/String;)V
                invoke-direct {v0, v1}, Ljava/io/BufferedReader;-><init>(Ljava/io/Reader;)V
                invoke-virtual {v0}, Ljava/io/BufferedReader;->readLine()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :no_override
                invoke-virtual {v0}, Ljava/lang/String;->trim()Ljava/lang/String;
                move-result-object v0
                invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
                move-result v0
                move p19, v0
                :no_override
                nop
                """.trimIndent(),
            )
        }
    }
}
