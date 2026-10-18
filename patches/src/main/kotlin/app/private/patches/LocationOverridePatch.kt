package app.private.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.private.patches.shared.Constants.COMPATIBILITY_BEREAL_PRIVATE

/**
 * Finds teb via its toString() which contains "Location(longitude=".
 * Then gets the (D,D) constructor from that class.
 */
object TebToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    strings = listOf("Location(longitude="),
)

/**
 * Private location override patch.
 *
 * Reads /sdcard/bereal_location.txt (single line: "latitude,longitude").
 * When the file exists, the patch substitutes its coordinates for BeReal's
 * location when constructing the post.
 *
 * When the file is absent, BeReal's normal location is untouched.
 *
 * PRIVATE PATCH — not for public distribution.
 */
@Suppress("unused")
val locationOverridePatch = bytecodePatch(
    name = "Change location",
    description = "Reads /sdcard/bereal_location.txt and substitutes its coordinates " +
        "for BeReal's location. Only active when the file exists.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_BEREAL_PRIVATE)

    execute {
        // Find teb via toString, then get the (D,D) constructor
        val classDef = TebToStringFingerprint.classDef
        val method = classDef.methods.singleOrNull {
            it.name == "<init>" && it.parameterTypes.map { p -> p.toString() } == listOf("D", "D")
        } ?: throw Exception("Change location: teb(D,D) constructor not found")

        method.apply {
            addInstructions(
                0,
                """
                const-string v0, "/sdcard/bereal_location.txt"
                new-instance v1, Ljava/io/File;
                invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v1}, Ljava/io/File;->exists()Z
                move-result v0
                if-eqz v0, :no_override
                new-instance v0, Ljava/io/BufferedReader;
                new-instance v1, Ljava/io/FileReader;
                const-string v2, "/sdcard/bereal_location.txt"
                invoke-direct {v1, v2}, Ljava/io/FileReader;-><init>(Ljava/lang/String;)V
                invoke-direct {v0, v1}, Ljava/io/BufferedReader;-><init>(Ljava/io/Reader;)V
                invoke-virtual {v0}, Ljava/io/BufferedReader;->readLine()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :no_override
                invoke-virtual {v0}, Ljava/lang/String;->trim()Ljava/lang/String;
                move-result-object v0
                const-string v1, ","
                invoke-virtual {v0, v1}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;
                move-result-object v0
                array-length v1, v0
                const/4 v2, 0x2
                if-ne v1, v2, :no_override
                const/4 v1, 0x0
                aget-object v1, v0, v1
                invoke-static {v1}, Ljava/lang/Double;->parseDouble(Ljava/lang/String;)D
                move-result-wide p1
                const/4 v1, 0x1
                aget-object v0, v0, v1
                invoke-static {v0}, Ljava/lang/Double;->parseDouble(Ljava/lang/String;)D
                move-result-wide p3
                :no_override
                nop
                """.trimIndent(),
            )
        }
    }
}
