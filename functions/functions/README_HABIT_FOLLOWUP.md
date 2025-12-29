# Habit Follow-Up Scheduler ⏰

A sophisticated **tiered follow-up system** that gently guides users back to their habits based on consecutive miss patterns.

## 🎯 Mission

**Transform missed habits into learning opportunities** by sending increasingly supportive follow-ups that help users understand and overcome their habit challenges.

## ⏰ Schedule & Timing

- **Runs Daily**: 8 PM UTC (adjustable timezone)
- **Processing Window**: Checks habits created that day
- **Follow-up Timing**: Evening reflection time
- **Global Coverage**: Processes all active users simultaneously

## 🔄 Follow-Up Tiers

### **Tier 1: Gentle Reminder** (miss_streak = 1)
```
Title: 💙 Thinking of you
Body: We noticed you might have missed your habit today. No worries - tomorrow is a fresh start!
```
- **Trigger**: First missed habit
- **Tone**: Compassionate, understanding
- **Goal**: Normalize setbacks, encourage tomorrow
- **Action**: None required, just awareness

### **Tier 2: Quick Reply Encouragement** (miss_streak = 3)
```
Title: 🔄 Ready to bounce back?
Body: It's been a few days - what's one small thing you can do today to get back on track?
```
- **Trigger**: Third consecutive miss
- **Tone**: Motivational, action-oriented
- **Goal**: Prompt reflection and planning
- **Action**: Quick reply options available

### **Tier 3: Habit Autopsy Deep Link** (miss_streak ≥ 5)
```
Title: 🔍 Let's figure this out together
Body: It's been a week - let's chat about what's been going on with your habit journey.
```
- **Trigger**: Fifth or more consecutive miss
- **Tone**: Supportive, therapeutic
- **Goal**: Deep understanding and intervention
- **Action**: Deep link to CoachChat for guided conversation

## 🔧 Technical Implementation

### Core Algorithm
```typescript
For each user:
  1. Find habits created today with no completion
  2. Increment miss_streak for each missed habit
  3. Determine tier based on new miss_streak value
  4. Send appropriate notification
  5. Log notification for analytics
```

### Data Flow
```
User Habits → Today's Creations → Completion Check → Miss Detection → Streak Update → Tier Logic → Notification → Analytics Log
```

### Firebase Operations
- **Read**: `users/{userId}/habits` + `users/{userId}/completions`
- **Write**: Update `missStreak` field on habits
- **Log**: Create `notification_logs` entries

## 📊 Smart Detection Logic

### Missed Habit Criteria
- ✅ Habit created today (`createdAt` between start/end of day)
- ✅ Habit is active (`isActive: true`)
- ❌ No completion record for today (`completedAt` not in today range)

### Streak Management
```typescript
// Increment miss_streak
const newMissStreak = currentMissStreak + 1;

// Tier determination
if (newMissStreak === 1) → Tier 1
if (newMissStreak === 3) → Tier 2
if (newMissStreak >= 5) → Tier 3
```

## 📱 Notification System

### Expo Push Integration
- **Token Validation**: Ensures valid Expo push tokens
- **Personalization**: Habit-specific context in messages
- **Deep Linking**: Tier 3 links to CoachChat
- **Analytics**: Full delivery tracking

### Message Personalization
```typescript
// Tier 3 example
personalized.body = "It's been a week - let's chat about what's been going on with your \"Drink Water\" habit journey."
```

## 📈 Analytics & Monitoring

### Success Metrics
- **Delivery Rate**: Notifications successfully sent
- **Tier Distribution**: How many users reach each tier
- **Engagement Rate**: Deep link clicks and chat initiations
- **Recovery Rate**: Users who complete habits after follow-ups

### Logging Structure
```json
{
  "userId": "user123",
  "habitId": "habit456",
  "missStreak": 3,
  "tier": 2,
  "notificationType": "habit_followup",
  "title": "🔄 Ready to bounce back?",
  "body": "It's been a few days...",
  "sentAt": "2024-01-15T20:00:00Z",
  "data": {
    "action": "quick_reply",
    "reply_options": ["I'm ready!", "Need motivation", "Tell me more"]
  }
}
```

## 🎯 Behavioral Psychology

### **Tier 1: Normalization**
- **Why it works**: Removes guilt and shame
- **Psychology**: Self-compassion reduces defensive responses
- **Goal**: Keep the door open for tomorrow

### **Tier 2: Reflection**
- **Why it works**: Prompts metacognition without pressure
- **Psychology**: Small questions lead to big insights
- **Goal**: Help users identify barriers

### **Tier 3: Intervention**
- **Why it works**: Expert guidance for complex issues
- **Psychology**: Therapeutic alliance builds trust
- **Goal**: Transform understanding into action

## 🔄 User Journey Examples

### **Scenario A: Quick Recovery**
```
Day 1: Miss habit → Tier 1 (gentle reminder)
Day 2: Complete habit → Streak resets to 0
Result: User recovers quickly with minimal intervention
```

### **Scenario B: Pattern Recognition**
```
Day 1: Miss → Tier 1
Day 3: Miss → Tier 2 (reflection prompt)
Day 4: Complete habit → User identifies "work stress" barrier
Result: User gains self-awareness and overcomes obstacle
```

### **Scenario C: Deep Support**
```
Day 1: Miss → Tier 1
Day 3: Miss → Tier 2
Day 5: Miss → Tier 3 (deep link to chat)
Result: Coachie helps user redesign habit or address root causes
```

## ⚙️ Configuration

### Schedule Customization
```typescript
export const habitFollowUpScheduler = functions.pubsub
  .schedule('0 20 * * *') // 8 PM UTC - adjust as needed
  .timeZone('UTC')        // Change timezone for different regions
  .onRun(async (context) => {
    // Implementation
  });
```

### Tier Thresholds
```typescript
const TIER_THRESHOLDS = {
  GENTLE: 1,      // First miss
  REFLECTION: 3,  // Third miss
  INTERVENTION: 5 // Fifth miss
};
```

### Notification Content
```typescript
// Easily customizable notification copy
const TIER_1_COPY = {
  title: "💙 Thinking of you",
  body: "Custom message for first miss",
  // ...
};
```

## 🚨 Error Handling & Resilience

### Graceful Degradation
- **Missing Push Tokens**: Skip notifications, continue processing
- **Firestore Errors**: Log errors, don't stop batch processing
- **Expo API Failures**: Retry logic with exponential backoff

### Monitoring & Alerts
- **Success Rate Tracking**: Alert if delivery rate drops below 95%
- **Processing Time**: Monitor function execution duration
- **Error Patterns**: Detect and alert on systematic failures

## 🔮 Advanced Features (Future)

### **Smart Timing**
- **Timezone Detection**: Send at optimal local time
- **User Preferences**: Respect quiet hours
- **Circadian Rhythm**: Time notifications based on chronotype

### **Personalization Engine**
- **Habit History**: Consider past recovery patterns
- **User Tendencies**: Adapt based on Four Tendencies results
- **Context Awareness**: Weather, location, calendar events

### **A/B Testing Framework**
- **Message Variants**: Test different copy effectiveness
- **Timing Variations**: Optimal send times
- **Tier Thresholds**: Experiment with intervention points

## 📊 Performance Optimization

### Scalability Considerations
- **Batch Processing**: Handle thousands of users efficiently
- **Query Optimization**: Compound indexes for fast lookups
- **Caching Strategy**: Cache user tokens and preferences

### Resource Management
- **Memory Usage**: Process users in chunks if needed
- **Timeout Handling**: 9-minute function timeout management
- **Rate Limiting**: Respect Expo push API limits

## 🎯 Success Metrics Dashboard

### Key Performance Indicators
- **Recovery Rate**: % of Tier 1 users who complete next day
- **Engagement Rate**: % of Tier 2 users who interact with replies
- **Intervention Success**: % of Tier 3 users who restart habits
- **Overall Retention**: Habit completion rates over time

### User Experience Metrics
- **Notification Fatigue**: Users who disable notifications
- **Deep Link Usage**: Chat session initiation rates
- **Satisfaction Scores**: Post-intervention user feedback

---

**This follow-up system transforms habit misses into opportunities for growth, ensuring users never feel alone in their journey. 🌱➡️🌳**
