# BLUEPRINT GENERATION PROCESS - CURRENT (OVERLY COMPLICATED)

## STEP-BY-STEP WHAT HAPPENS NOW:

### 1. **Function Entry** (0ms)
- Android app calls `generateWeeklyBlueprint()` Cloud Function
- Function logs entry point

### 2. **Profile Loading** (~100-500ms)
- Tries `users/{uid}/profile/main` first
- Falls back to `users/{uid}` if not found
- Falls back to `users/{uid}/profile/goals` if still not found
- **3 different locations checked**

### 3. **Profile Normalization** (~10ms)
- Normalizes every field (weight, height, age, etc.)
- Converts strings to numbers, handles nulls/undefined
- **Defensive programming for every field**

### 4. **Profile Validation** (~10ms)
- Checks if dietary preference exists
- Checks if calories can be calculated
- Builds list of missing fields
- **Throws error if anything missing**

### 5. **Get Previous Weeks' Meals** (~500-2000ms)
- Queries 12 weeks of previous blueprints
- Extracts meal names from all meals
- Extracts ingredients from all meals
- Builds exclusion list string
- **Fetches and processes 12 weeks of data**

### 6. **Build Massive Prompt** (~50-100ms)
- Constructs 10,000+ character prompt
- Includes dietary rules
- Includes previous weeks exclusion list
- Includes unit preferences (imperial/metric)
- Includes variety requirements
- Includes JSON format example
- **Massive prompt with tons of instructions**

### 7. **Call OpenAI** (~30-120 seconds)
- Sends prompt to GPT-4o
- Waits for response
- **This is the slowest part**

### 8. **Response Validation** (~100-500ms)
- Checks if response is empty
- Checks if response is too short (<2000 chars)
- Checks if response was truncated
- Checks if response is valid JSON format
- **Multiple validation checks**

### 9. **JSON Parsing with Recovery** (~50-200ms)
- Tries to parse JSON
- If fails, runs 5 recovery passes:
  - Fix unquoted keys
  - Fix trailing commas
  - Fix single quotes
  - Fix missing commas
  - Fix unclosed strings
- **Complex recovery logic**

### 10. **Dietary Preference Validation** (~50ms)
- Checks if meals follow dietary rules
- **Post-processing validation**

### 11. **Unit Validation** (~50ms)
- Checks if units match user preference
- Warns if wrong units found
- **Post-processing validation**

### 12. **Shopping List Limit Enforcement** (~50ms)
- Counts shopping list items
- Reduces to 25 items if needed
- **Post-processing modification**

### 13. **7-Day Validation** (~10ms)
- Checks if exactly 7 days
- **Throws error and RETRIES if not 7 days**

### 14. **Lunch Validation** (~10ms)
- If mealsPerDay >= 3, checks all days have lunch
- **Throws error and RETRIES if missing lunch**

### 15. **Meal Name Extraction** (~10ms)
- Extracts all meal names
- Sorts them
- Creates SHA-256 hash
- **For duplicate detection**

### 16. **Duplicate Blueprint Check** (~200-500ms)
- Queries Firestore for blueprints with same hash
- If duplicate found, **THROWS ERROR AND RETRIES**
- **This can cause infinite loops**

### 17. **Sanitize for Firestore** (~50-200ms)
- Recursively sanitizes entire object
- Flattens nested arrays
- Removes undefined values
- Converts Dates to timestamps
- **Complex recursive sanitization**

### 18. **Final Validation** (~10ms)
- Double-checks 7 days
- Double-checks shopping list exists
- **Redundant validation**

### 19. **Save to Firestore** (~200-500ms)
- Saves to `users/{uid}/weeklyBlueprints/{weekId}`
- Also saves to `users/{uid}/weeklyPlans/{weekId}` (backward compatibility)
- **Saves to 2 locations**

### 20. **Return Success** (~10ms)
- Returns to Android app
- **Total time: 2-4 minutes**

---

## RETRY LOGIC:
- If OpenAI returns < 7 days → RETRY (up to 3 times)
- If OpenAI returns missing lunch → RETRY (up to 3 times)
- If duplicate blueprint → RETRY (up to 3 times)
- If JSON parse fails → RETRY (up to 3 times)
- If response too short → RETRY (up to 3 times)

**Each retry takes 30-120 seconds (OpenAI call)**

---

## PROBLEMS:
1. **Too many validation steps** - Most should be handled by the prompt
2. **Too many retries** - Each retry is expensive and slow
3. **Duplicate checking is expensive** - Queries Firestore every time
4. **Sanitization is overkill** - Shouldn't need recursive sanitization
5. **Saving to 2 locations** - Unnecessary
6. **Previous weeks fetching** - Takes 500-2000ms, may not be needed
7. **Massive prompt** - 10,000+ characters is expensive and slow

---

## WHAT IT SHOULD BE (SIMPLE):

1. **Get user profile** (1 location only)
2. **Build simple prompt** (2000-3000 chars max)
3. **Call OpenAI** (once, no retries)
4. **Parse JSON** (simple parse, no recovery)
5. **Save to Firestore** (1 location only)

**Total time: 30-60 seconds**

---

## THE USER IS RIGHT:
You can literally go to ChatGPT and say:
"Give me a 7-day meal plan for a [diet] person eating [calories] calories per day. Include a shopping list."

And it works. We're overcomplicating it.

