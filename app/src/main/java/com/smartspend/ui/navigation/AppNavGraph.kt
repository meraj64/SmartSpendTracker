package com.smartspend.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartspend.ui.bills.BillsScreen
import com.smartspend.ui.budget.BudgetScreen
import com.smartspend.ui.categories.CategoriesScreen
import com.smartspend.ui.dashboard.DashboardScreen
import com.smartspend.ui.expense.AddExpenseScreen
import com.smartspend.ui.khata.KhataPersonDetailScreen
import com.smartspend.ui.khata.KhataScreen
import com.smartspend.ui.reports.ReportsScreen
import com.smartspend.ui.search.SearchScreen
import com.smartspend.ui.settings.SettingsScreen
import com.smartspend.ui.smsscan.SmsScanScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onSearch             = { filter ->
                    val (start, end) = filter.toDateRange()
                    navController.navigate(Screen.Search.withRange(start, end, filter.label()))
                },
                onAddExpense         = { navController.navigate(Screen.AddExpense.new()) },
                onEditExpense        = { expenseId -> navController.navigate(Screen.AddExpense.edit(expenseId)) },
                onResolveTransaction = { transactionId -> navController.navigate(Screen.AddExpense.fromTransaction(transactionId)) },
                onBudgetClick        = { navController.navigate(Screen.Budget.route) },
                onBillsClick         = { navController.navigate(Screen.Bills.route) },
                onKhataClick         = { navController.navigate(Screen.Khata.route) },
                onScanSms            = { navController.navigate(Screen.SmsScan.route) },
                onSettings           = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }


        composable(Screen.SmsScan.route) {
            SmsScanScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Budget.route) {
            BudgetScreen(onManageCategories = { navController.navigate(Screen.Categories.route) })
        }

        composable(Screen.Bills.route) {
            BillsScreen()
        }

        composable(Screen.Khata.route) {
            KhataScreen(
                onPersonClick = { personId ->
                    navController.navigate("khata_person/$personId")
                }
            )
        }

        composable(
            route = "khata_person/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) { backStack ->
            val personId = backStack.arguments?.getLong("personId") ?: return@composable
            KhataPersonDetailScreen(
                personId = personId,
                onBack   = { navController.popBackStack() }
            )
        }

        composable(Screen.Reports.route) {
            ReportsScreen()
        }

        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("rangeStart") { type = NavType.LongType; defaultValue = -1L },
                navArgument("rangeEnd") { type = NavType.LongType; defaultValue = -1L },
                navArgument("rangeLabel") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onResultClick = { expenseId -> navController.navigate(Screen.AddExpense.edit(expenseId)) }
            )
        }

        composable(Screen.Categories.route) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("transactionId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStack ->
            val expenseId = backStack.arguments?.getLong("expenseId")?.takeIf { it != -1L }
            val transactionId = backStack.arguments?.getLong("transactionId")?.takeIf { it != -1L }
            AddExpenseScreen(
                expenseId = expenseId,
                transactionId = transactionId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
