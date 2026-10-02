package com.smartspend.ui.navigation

sealed class Screen(val route: String, val label: String, val icon: String) {
    object Dashboard : Screen("dashboard", "Home", "home")
    object Budget    : Screen("budget", "Budget", "pie_chart")
    object Bills     : Screen("bills", "Bills", "receipt")
    object Khata     : Screen("khata", "Khata", "book")
    object Reports   : Screen("reports", "Reports", "bar_chart")

    // Detail / modal screens
    object AddExpense : Screen("add_expense?expenseId={expenseId}&transactionId={transactionId}", "Add Expense", "") {
        /** Manual "add new expense" flow — no existing data to load. */
        fun new(): String = "add_expense?expenseId=-1&transactionId=-1"
        /** Edit an existing, already-saved expense (e.g. from the dashboard list). */
        fun edit(expenseId: Long): String = "add_expense?expenseId=$expenseId&transactionId=-1"
        /** Resolve an SMS/notification-detected transaction that hasn't been saved as an expense yet. */
        fun fromTransaction(transactionId: Long): String = "add_expense?expenseId=-1&transactionId=$transactionId"
    }
    object AddBudget        : Screen("add_budget?budgetId={budgetId}", "Add Budget", "")
    object BudgetDetail     : Screen("budget_detail/{budgetId}", "Budget Detail", "")
    object AddBill          : Screen("add_bill?billId={billId}", "Add Bill", "")
    object BillDetail       : Screen("bill_detail/{billId}", "Bill Detail", "")
    object BillCalendar     : Screen("bill_calendar", "Calendar", "")
    object KhataPersonDetail: Screen("khata_person/{personId}", "Person Ledger", "")
    object AddKhataEntry    : Screen("add_khata_entry?personId={personId}&entryId={entryId}", "Add Entry", "")
    object Categories       : Screen("categories", "Categories", "")
    object CategoryDetail   : Screen("category_detail/{categoryId}", "Category", "")
    object Search : Screen("search?rangeStart={rangeStart}&rangeEnd={rangeEnd}&rangeLabel={rangeLabel}", "Search", "") {
        /** Opens Search pre-scoped to the given [start, end) millis range, e.g. whatever
         *  time window the dashboard was showing — so search only looks within it by
         *  default. The screen itself offers a way to widen the search to all-time. */
        fun withRange(start: Long, end: Long, label: String): String {
            val encodedLabel = java.net.URLEncoder.encode(label, "UTF-8")
            return "search?rangeStart=$start&rangeEnd=$end&rangeLabel=$encodedLabel"
        }
    }
    object Filters          : Screen("filters", "Filters", "")
    object SmsScan          : Screen("sms_scan", "Scan SMS", "")
    object Settings         : Screen("settings", "Settings", "")

    companion object {
        val bottomNavItems = listOf(Dashboard, Budget, Bills, Khata, Reports)
    }
}
