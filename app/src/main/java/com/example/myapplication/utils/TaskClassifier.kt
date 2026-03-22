package com.example.myapplication.utils

/**
 * TaskClassifier
 *
 * Instant, offline keyword-based classifier for task priority and category.
 * Runs synchronously before the task is saved so the UI always shows a
 * meaningful classification immediately — even before the AI API responds.
 *
 * Priority rules  (evaluated in order, first match wins):
 *   HIGH   → contains an urgency keyword (deadline, doctor, emergency, etc.)
 *   LOW    → contains a low-urgency keyword (someday, optional, idea, etc.)
 *   MEDIUM → default
 *
 * Category rules:
 *   Score every category by how many of its keywords appear in the text.
 *   Pick the highest-scoring category; tie-break is the list order below.
 *   Default → "Personal" (better everyday default than "Work").
 */
object TaskClassifier {

    // ── Valid values ─────────────────────────────────────────────────────────

    val VALID_PRIORITIES  = setOf("High", "Medium", "Low")
    val VALID_CATEGORIES  = setOf("Work", "Personal", "Health", "Finance", "Errand", "Learning", "Social")

    // ── Priority keyword banks ────────────────────────────────────────────────

    private val HIGH_KEYWORDS = setOf(
        // time pressure
        "urgent", "asap", "immediately", "now", "today", "tonight",
        "deadline", "due", "overdue", "last chance", "expir",
        // medical / safety
        "doctor", "hospital", "clinic", "dentist", "prescription", "medicine",
        "emergency", "accident", "pain", "injury", "hurt", "fever",
        "ambulance", "surgery", "appointment",
        // critical work
        "critical", "blocker", "bug", "crash", "broken", "outage", "down",
        "fix", "hotfix", "incident", "production", "alert", "escalat",
        // financial urgency
        "overdue", "late fee", "final notice", "eviction", "foreclosure",
        // personal urgency
        "important", "must", "need to", "have to", "required", "mandatory",
        "cannot miss", "can't miss", "vital"
    )

    private val LOW_KEYWORDS = setOf(
        "someday", "maybe", "whenever", "eventually", "one day",
        "optional", "nice to have", "consider", "explore", "idea",
        "would be nice", "low priority", "not urgent", "backlog",
        "leisure", "casual", "whenever possible", "if time", "when free",
        "read", "browse", "watch", "look into", "think about", "research",
        "future", "long term", "someday", "wishlist"
    )

    // ── Category keyword banks ────────────────────────────────────────────────

    private val CATEGORY_KEYWORDS: Map<String, Set<String>> = linkedMapOf(
        "Health" to setOf(
            "doctor", "hospital", "clinic", "dentist", "physio", "therapy",
            "therapist", "counsellor", "counselor", "psychiatrist", "psychologist",
            "medicine", "prescription", "pills", "vitamins", "supplement",
            "workout", "exercise", "gym", "run", "jog", "walk", "hike",
            "yoga", "pilates", "stretching", "diet", "nutrition", "calories",
            "sleep", "meditation", "mindfulness", "mental health", "checkup",
            "blood test", "vaccination", "vaccine", "health", "wellness",
            "weight", "bmi", "heartrate", "heart rate", "steps", "water intake",
            "appointment", "pain", "injury", "fever", "sick", "illness", "recovery"
        ),
        "Work" to setOf(
            "meeting", "standup", "call with", "sync with", "interview",
            "presentation", "report", "proposal", "project", "sprint", "ticket",
            "task", "deliverable", "milestone", "deadline", "launch", "release",
            "deploy", "code", "debug", "review", "pr", "pull request", "commit",
            "branch", "test", "qa", "bug", "feature", "backlog",
            "email", "follow up", "respond", "reply", "slack", "teams",
            "client", "customer", "stakeholder", "manager", "boss", "hr",
            "contract", "proposal", "bid", "invoice", "quote",
            "office", "remote", "wfh", "work from home", "onboarding",
            "training", "workshop", "conference"
        ),
        "Finance" to setOf(
            "pay", "payment", "bill", "bills", "invoice", "rent",
            "mortgage", "loan", "credit", "debit", "card", "bank",
            "transfer", "wire", "budget", "expense", "income", "salary",
            "tax", "taxes", "vat", "gst", "filing", "return",
            "insurance", "premium", "subscription", "cancel subscription",
            "savings", "invest", "investment", "stocks", "crypto", "portfolio",
            "fees", "fine", "penalty", "refund", "cashback", "receipt",
            "money", "dollars", "euros", "pounds", "currency", "exchange rate"
        ),
        "Learning" to setOf(
            "learn", "study", "read", "book", "course", "tutorial",
            "practice", "skill", "class", "lesson", "lecture", "homework",
            "assignment", "exam", "quiz", "revision", "flashcard", "notes",
            "research", "article", "paper", "chapter", "module", "topic",
            "certificate", "degree", "university", "college", "school",
            "language", "math", "science", "history", "programming", "code"
        ),
        "Social" to setOf(
            "call", "call mom", "call dad", "call friend", "catch up",
            "meet", "visit", "dinner with", "lunch with", "coffee with",
            "birthday", "anniversary", "party", "celebration", "gift",
            "wedding", "event", "gathering", "hang out", "hangout",
            "family", "friend", "parents", "siblings", "relative",
            "date", "relationship", "social", "rsvp", "invitation"
        ),
        "Errand" to setOf(
            "buy", "shop", "shopping", "grocery", "groceries", "supermarket",
            "pickup", "pick up", "drop off", "drop", "deliver", "delivery",
            "mail", "post office", "parcel", "package", "courier",
            "return", "exchange", "collect", "fetch", "get", "grab",
            "fill up", "fuel", "gas", "petrol", "service", "car wash",
            "dry cleaning", "laundry", "repair", "fix", "replace"
        ),
        "Personal" to setOf(
            "clean", "tidy", "organize", "declutter", "arrange",
            "cook", "meal prep", "recipe", "bake",
            "home", "house", "apartment", "room", "garden", "plants",
            "hobby", "project", "creative", "art", "draw", "paint",
            "music", "guitar", "piano", "practice", "journal",
            "plan", "travel", "vacation", "trip", "pack", "flight",
            "movie", "show", "series", "watch", "relax", "rest",
            "personal", "self", "myself", "me"
        )
    )

    // ── Public API ────────────────────────────────────────────────────────────

    data class Classification(val priority: String, val category: String)

    /**
     * Classify a task by its name.
     * Returns a [Classification] with validated Priority and Category strings.
     */
    fun classify(taskName: String): Classification {
        val lower = taskName.lowercase().trim()
        return Classification(
            priority = detectPriority(lower),
            category = detectCategory(lower)
        )
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun detectPriority(lower: String): String {
        // Check HIGH first (urgent tasks should never be demoted to LOW)
        if (HIGH_KEYWORDS.any { keyword -> lower.contains(keyword) }) return "High"
        if (LOW_KEYWORDS.any  { keyword -> lower.contains(keyword) }) return "Low"
        return "Medium"
    }

    private fun detectCategory(lower: String): String {
        // Score every category: +1 per keyword found in the task text
        val scores = CATEGORY_KEYWORDS.mapValues { (_, keywords) ->
            keywords.count { keyword -> lower.contains(keyword) }
        }

        val best = scores.maxByOrNull { it.value }
        // Only pick a category if at least one keyword matched; else default Personal
        return if (best != null && best.value > 0) best.key else "Personal"
    }
}
