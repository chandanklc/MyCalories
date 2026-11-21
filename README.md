# MyCalories

An Android calorie tracker you talk to. Say what you ate — "two roti and a bowl of dal for lunch" — and it works out calories, protein, carbs, fat, fiber, vitamins and minerals, meal by meal.

Built with Kotlin, Jetpack Compose (Material 3) and MVVM, test-first.

## Features

- **Log by voice, typing, plate photo or barcode**, with a review step before saving
- Asks which meal it was when you don't say, and suggests one by the time of day
- Food library on the phone (60 common Indian and everyday foods to start); new foods are looked up online (USDA, Open Food Facts) or estimated by AI, then saved for offline use
- Learns your own portion sizes, saved meals, recipes by voice, "same as yesterday"
- History, weekly summary, weight trend with goal suggestions, vitamins and minerals, an Ask chat
- Meal and water reminders, home-screen widget, Health Connect (steps, workouts, weight)
- PDF/CSV export, backup and restore, delete all data
- English and Hindi, light and dark themes

## Setup

1. Open the project in Android Studio.
2. Get a free Gemini API key at [aistudio.google.com](https://aistudio.google.com) → **Get API key**.
3. Add it to `local.properties` (this file is never committed):
   ```
   GEMINI_API_KEY=your-key
   ```
   Optional: `GEMINI_MODEL=...` to pick another model, `USDA_API_KEY=...` for USDA lookups,
   or `ANTHROPIC_API_KEY=...` / `CLAUDE_PROXY_URL` + `CLAUDE_PROXY_TOKEN` to use Claude instead (see [server/](server/README.md)).
4. Sync Gradle and run the app.

On Gemini's free tier, Google may use what you send to improve its products.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Unit, Room, ViewModel and Compose UI tests (Robolectric) run without a device. The key server in `server/` has its own tests: `cd server && npm test`.

## Project layout

```
app/src/main/java/com/example/mycalories/
├── domain/      models, calculators and rules (pure Kotlin)
├── data/        Room database, DataStore, AI and food-lookup clients
├── platform/    Health Connect, reminders, widget, PDF, camera and barcode
├── voice/       speech to text
└── ui/          Compose screens and their ViewModels
server/          optional Cloudflare Worker that keeps an Anthropic key off the phone
```
