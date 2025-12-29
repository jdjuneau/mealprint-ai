#!/bin/bash

# 🚀 SETUP CLOUD SCHEDULER FOR BRIEFS
# More reliable than Firebase pubsub schedules

echo "🌅 Setting up Cloud Scheduler for Coachie Briefs..."

PROJECT_ID="vanish-auth-real"
REGION="us-central1"

# Morning Briefs (9 AM EST)
echo "📧 Creating morning briefs job..."
gcloud scheduler jobs create http morning-briefs \
  --project=$PROJECT_ID \
  --schedule="0 9 * * *" \
  --uri="https://$REGION-$PROJECT_ID.cloudfunctions.net/sendMorningBriefs" \
  --http-method=POST \
  --time-zone="America/New_York" \
  --description="Send morning briefs to all users at 9 AM EST"

# Afternoon Briefs (2 PM EST)
echo "☀️ Creating afternoon briefs job..."
gcloud scheduler jobs create http afternoon-briefs \
  --project=$PROJECT_ID \
  --schedule="0 14 * * *" \
  --uri="https://$REGION-$PROJECT_ID.cloudfunctions.net/sendAfternoonBriefs" \
  --http-method=POST \
  --time-zone="America/New_York" \
  --description="Send afternoon briefs to all users at 2 PM EST"

# Evening Briefs (6 PM EST)
echo "🌙 Creating evening briefs job..."
gcloud scheduler jobs create http evening-briefs \
  --project=$PROJECT_ID \
  --schedule="0 18 * * *" \
  --uri="https://$REGION-$PROJECT_ID.cloudfunctions.net/sendEveningBriefs" \
  --http-method=POST \
  --time-zone="America/New_York" \
  --description="Send evening briefs to all users at 6 PM EST"

echo "✅ Cloud Scheduler jobs created successfully!"
echo ""
echo "📋 To verify jobs:"
echo "gcloud scheduler jobs list --project=$PROJECT_ID"
echo ""
echo "🔧 To update a job (e.g., change time):"
echo "gcloud scheduler jobs update http morning-briefs --schedule='0 9 * * *'"
echo ""
echo "🧪 To test a job manually:"
echo "gcloud scheduler jobs run morning-briefs --project=$PROJECT_ID"