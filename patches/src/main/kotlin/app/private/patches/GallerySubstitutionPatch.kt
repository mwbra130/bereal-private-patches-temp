package app.private.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.private.patches.shared.Constants.COMPATIBILITY_BEREAL_PRIVATE

/**
 * BeReal's upload multipart builder — the single chokepoint where captured
 * images become upload parts.
 *
 * Verified against BeReal 3.97.1: Ldefpackage/ytd;->a(Ljava/io/File;Ldefpackage/xr8;)Lokhttp3/MultipartBody\$Part;
 * is the ONLY method app-wide that creates "binary-content" multipart parts.
 * Called once per image (primary/back and secondary/front captures).
 */
object UploadMultipartFingerprint : Fingerprint(
    definingClass = "Ldefpackage/ytd;",
    name = "a",
    returnType = "Lokhttp3/MultipartBody\$Part;",
    parameters = listOf("Ljava/io/File;", "Ldefpackage/xr8;"),
    strings = listOf("binary-content"),
)

/**
 * Private gallery substitution patch (broken-camera workaround).
 *
 * When /sdcard/bereal_gallery_primary.jpg exists, it replaces the first
 * image upload (back camera). When /sdcard/bereal_gallery_secondary.jpg
 * exists, it replaces the second image upload (front camera).
 *
 * When neither file exists, BeReal's normal camera flow is untouched.
 *
 * PRIVATE PATCH — not for public distribution.
 */
@Suppress("unused")
val gallerySubstitutionPatch = bytecodePatch(
    name = "Use gallery photo",
    description = "Replaces camera-captured images with gallery files from " +
        "/sdcard/bereal_gallery_primary.jpg and /sdcard/bereal_gallery_secondary.jpg. " +
        "Only active when the files exist.",
) {
    compatibleWith(COMPATIBILITY_BEREAL_PRIVATE)

    execute {
        UploadMultipartFingerprint.method.apply {
            addInstructions(
                0,
                """
                const-string v0, "/sdcard/bereal_gallery_primary.jpg"
                new-instance v1, Ljava/io/File;
                invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v1}, Ljava/io/File;->exists()Z
                move-result v0
                if-eqz v0, :check_secondary
                const-string v0, "/sdcard/.bereal_primary_used"
                new-instance v2, Ljava/io/File;
                invoke-direct {v2, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v2}, Ljava/io/File;->exists()Z
                move-result v0
                if-nez v0, :check_secondary
                move-object p1, v1
                :try_create_marker
                new-instance v0, Ljava/io/File;
                const-string v2, "/sdcard/.bereal_primary_used"
                invoke-direct {v0, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v0}, Ljava/io/File;->createNewFile()Z
                goto :done
                :check_secondary
                const-string v0, "/sdcard/bereal_gallery_secondary.jpg"
                new-instance v1, Ljava/io/File;
                invoke-direct {v1, v0}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v1}, Ljava/io/File;->exists()Z
                move-result v0
                if-eqz v0, :done
                move-object p1, v1
                new-instance v0, Ljava/io/File;
                const-string v2, "/sdcard/.bereal_primary_used"
                invoke-direct {v0, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V
                invoke-virtual {v0}, Ljava/io/File;->delete()Z
                :done
                nop
                """.trimIndent(),
            )
        }
    }
}
