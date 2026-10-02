# SmartSpend Tracker — Full Finance App

A complete personal finance Android app built with Jetpack Compose + Hilt + Room.
Also includes the original SMS/UPI auto-detection module from v1.

---

## Features

| Module | What it does |
|---|---|
| 📊 Dashboard | Hero spend card, budget progress, upcoming bills, recent transactions, quick stats |
| 🎯 Budget Planner | Monthly budgets per category, circular/linear progress, color-coded status, estimated month-end |
| 🧾 Bill Organizer | Recurring bills, calendar view, mark paid (rolls forward for recurring), 7/3/1 day reminders |
| 📒 Khata/Cash Book | Person ledger, money given/received, running balance timeline, settlement tracking |
| 🗂 Categories | 12 default categories + unlimited custom, emoji + color picker |
| 📈 Reports | Vico charts — daily spending line chart + category bar breakdown |
| 🔍 Search | Real-time debounced search across all expenses |
| ➕ Add Expense | Full form with category, method, date picker |
| 📱 SMS Detection | Auto-detects UPI/bank SMS, finds merchant via Google Places, confirm/edit notification |

---

## Architecture

```
SmartSpendTracker/
├── data/
│   ├── models/          Category, Expense, Income, Budget, Bill, Khata entities
│   ├── db/              Room DAOs for every entity + AppDatabase
│   ├── repository/      CategoryRepo, ExpenseRepo, BudgetRepo, BillRepo, KhataRepo
│   └── datastore/       PreferencesManager (DataStore)
├── di/                  DatabaseModule (Hilt)
├── service/             TransactionProcessingService, NotificationListenerService
├── receiver/            SmsReceiver, BootReceiver, NotificationActionReceiver
├── work/                BillReminderWorker (WorkManager, daily)
├── util/                SmsParser, LocationHelper, NotificationHelper
└── ui/
    ├── theme/           Material 3 theme, dynamic color, typography
    ├── navigation/      AppNavGraph, BottomNavBar, Screen sealed class
    ├── dashboard/       DashboardScreen + DashboardViewModel
    ├── budget/          BudgetScreen + BudgetViewModel
    ├── bills/           BillsScreen + BillViewModel
    ├── khata/           KhataScreen, KhataPersonDetailScreen + ViewModels
    ├── categories/      CategoriesScreen + CategoryViewModel
    ├── expense/         AddExpenseScreen + AddExpenseViewModel
    ├── search/          SearchScreen + SearchViewModel
    ├── reports/         ReportsScreen + ReportsViewModel
    └── common/          CommonComponents (BudgetProgress, EmojiAvatar, EmptyState, formatCurrency…)
```

---

## Setup

### 1. Get a Google Places API key
→ https://console.cloud.google.com → Enable **Places API (New)**

### 2. Add to local.properties
```
sdk.dir=/path/to/your/sdk
PLACES_API_KEY=AIzaSy_YOUR_KEY_HERE
```

### 3. Open in Android Studio
File → Open → select SmartSpendTracker folder → wait for Gradle sync

### 4. Run on a real device
Green ▶ button — a real phone is required for SMS and location features.

### 5. Grant permissions on first launch
- SMS (Receive + Read)
- Location → "Allow all the time"
- Post Notifications
- Notification Access (Settings → Special app access → Notification access → SmartSpend → ON)

---

## Gradle troubleshooting

If you get a Gradle version error, open:
`gradle/wrapper/gradle-wrapper.properties`

and set:
```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
```

Then **File → Invalidate Caches → Restart**.
