package dev.tap.app.transfer

import android.content.Context
import dev.tap.app.contacts.ContactCard
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Universal Embedded Local HTTP Server.
 *
 * Allows ANY device (iPhone, Android, Windows, Mac, Linux) to receive contact cards
 * or files via standard web browser (Safari, Chrome, Firefox) without installing TAP!
 *
 * Built purely on standard Android `java.net.ServerSocket`.
 */
class LocalHttpServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private val threadPool = Executors.newFixedThreadPool(4)

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var serverUrl: String = ""
        private set

    fun start(
        hostAddress: String,
        port: Int = DEFAULT_WEB_PORT,
        contactCard: ContactCard? = null,
        files: List<SelectedFile> = emptyList()
    ): Result<String> {
        stop()
        return try {
            val ss = ServerSocket(port)
            serverSocket = ss
            isRunning = true
            serverUrl = "http://$hostAddress:$port/"

            threadPool.execute {
                while (isRunning && !ss.isClosed) {
                    try {
                        val client = ss.accept()
                        threadPool.execute {
                            handleClient(client, contactCard, files)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            Result.success(serverUrl)
        } catch (e: Exception) {
            stop()
            Result.failure(e)
        }
    }

    fun stop() {
        try {
            isRunning = false
            serverSocket?.close()
            serverSocket = null
            serverUrl = ""
        } catch (_: Exception) {}
    }

    private fun handleClient(client: Socket, card: ContactCard?, files: List<SelectedFile>) {
        try {
            client.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            if (method != "GET") {
                val out = client.getOutputStream()
                out.write("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
                return
            }

            val out = client.getOutputStream()
            when {
                path == "/" || path.startsWith("/?") -> {
                    val html = buildHtml(card, files)
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.write(bytes)
                    out.flush()
                }
                path == "/contact.vcf" && card != null -> {
                    val vcard = card.toVCard3(filterSelected = true).toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/vcard; charset=utf-8\r\nContent-Disposition: attachment; filename=\"contact.vcf\"\r\nContent-Length: ${vcard.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.write(vcard)
                    out.flush()
                }
                path.startsWith("/file/") -> {
                    val indexStr = path.removePrefix("/file/").substringBefore("?")
                    val index = indexStr.toIntOrNull()
                    if (index != null && index in files.indices) {
                        val file = files[index]
                        val stream: InputStream? = try {
                            context.contentResolver.openInputStream(file.uri)
                        } catch (_: Exception) { null }

                        if (stream != null) {
                            val header = "HTTP/1.1 200 OK\r\nContent-Type: ${file.mimeType}\r\nContent-Disposition: attachment; filename=\"${file.name}\"\r\nContent-Length: ${file.sizeBytes}\r\nConnection: close\r\n\r\n"
                            out.write(header.toByteArray(Charsets.UTF_8))
                            val buffer = ByteArray(64 * 1024)
                            stream.use { input ->
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                }
                            }
                            out.flush()
                        } else {
                            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    } else {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
                else -> {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun buildHtml(contactCard: ContactCard?, files: List<SelectedFile>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
        sb.append("<title>TAP — Universal Sharing</title>")
        sb.append("<style>")
        sb.append("body { background-color: #090B10; color: #E8ECEF; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 24px; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 90vh; }")
        sb.append(".card { background: #131722; border: 1px solid #232B3E; border-radius: 20px; max-width: 480px; width: 100%; padding: 28px; box-sizing: border-box; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }")
        sb.append(".badge { display: inline-block; background: rgba(183,243,107,0.15); color: #B7F36B; padding: 6px 14px; border-radius: 20px; font-weight: 700; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 16px; }")
        sb.append("h1 { color: #FFFFFF; font-size: 24px; margin: 0 0 8px 0; }")
        sb.append("p.sub { color: #8F9CAE; font-size: 14px; margin: 0 0 24px 0; }")
        sb.append(".btn { display: block; background: #B7F36B; color: #090B10; font-weight: 700; font-size: 16px; padding: 14px 20px; border-radius: 12px; text-decoration: none; margin: 12px 0; transition: transform 0.1s ease; }")
        sb.append(".btn:active { transform: scale(0.98); }")
        sb.append(".btn-secondary { background: #1D2333; color: #E8ECEF; border: 1px solid #2B354D; }")
        sb.append(".field { text-align: left; background: #0E121A; padding: 12px 16px; border-radius: 10px; margin-bottom: 8px; font-size: 14px; }")
        sb.append(".field-label { color: #728096; font-size: 12px; margin-bottom: 2px; }")
        sb.append(".field-value { color: #FFFFFF; font-weight: 500; word-break: break-all; }")
        sb.append(".footer { color: #4E596D; font-size: 12px; margin-top: 24px; }")
        sb.append("</style></head><body>")

        sb.append("<div class='card'>")
        sb.append("<div class='badge'>TAP Universal</div>")

        if (contactCard != null) {
            sb.append("<h1>${escape(contactCard.displayName.ifBlank { "Contact Card" })}</h1>")
            if (contactCard.jobTitle.isNotBlank() || contactCard.company.isNotBlank()) {
                sb.append("<p class='sub'>${escape(contactCard.jobTitle)} at ${escape(contactCard.company)}</p>")
            }

            if (contactCard.phone.isNotBlank()) {
                sb.append("<div class='field'><div class='field-label'>PHONE</div><div class='field-value'>${escape(contactCard.phone)}</div></div>")
            }
            if (contactCard.email.isNotBlank()) {
                sb.append("<div class='field'><div class='field-label'>EMAIL</div><div class='field-value'>${escape(contactCard.email)}</div></div>")
            }
            if (contactCard.website.isNotBlank()) {
                sb.append("<div class='field'><div class='field-label'>WEBSITE</div><div class='field-value'><a href='${escape(contactCard.website)}' style='color:#B7F36B;'>${escape(contactCard.website)}</a></div></div>")
            }

            sb.append("<a class='btn' href='/contact.vcf' download='contact.vcf'>📥 Save Contact Card (.vcf)</a>")
        }

        if (files.isNotEmpty()) {
            sb.append("<h2 style='font-size:18px; margin:24px 0 12px 0;'>Shared Files (${files.size})</h2>")
            files.forEachIndexed { idx, f ->
                val sizeMb = String.format("%.2f MB", f.sizeBytes / (1024.0 * 1024.0))
                sb.append("<a class='btn btn-secondary' href='/file/$idx' download='${escape(f.name)}'>Download ${escape(f.name)} ($sizeMb)</a>")
            }
        }

        sb.append("<div class='footer'>No app installation needed • Encrypted local transport</div>")
        sb.append("</div></body></html>")
        return sb.toString()
    }

    private fun escape(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    companion object {
        const val DEFAULT_WEB_PORT = 8989
    }
}
