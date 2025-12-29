import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

/**
 * One-time setup function to:
 * 1. Delete old news/wellness news channels
 * 2. Create Coachie News channel
 * 3. Add initial posts to Coachie News
 */
export const setupCoachieNews = functions.https.onCall(async (_data, context) => {
  // Only allow authenticated users (you can restrict this further if needed)
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Must be signed in.');
  }

  try {
    // Step 1: Delete old news channels from forum_channels collection
    const oldChannelIds = ['news', 'wellness-news', 'wellness_news'];
    for (const oldId of oldChannelIds) {
      const oldChannelRef = db.collection('forum_channels').doc(oldId);
      const oldChannelSnap = await oldChannelRef.get();
      if (oldChannelSnap.exists) {
        // Delete all threads and posts in the channel
        const threadsSnapshot = await oldChannelRef.collection('threads').get();
        const batch = db.batch();
        
        for (const threadDoc of threadsSnapshot.docs) {
          // Delete all posts in each thread
          const postsSnapshot = await threadDoc.ref.collection('posts').get();
          postsSnapshot.docs.forEach(postDoc => batch.delete(postDoc.ref));
          // Delete the thread
          batch.delete(threadDoc.ref);
        }
        
        // Delete the channel itself
        batch.delete(oldChannelRef);
        await batch.commit();
        console.log(`Deleted old channel: ${oldId}`);
      }
    }

    // Step 2: Delete old news forums from forums collection
    const forumsSnapshot = await db.collection('forums')
      .where('title', 'in', ['News', 'Wellness News', 'WellnessNews'])
      .get();
    
    for (const forumDoc of forumsSnapshot.docs) {
      // Delete all posts in the forum
      const postsSnapshot = await forumDoc.ref.collection('posts').get();
      const batch = db.batch();
      postsSnapshot.docs.forEach(postDoc => batch.delete(postDoc.ref));
      batch.delete(forumDoc.ref);
      await batch.commit();
      console.log(`Deleted old forum: ${forumDoc.id}`);
    }

    // Step 3: Ensure Coachie News channel exists (forum_channels)
    const coachieNewsChannelRef = db.collection('forum_channels').doc('coachie-news');
    const coachieNewsChannelSnap = await coachieNewsChannelRef.get();
    
    if (!coachieNewsChannelSnap.exists) {
      await coachieNewsChannelRef.set({
        name: 'Coachie News',
        description: 'App updates, new feature announcements, roadmaps, and development updates from the Coachie team',
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        threadsCount: 0,
        postsCount: 0
      });
      console.log('Created Coachie News channel in forum_channels');
    }

    // Step 4: Ensure Coachie News forum exists (forums collection)
    const coachieNewsForumsSnapshot = await db.collection('forums')
      .where('title', '==', 'Coachie News')
      .limit(1)
      .get();
    
    let coachieNewsForumId: string;
    if (coachieNewsForumsSnapshot.empty) {
      const newForumRef = await db.collection('forums').add({
        title: 'Coachie News',
        description: 'App updates, new feature announcements, roadmaps, and development updates from the Coachie team',
        category: 'news',
        createdBy: 'system',
        createdByName: 'Coachie Team',
        postCount: 0,
        isActive: true,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      coachieNewsForumId = newForumRef.id;
      console.log('Created Coachie News forum in forums collection');
    } else {
      coachieNewsForumId = coachieNewsForumsSnapshot.docs[0].id;
      console.log('Coachie News forum already exists');
    }

    // Step 5: Create posts in forum_channels (web/React Native)
    // Use the authenticated user's ID or a system ID
    const systemUserId = context.auth.uid || 'system';
    const systemUserName = 'Coachie Team';

    // Post 1: Coachie is Live for Testing
    const thread1Ref = await coachieNewsChannelRef.collection('threads').add({
      title: '🎉 Coachie is Live for Testing!',
      createdBy: systemUserId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastPostAt: admin.firestore.FieldValue.serverTimestamp(),
      postsCount: 1
    });

    await thread1Ref.collection('posts').add({
      content: `We're excited to announce that Coachie is now live for testing! 🚀

This is your opportunity to explore all the features we've been building and help us make Coachie the best health and wellness companion it can be.

**What to Expect:**
- A comprehensive health and wellness tracking platform
- AI-powered meal analysis and recommendations
- Community features to connect with others on similar journeys
- Personalized insights and progress tracking

**We Need Your Feedback:**
As testers, your feedback is invaluable. Please report any bugs, share your thoughts on features, and let us know what you'd like to see improved. Your input will directly shape the future of Coachie!

Thank you for being part of our testing community! 🙏`,
      createdBy: systemUserId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      replies: 0
    });

    // Post 2: Welcome New Users
    const thread2Ref = await coachieNewsChannelRef.collection('threads').add({
      title: '👋 Welcome to Coachie - Getting Started Guide',
      createdBy: systemUserId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastPostAt: admin.firestore.FieldValue.serverTimestamp(),
      postsCount: 1
    });

    await thread2Ref.collection('posts').add({
      content: `Welcome to Coachie! We're thrilled to have you here. Here's a quick guide to help you get started with the most important features:

**📱 Essential Features to Explore:**

1. **Meal Logging & AI Analysis**
   - Take photos of your meals for instant nutrition analysis
   - Get detailed macro and micronutrient breakdowns
   - Save your favorite meals for quick logging

2. **Personalized Briefs**
   - Receive daily personalized briefs based on your goals and progress
   - Get insights on your nutrition, activity, and habits

3. **Weekly Blueprint**
   - Generate AI-powered meal plans tailored to your preferences
   - Get shopping lists organized by category
   - Adjust serving sizes for individuals or families

4. **Habits & Streaks**
   - Track your daily habits and build consistency
   - Watch your streak grow as you stay committed
   - Join circles to stay accountable with others

5. **Recipe Sharing**
   - Share your favorite recipes with the community
   - Discover new meal ideas from other users
   - Save recipes to your personal collection

6. **Community Forums**
   - Join discussions in various forums
   - Share feedback and feature requests
   - Connect with the Coachie community

**💡 Pro Tips:**
- Complete your profile and goals for the most accurate recommendations
- Set up 3-5 habits to track for best results
- Explore the AI meal inspiration feature to discover new recipes
- Check your daily brief regularly for personalized insights

**Need Help?**
- Check out the Help section in the app
- Post questions in the "Bugs & Feedback" forum
- Share your experience in "General Discussion"

We're here to support you on your wellness journey. Happy tracking! 🌟`,
      createdBy: systemUserId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      replies: 0
    });

    // Post 3: Welcome & Encourage Forum Participation
    const thread3Ref = await coachieNewsChannelRef.collection('threads').add({
      title: '💬 Welcome! Share Your Feedback, Bugs & Feature Requests',
      createdBy: systemUserId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastPostAt: admin.firestore.FieldValue.serverTimestamp(),
      postsCount: 1
    });

    await thread3Ref.collection('posts').add({
      content: `Welcome to Coachie! We're so excited to have you here! 🎉

Your feedback is incredibly valuable to us as we continue to build and improve Coachie. We want to make this the best health and wellness companion possible, and that starts with hearing from you.

**📢 How to Get Involved:**

**🐛 Found a Bug?**
- Head to the "Bugs & Feedback" forum channel
- Create a new post describing the issue
- Include steps to reproduce if possible
- Screenshots are always helpful!

**💡 Have a Feature Request?**
- Share your ideas in the "Feature Requests" forum channel
- Upvote features you'd like to see from other users
- The most upvoted features get prioritized in our roadmap

**💬 General Feedback?**
- Use the "General Discussion" forum to share your thoughts
- Tell us what you love about Coachie
- Share what could be improved
- Connect with other users in the community

**⭐ Why Your Voice Matters:**
- Your feedback directly shapes our development priorities
- Bug reports help us fix issues quickly
- Feature requests guide our product roadmap
- Community discussions help us understand your needs

**🚀 Quick Tips:**
- Use the upvote feature to show support for posts you find helpful
- Sort posts by "Top" to see the most popular discussions
- Check back regularly for updates and responses from the team

We're building Coachie together, and your participation makes all the difference. Thank you for being part of our community! 🙏

Happy tracking! 🌟`,
      createdBy: systemUserId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      replies: 0
    });

    // Update channel stats
    await coachieNewsChannelRef.set({
      threadsCount: admin.firestore.FieldValue.increment(3),
      postsCount: admin.firestore.FieldValue.increment(3),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    // Step 6: Create posts in forums collection (Android)
    const forumRef = db.collection('forums').doc(coachieNewsForumId);

    // Post 1: Coachie is Live for Testing
    await forumRef.collection('posts').add({
      title: '🎉 Coachie is Live for Testing!',
      content: `We're excited to announce that Coachie is now live for testing! 🚀

This is your opportunity to explore all the features we've been building and help us make Coachie the best health and wellness companion it can be.

**What to Expect:**
- A comprehensive health and wellness tracking platform
- AI-powered meal analysis and recommendations
- Community features to connect with others on similar journeys
- Personalized insights and progress tracking

**We Need Your Feedback:**
As testers, your feedback is invaluable. Please report any bugs, share your thoughts on features, and let us know what you'd like to see improved. Your input will directly shape the future of Coachie!

Thank you for being part of our testing community! 🙏`,
      authorId: systemUserId,
      authorName: systemUserName,
      forumId: coachieNewsForumId,
      forumTitle: 'Coachie News',
      likes: [],
      upvotes: [],
      commentCount: 0,
      viewCount: 0,
      isPinned: true, // Pin the announcement
      tags: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Post 2: Welcome New Users
    await forumRef.collection('posts').add({
      title: '👋 Welcome to Coachie - Getting Started Guide',
      content: `Welcome to Coachie! We're thrilled to have you here. Here's a quick guide to help you get started with the most important features:

**📱 Essential Features to Explore:**

1. **Meal Logging & AI Analysis**
   - Take photos of your meals for instant nutrition analysis
   - Get detailed macro and micronutrient breakdowns
   - Save your favorite meals for quick logging

2. **Personalized Briefs**
   - Receive daily personalized briefs based on your goals and progress
   - Get insights on your nutrition, activity, and habits

3. **Weekly Blueprint**
   - Generate AI-powered meal plans tailored to your preferences
   - Get shopping lists organized by category
   - Adjust serving sizes for individuals or families

4. **Habits & Streaks**
   - Track your daily habits and build consistency
   - Watch your streak grow as you stay committed
   - Join circles to stay accountable with others

5. **Recipe Sharing**
   - Share your favorite recipes with the community
   - Discover new meal ideas from other users
   - Save recipes to your personal collection

6. **Community Forums**
   - Join discussions in various forums
   - Share feedback and feature requests
   - Connect with the Coachie community

**💡 Pro Tips:**
- Complete your profile and goals for the most accurate recommendations
- Set up 3-5 habits to track for best results
- Explore the AI meal inspiration feature to discover new recipes
- Check your daily brief regularly for personalized insights

**Need Help?**
- Check out the Help section in the app
- Post questions in the "Bugs & Feedback" forum
- Share your experience in "General Discussion"

We're here to support you on your wellness journey. Happy tracking! 🌟`,
      authorId: systemUserId,
      authorName: systemUserName,
      forumId: coachieNewsForumId,
      forumTitle: 'Coachie News',
      likes: [],
      upvotes: [],
      commentCount: 0,
      viewCount: 0,
      isPinned: true, // Pin the welcome post
      tags: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Post 3: Welcome & Encourage Forum Participation
    await forumRef.collection('posts').add({
      title: '💬 Welcome! Share Your Feedback, Bugs & Feature Requests',
      content: `Welcome to Coachie! We're so excited to have you here! 🎉

Your feedback is incredibly valuable to us as we continue to build and improve Coachie. We want to make this the best health and wellness companion possible, and that starts with hearing from you.

**📢 How to Get Involved:**

**🐛 Found a Bug?**
- Head to the "Bugs & Feedback" forum channel
- Create a new post describing the issue
- Include steps to reproduce if possible
- Screenshots are always helpful!

**💡 Have a Feature Request?**
- Share your ideas in the "Feature Requests" forum channel
- Upvote features you'd like to see from other users
- The most upvoted features get prioritized in our roadmap

**💬 General Feedback?**
- Use the "General Discussion" forum to share your thoughts
- Tell us what you love about Coachie
- Share what could be improved
- Connect with other users in the community

**⭐ Why Your Voice Matters:**
- Your feedback directly shapes our development priorities
- Bug reports help us fix issues quickly
- Feature requests guide our product roadmap
- Community discussions help us understand your needs

**🚀 Quick Tips:**
- Use the upvote feature to show support for posts you find helpful
- Sort posts by "Top" to see the most popular discussions
- Check back regularly for updates and responses from the team

We're building Coachie together, and your participation makes all the difference. Thank you for being part of our community! 🙏

Happy tracking! 🌟`,
      authorId: systemUserId,
      authorName: systemUserName,
      forumId: coachieNewsForumId,
      forumTitle: 'Coachie News',
      likes: [],
      upvotes: [],
      commentCount: 0,
      viewCount: 0,
      isPinned: true, // Pin the welcome post
      tags: [],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    // Update forum stats
    await forumRef.set({
      postCount: admin.firestore.FieldValue.increment(3),
      lastPostAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    return { 
      ok: true, 
      message: 'Coachie News setup complete!',
      deletedChannels: oldChannelIds.length,
      deletedForums: forumsSnapshot.size,
      createdPosts: 6 // 3 in forum_channels, 3 in forums
    };
  } catch (error: any) {
    console.error('Error setting up Coachie News:', error);
    throw new functions.https.HttpsError('internal', `Failed to setup Coachie News: ${error.message}`);
  }
});

