package app.private.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.private.patches.shared.Constants.COMPATIBILITY_BEREAL_PRIVATE

/**
 * OkHttp request builder — intercepts outgoing requests.
 *
 * Strategy: BeReal reports screenshots via POST to "content/screenshots".
 * Rather than finding the obfuscated detector, we hook OkHttp's Request.Builder
 * and drop any request whose URL contains "content/screenshots".
 *
 * Verified against BeReal 3.97.1: "content/screenshots" exists in classes3.dex
 * string pool.
 */
object OkHttpRequestBuilderFingerprint : Fingerprint(
    definingClass = "Lokhttp3/Request\$Builder;",
    name = "url",
    returnType = "Lokhttp3/Request\$Builder;",
    parameters = listOf("Ljava/lang/String;"),
)

/**
 * Private screenshot notification suppression patch.
 *
 * Intercepts OkHttp requests and silently drops any POST to BeReal's
 * screenshot reporting endpoint ("content/screenshots"). Screenshots still
 * work locally — the image is captured — but the server is never notified,
 * so the poster sees no screenshot indicator.
 *
 * PRIVATE PATCH — not for public distribution.
 */
@Suppress("unused")
val screenshotBypassPatch = bytecodePatch(
    name = "Hide screenshot notification",
    description = "Blocks BeReal's screenshot reports from reaching the server. " +
        "Screenshots work normally locally, but the poster is not notified.",
) {
    compatibleWith(COMPATIBILITY_BEREAL_PRIVATE)

    execute {
        OkHttpRequestBuilderFingerprint.method.apply {
            addInstructions(
                0,
                """
                const-string v0, "content/screenshots"
                invoke-virtual {p1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-eqz v0, :no_block
                const-string p1, "https://127.0.0.1/screenshot-blocked"
                :no_block
                nop
                """.trimIndent(),
            )
        }
    }
}
