package com.heysia.app.a11y

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityNodeInfo
import com.heysia.app.actions.AppLauncher
import com.heysia.app.actions.Screens
import com.heysia.app.brain.Fuzzy
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.util.Locale

/** Sends WhatsApp and Messenger messages by filling in the chat and pressing Send for you. */
class Messaging(private val context: Context, private val apps: AppLauncher) {

    enum class Result { Sent, OpenedOnly, AppMissing, Failed }

    private val robot: ScreenRobot? get() = SiaAccessibilityService.instance?.let { ScreenRobot(it) }

    // ---------- WhatsApp ----------

    fun whatsAppInstalled(): Boolean = whatsAppPackage() != null

    /** Opens the chat with [text] typed in, then presses Send if the accessibility service is on. */
    suspend fun sendWhatsApp(phoneNumber: String, text: String): Result {
        val pkg = whatsAppPackage() ?: return Result.AppMissing
        val digits = internationalDigits(phoneNumber)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digits&text=" + URLEncoder.encode(text, "UTF-8"))
        if (!Screens.open(context, Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))) return Result.Failed

        val robot = robot ?: return Result.OpenedOnly
        val send = robot.waitFor(WAIT_MS) { node ->
            node.viewIdResourceName == "$pkg:id/send" ||
                (node.packageName == pkg && node.contentDescription?.toString().equals("Send", ignoreCase = true))
        } ?: return Result.OpenedOnly
        return if (robot.tap(send)) Result.Sent else Result.OpenedOnly
    }

    private fun whatsAppPackage(): String? = WHATSAPP_PACKAGES.firstOrNull { apps.isInstalled(it) }

    /** "01712-345678" -> "8801712345678" using your SIM's country, as WhatsApp needs. */
    private fun internationalDigits(number: String): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val country = (tm.simCountryIso.ifBlank { tm.networkCountryIso }.ifBlank { Locale.getDefault().country })
            .uppercase(Locale.US)
        val e164 = PhoneNumberUtils.formatNumberToE164(number, country)
        return (e164 ?: number).filter { it.isDigit() }
    }

    // ---------- Messenger ----------

    fun messengerInstalled(): Boolean = apps.isInstalled(MESSENGER)

    /**
     * Messenger has no way for other apps to start a chat, so Sia does it on screen:
     * search the name, open the chat, type the message, press Send.
     */
    suspend fun sendMessenger(name: String, text: String): Result {
        if (!messengerInstalled()) return Result.AppMissing
        val robot = robot ?: return Result.Failed
        if (!apps.open(MESSENGER)) return Result.Failed

        val search = robot.waitFor(LONG_WAIT_MS) { inMessenger(it) && it.isClickable && labelHas(it, "search") }
            ?: return Result.Failed
        robot.tap(search)
        val field = robot.waitFor(WAIT_MS) { inMessenger(it) && it.isEditable } ?: return Result.Failed
        robot.type(field, name)
        delay(SEARCH_DELAY_MS)

        // Pick the result whose name sounds most like what you said.
        val results = robot.findAll { inMessenger(it) && !it.isEditable && ScreenRobot.label(it).isNotBlank() }
        val person = results.maxByOrNull { Fuzzy.score(name, ScreenRobot.label(it)) }
            ?.takeIf { Fuzzy.score(name, ScreenRobot.label(it)) >= 0.75 } ?: return Result.Failed
        if (!robot.tap(person)) return Result.Failed

        val box = robot.waitFor(LONG_WAIT_MS) { inMessenger(it) && it.isEditable } ?: return Result.Failed
        robot.type(box, text)
        val send = robot.waitFor(WAIT_MS) { inMessenger(it) && labelHas(it, "send") } ?: return Result.OpenedOnly
        return if (robot.tap(send)) Result.Sent else Result.OpenedOnly
    }

    private fun inMessenger(node: AccessibilityNodeInfo) = node.packageName == MESSENGER

    private fun labelHas(node: AccessibilityNodeInfo, word: String) =
        ScreenRobot.label(node).lowercase().split(Regex("[^a-z]+")).contains(word)

    private companion object {
        val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
        const val MESSENGER = "com.facebook.orca"
        const val WAIT_MS = 6000L
        const val LONG_WAIT_MS = 8000L
        const val SEARCH_DELAY_MS = 2000L
    }
}
