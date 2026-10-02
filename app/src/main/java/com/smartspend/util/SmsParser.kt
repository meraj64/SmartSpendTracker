
package com.smartspend.util

import com.smartspend.data.models.ParsedTransactionData

object SmsParser {

    // ── Amount patterns — ordered most-specific to least ──────────────────
    private val AMOUNT_PATTERNS = listOf(
        // "debited by Rs.562.00" / "credited with Rs 1,234"
        Regex("""(?:debited|spent|paid|deducted|credited|received)[\s\S]{0,30}?(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Rs.562 debited" / "Rs.500 credited"
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)[\s\S]{0,30}?(?:debited|debit|spent|paid|deducted|credited|credit|received)""", RegexOption.IGNORE_CASE),
        // "Dr Rs.562" or "Dr. 562" / "Cr Rs.500"
        Regex("""(?:Dr|Cr)\.?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Amount: Rs.562" / "Amt:562"
        Regex("""(?:amount|amt)[:\s]+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "INR 562.00" or "₹562"
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // No currency symbol at all — e.g. SBI's "A/C X6198 debited by 384.00 on date...".
        // Very common in real bank SMS despite every pattern above assuming Rs/INR/₹ is present.
        Regex("""(?:debited|credited|debit|credit|deducted|spent|paid|received)\s+(?:by|with|for)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // Same idea, reversed — "384.00 was debited" / "500 credited to your account"
        Regex("""([\d,]+(?:\.\d{1,2})?)\s+(?:was\s+|has\s+been\s+)?(?:debited|credited|deducted|spent|paid)""", RegexOption.IGNORE_CASE)
    )

    // ── Debit keywords — expanded to cover more bank formats ──────────────
    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "spent", "paid", "payment", "purchase",
        "transferred", "sent to", "withdrawn", "pos txn", "upi",
        " dr ", "dr.", "debit", "debited from", "charged", "transaction of",
        "txn of", "a/c debited", "acct debited", "your account debited",
        // Samsung Wallet / tap-to-pay phrasing — often has no amount at all
        "samsung wallet", "samsung pay", "tap and pay", "tap to pay",
        "payment successful", "payment complete", "payment done"
    )

    // ── Credit keywords — money coming IN (refunds, cashback, transfers received) ──
    // These are matched separately from debits so an incoming payment isn't silently
    // dropped just because it has no debit wording; it gets flagged isCredit=true instead.
    private val CREDIT_KEYWORDS = listOf(
        "credited", "credited with", "credited to your account", "a/c credited",
        "acct credited", "your account credited", "received", "money received",
        "refund", "refunded", "cashback", "cash back", "reversed", "reversal",
        "amount added", "added to your wallet", "deposited", " cr ", "cr.",
        "payment received"
    )

    // ── DLT-registered senders ──────────────────────────────────────────────
    // Indian telecom regulation (TRAI DLT) requires bulk SMS senders to register a route:
    // "-T" = Transactional (e.g. "VM-HDFCBK-T"), "-S" = Service (implicit consent — also
    // commonly used by banks for account alerts, e.g. SBI), "-P" = Promotional. A message
    // from a "-T" or "-S" sender is essentially guaranteed to be a real bank/UPI message
    // (never marketing), so it's used below as a safety net: if the body has a clear amount
    // but happens to use phrasing our keyword list doesn't recognize, we still don't drop it.
    private fun isKnownBankRouteSender(sender: String): Boolean {
        val s = sender.trim().uppercase()
        return s.endsWith("-T") || s.endsWith("-S")
    }

    fun parse(smsBody: String, sender: String = "", allowMissingAmount: Boolean = false): ParsedTransactionData? {
        val body = smsBody.trim()
        val lowerBody = body.lowercase()

        // Credit wording takes priority: a message like "credited ... via UPI" would
        // otherwise also match the generic "upi" debit keyword and get misclassified.
        val isCredit = CREDIT_KEYWORDS.any { lowerBody.contains(it.lowercase()) }
        val isDebit = !isCredit && DEBIT_KEYWORDS.any { lowerBody.contains(it.lowercase()) }
        val trustedSender = isKnownBankRouteSender(sender)

        // Normally require a debit/credit keyword match. But a "-T"/"-S" sender is a
        // DLT-verified bank route, so as long as there's a clear currency amount, don't
        // reject it purely because the exact wording isn't one we've seen before — that's
        // exactly the kind of message we can't afford to miss.
        if (!isDebit && !isCredit && !trustedSender) return null

        // Must have an amount — unless the source is known to sometimes omit it
        // (e.g. Samsung Wallet tap-to-pay notifications), in which case we still
        // record a pending transaction with amount=0 and let the user fill it in.
        val amount = extractAmount(body)
        if (amount == null && !allowMissingAmount) return null
        if (amount != null && amount < 1.0) return null

        val merchantHint = extractMerchantHint(body)
        return ParsedTransactionData(
            amount = amount ?: 0.0,
            bankName = detectBank(lowerBody, sender),
            referenceNumber = extractRef(body),
            merchantHint = merchantHint,
            rawBody = body,
            isAmountMissing = amount == null,
            // If neither keyword list matched but we trusted the "-T" sender, default to a
            // normal debit — it's the far more common case for an unrecognized bank alert,
            // and the user can flip it to Credit/Excluded in the pending review either way.
            isCredit = isCredit
        )
    }

    // ── Bank detection ────────────────────────────────────────────────────
    private val BANK_HINTS = mapOf(
        "SBI"        to listOf("sbi", "state bank", "SBI"),
        "HDFC"       to listOf("hdfc"),
        "ICICI"      to listOf("icici"),
        "Axis"       to listOf("axis bank", "axis"),
        "Kotak"      to listOf("kotak"),
        "Yes Bank"   to listOf("yes bank", "yesbank"),
        "PNB"        to listOf("pnb", "punjab national"),
        "BOI"        to listOf("bank of india"),
        "Canara"     to listOf("canara"),
        "Union"      to listOf("union bank"),
        "IDFC"       to listOf("idfc"),
        "IndusInd"   to listOf("indusind"),
        "Federal"    to listOf("federal bank"),
        "GPay"       to listOf("gpay", "google pay", "tez"),
        "PhonePe"    to listOf("phonepe", "phone pe"),
        "Paytm"      to listOf("paytm"),
        "Amazon Pay" to listOf("amazon pay"),
        "CRED"       to listOf("cred")
    )

    // ── Reference patterns ────────────────────────────────────────────────
    private val REF_PATTERNS = listOf(
        Regex("""(?:ref|txn|transaction|utr|rrn|refno)[.:\s#]*([A-Z0-9]{8,})""", RegexOption.IGNORE_CASE),
        Regex("""UPI[\s/]*Ref[.:\s]*([0-9]{8,})""", RegexOption.IGNORE_CASE),
        Regex("""Ref\s*No[.:\s]*([A-Z0-9]{8,})""", RegexOption.IGNORE_CASE)
    )

    // ── Merchant hint patterns ────────────────────────────────────────────
    private val MERCHANT_PATTERNS = listOf(
        // Non-greedy capture that stops at the first trailing boilerplate token (ref/UTR/IMPS
        // number, date, balance note, etc.) or digit run, instead of swallowing the rest of
        // the SMS — e.g. "trf to RAFEEQ AHMED Refno 908309362086" now yields "RAFEEQ AHMED"
        // rather than "RAFEEQ AHMED Refno 908309362086".
        Regex(
            """(?:at|to|merchant|towards|trf to|transfer to)\s+([A-Za-z][A-Za-z\s\-&'.]{1,35}?)""" +
                """(?=\s+(?:Refno|Ref\b|RRN|UTR|IMPS|UPI|on\s+\d|dated|avl|avbl|bal|If\s|not\s+u|call|info|w\.e\.f)|[\d]|$)""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""VPA\s+[\w.\-]+@[\w]+\s+(?:to\s+)?(.+?)(?:\s+on|\s+for|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:info|narration|remarks)[:\s]+([A-Za-z][A-Za-z0-9\s\-&'.]{2,35})""", RegexOption.IGNORE_CASE)
    )

    private fun extractAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(body) ?: continue
            val raw = match.groupValues[1].replace(",", "").trim()
            val value = raw.toDoubleOrNull() ?: continue
            if (value > 0) return value
        }
        return null
    }

    private fun detectBank(lowerBody: String, sender: String): String? {
        val combined = "$lowerBody ${sender.lowercase()}"
        for ((bank, hints) in BANK_HINTS) {
            if (hints.any { combined.contains(it.lowercase()) }) return bank
        }
        return null
    }

    private fun extractRef(body: String): String? {
        for (pattern in REF_PATTERNS) {
            val match = pattern.find(body) ?: continue
            return match.groupValues[1]
        }
        return null
    }

    private fun extractMerchantHint(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(body) ?: continue
            val hint = match.groupValues[1].trim()
            if (hint.length >= 3) return hint.take(40)
        }
        return null
    }

    fun formatAmount(amount: Double): String = when {
        amount >= 1_00_000 -> "₹%.2fL".format(amount / 1_00_000)
        amount >= 1_000    -> "₹%,.2f".format(amount)
        else               -> "₹%.2f".format(amount)
    }

    /** Test helper — use this in logcat to debug why an SMS isn't being parsed */
    fun debugParse(smsBody: String, sender: String = ""): String {
        val body = smsBody.trim()
        val lowerBody = body.lowercase()
        val matchedCredit = CREDIT_KEYWORDS.firstOrNull { lowerBody.contains(it.lowercase()) }
        val matchedDebit = if (matchedCredit == null) DEBIT_KEYWORDS.firstOrNull { lowerBody.contains(it.lowercase()) } else null
        val trustedSender = isKnownBankRouteSender(sender)
        val amount = extractAmount(body)
        val keywordOk = matchedCredit != null || matchedDebit != null
        return buildString {
            appendLine("=== SmsParser Debug ===")
            appendLine("Sender: $sender")
            appendLine("Body: $body")
            appendLine("DLT bank-route sender (-T/-S): ${if (trustedSender) "YES ✓" else "no"}")
            appendLine("Matched keyword: ${matchedCredit?.let { "$it (credit)" } ?: matchedDebit?.let { "$it (debit)" } ?: if (trustedSender) "NONE — but sender is trusted, so still accepted" else "NONE — will be rejected"}")
            appendLine("Amount: ${amount ?: "NOT FOUND — will be rejected"}")
            appendLine("Bank: ${detectBank(lowerBody, sender) ?: "unknown"}")
            appendLine("Ref: ${extractRef(body) ?: "none"}")
            appendLine("Merchant hint: ${extractMerchantHint(body) ?: "none"}")
            appendLine("Result: ${if ((keywordOk || trustedSender) && amount != null) "WILL DETECT ✓" else "REJECTED ✗"}")
        }
    }
}
