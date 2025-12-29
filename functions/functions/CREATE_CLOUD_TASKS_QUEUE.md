# Creating the Cloud Tasks Queue

Before deploying the scalable brief system, you need to create the Cloud Tasks queue.

## Option 1: Using gcloud CLI (Recommended)

```bash
gcloud tasks queues create brief-generation-queue \
  --location=us-central1 \
  --max-dispatches-per-second=10 \
  --max-concurrent-dispatches=100 \
  --max-retry-duration=3600s
```

## Option 2: Using Google Cloud Console

1. Go to [Cloud Tasks Console](https://console.cloud.google.com/cloudtasks)
2. Select project: `vanish-auth-real`
3. Click "Create Queue"
4. Configure:
   - **Name**: `brief-generation-queue`
   - **Location**: `us-central1`
   - **Max dispatches per second**: `10`
   - **Max concurrent dispatches**: `100`
   - **Max retry duration**: `3600s` (1 hour)
5. Click "Create"

## Queue Configuration Explained

- **Max dispatches per second**: Controls how fast tasks are dispatched (10 = 10 users/second)
- **Max concurrent dispatches**: Maximum number of tasks processed simultaneously (100 = 100 users at once)
- **Max retry duration**: How long to retry failed tasks (1 hour = plenty of time for retries)

## Why This Scales

- **10,000 users**: Takes ~17 minutes to enqueue all tasks, then processes 100 at a time = ~100 minutes total
- **100,000 users**: Takes ~3 hours to enqueue, then processes in parallel = ~17 hours total
- **No timeouts**: Each user is processed in a separate function invocation (9 min timeout per user)
- **Automatic retries**: Failed tasks are automatically retried
- **Horizontal scaling**: Cloud Tasks automatically distributes work across multiple function instances

## Verification

After creating the queue, verify it exists:

```bash
gcloud tasks queues describe brief-generation-queue --location=us-central1
```
