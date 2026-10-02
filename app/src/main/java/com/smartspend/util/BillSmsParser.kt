package com.smartspend.util

import com.smartspend.data.models.BillType
import com.smartspend.data.models.ParsedBillData
import java.util.Calendar

/**
 * Detects "you have a bill to pay" SMS — electricity/water/internet bills, credit card
 * statements, EMI reminders, insurance premiums, etc. — and extracts enough information to
 * create a [com.smartspend.data.models.Bill] automatically.
 *
 * This is intentionally separate from [SmsParser], which handles *completed* debit
 * notifications ("Rs.500 debited"). A bill-due SMS is a forward-looking reminder to pay,
 * not a record of money already spent.
 */
object BillSmsParser {

    // ── Phrases that signal "you owe money, pay it by X" ───────────────────
    private val BILL_KEYWORDS = listOf(
        "bill is due", "bill due", "payment due", "due date", "due on", "due by",
        "amount due", "total amount due", "minimum amount due", "min due",
        "outstanding amount", "please pay", "kindly pay", "pay before", "pay by",
        "last date for payment", "avoid late fee", "avoid late payment",
        "premium due", "premium payment", "emi due", "emi of", "installment due",
        "installment of", "recharge before", "renew before", "renewal due",
        "bill generated", "statement generated", "fee due", "fees due", "rent due",
        "is due for payment", "payable by", "bill of rs"
    )

    // Confirmations that money already moved — these should NOT create a bill.
    private val EXCLUDE_KEYWORDS = listOf(
        "payment successful", "payment received", "has been paid", "paid successfully",
        "thank you for your payment", "payment confirmed", "amount credited",
        "credited to your account", "debited", "we have received your payment"
    )

    private val TYPE_HINTS: List<Pair<BillType, List<String>>> = listOf(
        BillType.CREDIT_CARD to listOf("credit card", "card statement", "minimum amount due", "total amount due", "card ending"),
        BillType.ELECTRICITY to listOf("electricity", "power bill", "discom", "electric bill", "energy bill"),
        BillType.WATER to listOf("water bill", "water board", "water supply", "water charges"),
        BillType.INTERNET to listOf("broadband", "wifi bill", "internet bill", "fiber", "fibernet"),
        BillType.MOBILE_RECHARGE to listOf("recharge", "mobile bill", "postpaid"),
        BillType.INSURANCE to listOf("premium", "insurance", "policy"),
        BillType.EMI to listOf("emi"),
        BillType.LOAN to listOf("loan", "installment"),
        BillType.RENT to listOf("rent"),
        BillType.SCHOOL_FEES to listOf("school fee", "tuition fee", "fee due", "fees due"),
        BillType.SUBSCRIPTION to listOf("subscription", "renewal", "renew")
    )

    private val BILL_AMOUNT_PATTERNS = listOf(
        Regex("""(?:total amount due|minimum amount due|min(?:imum)?\.?\s*due|amount due|bill amount|outstanding amount|total due)[:\s]*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)[\s\S]{0,25}?(?:is due|due on|due by|payable)""", RegexOption.IGNORE_CASE),
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val NUMERIC_DATE = Regex("""\b(0?[1-9]|[12][0-9]|3[01])[/\-.](0?[1-9]|1[0-2])[/\-.](\d{4}|\d{2})\b""")
    private val TEXTUAL_DATE = Regex(
        """\b(0?[1-9]|[12][0-9]|3[01])(?:st|nd|rd|th)?[\s\-]?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\s\-,]*(\d{4}|\d{2})?\b""",
        RegexOption.IGNORE_CASE
    )
    private val MONTH_INDEX = mapOf(
        "jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5,
        "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11
    )

    /**
     * @param smsTimestamp the time the SMS itself was received — used to resolve due dates
     *        that omit a year (e.g. "due on 15 Jul") and as a fallback due date if none is found.
     */
    fun parse(smsBody: String, sender: String = "", smsTimestamp: Long = System.currentTimeMillis()): ParsedBillData? {
        val body = smsBody.trim()
        val lowerBody = body.lowercase()

        val looksLikeBill = BILL_KEYWORDS.any { lowerBody.contains(it) }
        if (!looksLikeBill) return null

        val looksLikeConfirmation = EXCLUDE_KEYWORDS.any { lowerBody.contains(it) }
        if (looksLikeConfirmation) return null

        val amount = extractAmount(body) ?: return null
        if (amount < 1.0) return null

        val dueDate = extractDueDate(body, smsTimestamp) ?: return null

        val type = detectType(lowerBody)
        val provider = cleanSender(sender)
        val title = if (provider.isNotBlank()) "$provider ${type.displayName}" else type.displayName

        return ParsedBillData(
            amount = amount,
            dueDate = dueDate,
            type = type,
            title = title.trim(),
            sender = sender,
            rawBody = body
        )
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in BILL_AMOUNT_PATTERNS) {
            val match = pattern.find(body) ?: continue
            val raw = match.groupValues[1].replace(",", "").trim()
            val value = raw.toDoubleOrNull() ?: continue
            if (value > 0) return value
        }
        return null
    }

    private fun extractDueDate(body: String, referenceMillis: Long): Long? {
        NUMERIC_DATE.find(body)?.let { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = m.groupValues[2].toIntOrNull()
            var year = m.groupValues[3].toIntOrNull()
            if (day != null && month != null && year != null && day in 1..31 && month in 1..12) {
                if (year < 100) year += 2000
                val cal = Calendar.getInstance().apply {
                    clear()
                    set(year, month - 1, day, 0, 0, 0)
                }
                return cal.timeInMillis
            }
        }

        TEXTUAL_DATE.find(body)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val monthAbbr = m.groupValues[2].lowercase().take(3)
            val month = MONTH_INDEX[monthAbbr] ?: return@let
            val yearGroup = m.groupValues[3]

            val refCal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
            val year = if (yearGroup.isNotBlank()) {
                yearGroup.toIntOrNull()?.let { if (it < 100) it + 2000 else it } ?: refCal.get(Calendar.YEAR)
            } else {
                refCal.get(Calendar.YEAR)
            }

            val candidate = Calendar.getInstance().apply {
                clear()
                set(year, month, day, 0, 0, 0)
            }

            // No explicit year and the date landed more than ~20 days in the past relative to
            // the SMS — most likely means next year (e.g. bill received in late Dec for a Jan due date).
            val twentyDaysMillis = 20L * 24 * 60 * 60 * 1000
            if (yearGroup.isBlank() && candidate.timeInMillis < referenceMillis - twentyDaysMillis) {
                candidate.add(Calendar.YEAR, 1)
            }
            return candidate.timeInMillis
        }

        return null
    }

    private fun detectType(lowerBody: String): BillType {
        for ((type, hints) in TYPE_HINTS) {
            if (hints.any { lowerBody.contains(it) }) return type
        }
        return BillType.CUSTOM
    }

    private fun cleanSender(sender: String): String {
        // Strip common DLT sender-ID prefixes/suffixes, e.g. "AD-HDFCBK-S" -> "HDFCBK"
        return sender.replace(Regex("""^[A-Z]{2}-"""), "")
            .replace(Regex("""-[A-Z]$"""), "")
            .replace(Regex("""[^A-Za-z0-9]"""), " ")
            .trim()
    }

    /** Test/debug helper — mirrors [SmsParser.debugParse]. */
    fun debugParse(smsBody: String, sender: String = ""): String {
        val body = smsBody.trim()
        val lowerBody = body.lowercase()
        val matchedKeyword = BILL_KEYWORDS.firstOrNull { lowerBody.contains(it) }
        val excludeMatch = EXCLUDE_KEYWORDS.firstOrNull { lowerBody.contains(it) }
        val amount = extractAmount(body)
        val dueDate = extractDueDate(body, System.currentTimeMillis())
        return buildString {
            appendLine("=== BillSmsParser Debug ===")
            appendLine("Sender: $sender")
            appendLine("Body: $body")
            appendLine("Matched bill keyword: ${matchedKeyword ?: "NONE — will be rejected"}")
            appendLine("Matched exclude keyword: ${excludeMatch ?: "none"}")
            appendLine("Amount: ${amount ?: "NOT FOUND — will be rejected"}")
            appendLine("Due date: ${dueDate ?: "NOT FOUND — will be rejected"}")
            appendLine("Type: ${detectType(lowerBody)}")
            appendLine("Result: ${if (matchedKeyword != null && excludeMatch == null && amount != null && dueDate != null) "WILL DETECT ✓" else "REJECTED ✗"}")
        }
    }
}
