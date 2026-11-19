package ir.jrweb.banksms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import java.text.DecimalFormat
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody)
        }

        val bodyText = normalizePersian(fullBody.toString())

        // شرط اصلی: وجود کلمه ریال یا تومان در متن پیامک
        val hasRial = bodyText.contains("ریال")
        val hasToman = bodyText.contains("تومان") || bodyText.contains("تومن")
        if (!hasRial && !hasToman) return

        val amountToman = extractAmountInToman(bodyText, hasRial, hasToman) ?: return
        val txType = detectTransactionType(bodyText)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, PopupService::class.java).apply {
                putExtra("TYPE", txType)
                putExtra("AMOUNT", amountToman)
            }
            context.startService(serviceIntent)
        }
    }

    private fun normalizePersian(input: String): String {
        return input
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("٬", ",")
            .replace("،", ",")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8")
            .replace("۹", "9")
    }

    private fun detectTransactionType(body: String): String {
        val withdrawKeywords = listOf(
            "برداشت", "پرید", "کسر", "بدهکار", "خرید",
            "انتقال از", "پرداخت", "کارمزد", "قبض", "قسط"
        )
        val depositKeywords = listOf(
            "واریز", "نشست", "افزایش", "بستانکار",
            "انتقال به", "دریافت وجه", "عودت", "برگشت"
        )

        var firstWithdraw = Int.MAX_VALUE
        for (kw in withdrawKeywords) {
            val idx = body.indexOf(kw)
            if (idx in 0 until firstWithdraw) firstWithdraw = idx
        }

        var firstDeposit = Int.MAX_VALUE
        for (kw in depositKeywords) {
            val idx = body.indexOf(kw)
            if (idx in 0 until firstDeposit) firstDeposit = idx
        }

        if (firstWithdraw != Int.MAX_VALUE || firstDeposit != Int.MAX_VALUE) {
            return if (firstWithdraw < firstDeposit) "WITHDRAW" else "DEPOSIT"
        }

        if (Pattern.compile("(?:(?<![0-9/])-\\s*[0-9,]{3,}|[0-9,]{3,}\\s*-(?![0-9]))").matcher(body).find()) {
            return "WITHDRAW"
        }

        return "DEPOSIT"
    }

    private fun extractAmountInToman(body: String, hasRial: Boolean, hasToman: Boolean): String? {
        // حذف شماره کارت، تاریخ، ساعت و بخش مانده/موجودی تا مبلغ اصلی تراکنش خوانده شود
        val cleaned = body
            .replace(Regex("\\b\\d{4}[-\\s*]+\\d{2,4}[-\\s*]+\\d{2,4}[-\\s*]+\\d{4}\\b"), " ")
            .replace(Regex("\\b\\d{2,4}/\\d{1,2}/\\d{1,2}\\b"), " ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b"), " ")
            .replace(Regex("(موجودی|مانده)[^\\n\\r]*"), " ")

        // اولویت اول: عددی که دقیقاً قبل از کلمه ریال یا تومان آمده (مثل 200000ریال یا 50,000 تومان)
        val directMatcher = Pattern.compile("([0-9][0-9,\\.]{1,})\\s*(?:ریال|تومان|تومن)").matcher(cleaned)
        var rawNumberStr: String? = null
        var matchedUnitIsToman = false

        if (directMatcher.find()) {
            rawNumberStr = directMatcher.group(1)?.replace(",", "")?.replace(".", "")
            val fullMatch = directMatcher.group(0) ?: ""
            matchedUnitIsToman = fullMatch.contains("تومان") || fullMatch.contains("تومن")
        } else {
            // اولویت دوم: اولین عدد معتبر در متن
            val fallbackMatcher = Pattern.compile("([0-9][0-9,\\.]{2,})").matcher(cleaned)
            while (fallbackMatcher.find()) {
                val candidate = fallbackMatcher.group(1)?.replace(",", "")?.replace(".", "") ?: continue
                if (candidate.length in 3..13 && !candidate.startsWith("140")) {
                    rawNumberStr = candidate
                    matchedUnitIsToman = hasToman && !hasRial
                    break
                }
            }
        }

        val amount = rawNumberStr?.toLongOrNull() ?: return null
        if (amount <= 0) return null

        val tomanValue = if (matchedUnitIsToman) amount else amount / 10
        val formatter = DecimalFormat("#,###")
        return formatter.format(tomanValue)
    }
}