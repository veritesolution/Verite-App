# 🧠 MindSet Pro — Kotlin Android Mobile App

## AI-Powered Habit & Task Intelligence System

A complete Android app (6,400+ lines of Kotlin) converted from the MindSet Pro Python notebook,
built with **Kotlin**, **Jetpack Compose**, **Room**, **Glance Widgets**, and on-device ML.

---

## 📱 Complete Feature Map (All Notebook Phases + Mobile Extras)

### Phase 1 — Data Models & Schema (`Models.kt`)
- Room entities: `Task`, `Habit`, `HabitCompletion`, `MoodEntry`, `BedtimeItem`
- Enums: `Priority`, `Category`, `Intent`
- Data classes: `DashboardSnapshot`, `StreakInfo`, `VoiceCommandResult`, `HabitCluster`, `DayOfWeekProfile`, `CategoryBreakdown`

### Phase 2 — Synthetic Data Engine (`SyntheticDataEngine.kt`)
- Generates 90 days of realistic habit history
- Weekday bias, gradual improvement trends, hot/cold streaks, per-habit personality

### Phase 3 — Task Manager (`TaskRepository.kt` + `Daos.kt`)
- Full CRUD: create, read, update, delete
- Filter by category, priority, done status
- Sort by priority ranking
- Full-text search

### Phase 4 — Habit Manager + Streak Engine (`HabitRepository.kt`)
- Full CRUD + per-day toggle
- Current streak & longest streak computation
- Completion rates (7d, 30d windows)
- Weekly breakdown, 90-day heatmap data, today's scheduled/completed

### Phase 5 — Voice Commands (`VoiceCommandProcessor.kt` + `VoiceInputHandler.kt`)
- **Tier 1**: Regex-based intent parsing (~0ms)
- **Tier 2**: Anthropic Claude Haiku LLM fallback (`LlmVoiceProcessor.kt`)
- Android `SpeechRecognizer` with partial results
- Batch command parsing ("add task X and I did Y then show tasks")
- Intents: add/complete/delete task, add/toggle habit, query streak/tasks/habits

### Phase 6 — ML Intelligence Engine (`BehavioralAnalyzer.kt`)
- **K-Means clustering** — groups habits by behavioral profile
- **Anomaly detection** — Z-score based drop-off week detection
- **Day-of-week productivity profiling**
- **Habit correlation matrix** — Pearson correlation between habits

### Phase 7 — Predictive Streak Engine (`BehavioralAnalyzer.predictTomorrow()`)
- Features: streak length, DOW cyclic encoding, rolling 7d/14d rates
- Sigmoid-squashed probability output per habit

### Phase 8 — Sentiment & Mood Tracker (`SentimentMoodTracker.kt`)
- Lexicon-based sentiment analysis (productivity-tuned word sets)
- Productive Momentum composite score (0–100):
  40% habits + 30% tasks + 20% streaks + 10% sentiment

### Phase 9 — Analytics & KPI Engine (`AnalyticsEngine.kt`)
- Today snapshot (habits %, tasks %, streaks)
- Category breakdown with completion rates
- Day-of-week profile, ML clusters, predictions

### Phase 10 — Rich Dashboard (`DashboardScreen.kt` + `AnalyticsScreen.kt`)
- 4 KPI tiles · Task cards · Habit cards with predictions
- Momentum arc gauge · Habit bar chart · Category donut chart
- Day-of-week heatmap · Cluster summary · Streak ranking table with medals

### ☁️ Firebase Cloud Sync (`FirebaseSyncManager.kt`)
- Firestore batch writes for tasks, habits, completions
- Full backup/restore · Enable by uncommenting deps

### ⏰ Scheduler & Notifications (`TaskScheduler.kt`)
- Local notifications: upcoming tasks, daily habit reminder, bedtime trigger
- `BootReceiver.kt` reschedules alarms after reboot
- Configurable reminder times via Settings

### 🌙 Bedtime Routine (`BedtimeRoutineManager.kt` + `BedtimeRoutineScreen.kt`)
- 7-item guided wind-down checklist with progress bar
- Per-day tracking, reset support

### 📲 Mobile-Only Features (not in notebook)

| Feature | File |
|---|---|
| **Onboarding** — 4-page swipeable welcome flow | `OnboardingScreen.kt` |
| **Settings** — notifications, sync, AI, danger zone | `SettingsScreen.kt` |
| **Add Task Dialog** — name, category dropdown, priority chips | `Dialogs.kt` |
| **Add Habit Dialog** — emoji picker, day-of-week chips, presets | `Dialogs.kt` |
| **Task Detail Screen** — edit, sentiment display, metadata | `TaskDetailScreen.kt` |
| **Habit Detail Screen** — 90-day heatmap, weekly bars, prediction | `HabitDetailScreen.kt` |
| **Swipe-to-Dismiss** — swipe right=done, swipe left=delete | `SwipeableCard.kt` |
| **Home Screen Widget** — Glance widget with KPIs + habit checklist | `MindSetWidget.kt` |
| **Data Export/Import** — JSON backup with share intent | `DataExportManager.kt` |
| **Preferences** — SharedPreferences for all settings | `PreferencesManager.kt` |
| **Unit Tests** — 15 tests for voice, sentiment, ML | `CoreLogicTest.kt` |

---

## 🏗️ Architecture (48 files)

```
com.mindsetpro/
├── data/
│   ├── model/Models.kt            # Room entities + data classes
│   ├── local/
│   │   ├── Daos.kt                # 5 DAOs (Task, Habit, Completion, Mood, Bedtime)
│   │   └── MindSetDatabase.kt     # Room database singleton
│   └── repository/
│       ├── TaskRepository.kt      # Task CRUD + analytics queries
│       ├── HabitRepository.kt     # Habit CRUD + streak engine + heatmap
│       └── FirebaseSyncManager.kt # Optional Firestore cloud sync
├── ml/
│   ├── BehavioralAnalyzer.kt      # K-Means, anomaly detection, predictions
│   ├── SentimentMoodTracker.kt    # Lexicon sentiment + momentum scoring
│   ├── AnalyticsEngine.kt         # KPI computation engine
│   └── LlmVoiceProcessor.kt      # Claude Haiku API fallback
├── ui/
│   ├── MindSetProApp.kt           # Root navigation + screen router
│   ├── theme/Theme.kt             # Dark theme + color system
│   ├── dashboard/
│   │   ├── DashboardScreen.kt     # Main KPI + task/habit lists
│   │   ├── DashboardViewModel.kt  # Central MVVM ViewModel
│   │   ├── Dialogs.kt             # Add Task/Habit dialogs + FAB menu
│   │   └── SwipeableCard.kt       # Swipe-to-dismiss wrapper
│   ├── analytics/AnalyticsScreen.kt  # Charts, gauges, heatmaps
│   ├── voice/VoiceCommandScreen.kt   # Animated mic + command display
│   ├── bedtime/BedtimeRoutineScreen.kt # Guided checklist
│   ├── tasks/TaskDetailScreen.kt     # Task edit + sentiment
│   ├── habits/HabitDetailScreen.kt   # Habit detail + 90d heatmap
│   ├── onboarding/OnboardingScreen.kt # 4-page welcome flow
│   ├── settings/SettingsScreen.kt    # Full settings panel
│   └── widget/MindSetWidget.kt       # Glance home screen widget
├── utils/
│   ├── VoiceCommandProcessor.kt   # 2-tier NLP intent parser
│   ├── VoiceInputHandler.kt       # Android SpeechRecognizer wrapper
│   ├── TaskScheduler.kt           # AlarmManager notifications
│   ├── BedtimeRoutineManager.kt   # Nightly routine logic
│   ├── SyntheticDataEngine.kt     # Demo data generator
│   ├── DataExportManager.kt       # JSON export/import
│   ├── PreferencesManager.kt      # SharedPreferences wrapper
│   └── BootReceiver.kt            # Alarm reschedule on boot
├── MainActivity.kt
└── MindSetProApplication.kt

res/
├── values/themes.xml, strings.xml
├── xml/widget_info.xml, file_paths.xml
└── layout/widget_loading.xml

test/
└── CoreLogicTest.kt               # 15 unit tests
```

**Stack**: Kotlin 1.9 · Jetpack Compose · Material 3 · Room · Coroutines/Flow · Glance Widgets · MVVM

---

## 🚀 Getting Started

1. Open in **Android Studio Hedgehog** (2023.1.1+)
2. Sync Gradle
3. Run on device or emulator (**API 26+** required)
4. On first launch, complete the onboarding flow
5. Tap ⚙️ Settings → "Load Demo Data" to seed 90 days of sample data

### Optional Setup

- **Firebase**: Add `google-services.json`, uncomment Firebase lines in `build.gradle.kts`
- **Claude LLM**: Add API key in Settings or BuildConfig
- **Widget**: Long-press home screen → Widgets → MindSet Pro

---

## 📋 Permissions

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Voice command input via SpeechRecognizer |
| `INTERNET` | Firebase sync & Anthropic LLM fallback |
| `POST_NOTIFICATIONS` | Task/habit/bedtime reminders |
| `SCHEDULE_EXACT_ALARM` | Precise notification scheduling |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |

---

## 🧪 Testing

Run unit tests:
```bash
./gradlew test
```

Tests cover:
- Voice command parsing (all 8 intents + batch + edge cases)
- Sentiment analysis (positive/negative/neutral)
- Momentum scoring (range validation, labels)
- Anomaly detection (insufficient data, drop-off detection)
- K-Means clustering (graceful small-dataset handling)
