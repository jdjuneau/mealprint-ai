import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Get FCM tokens for a user (supports both single token and array)
 */
async function getUserFcmTokens(userId: string): Promise<string[]> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      return [];
    }
    
    const data = userDoc.data();
    const tokens: string[] = [];
    
    // Support both fcmToken (legacy) and fcmTokens (array)
    if (data?.fcmTokens && Array.isArray(data.fcmTokens)) {
      tokens.push(...data.fcmTokens);
    } else if (data?.fcmToken) {
      tokens.push(data.fcmToken);
    }
    
    return tokens.filter(token => token && token.trim().length > 0);
  } catch (error) {
    console.error(`Error getting FCM tokens for user ${userId}:`, error);
    return [];
  }
}

/**
 * Get user's display name
 */
async function getUserName(userId: string): Promise<string> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      return 'Someone';
    }
    const data = userDoc.data();
    return data?.name || 'Someone';
  } catch (error) {
    console.error(`Error getting user name for ${userId}:`, error);
    return 'Someone';
  }
}

/**
 * Send FCM notification to user(s)
 */
async function sendFCMNotification(
  tokens: string[],
  title: string,
  body: string,
  data?: Record<string, string>
): Promise<void> {
  if (tokens.length === 0) {
    return;
  }

  try {
    if (tokens.length === 1) {
      // Single token - use send
      await messaging.send({
        token: tokens[0],
        notification: {
          title,
          body,
        },
        data: {
          ...data,
        },
        android: {
          priority: 'high',
          notification: {
            sound: 'default',
            channelId: 'coachie_messages',
          },
        },
        apns: {
          payload: {
            aps: {
              sound: 'default',
              badge: 1,
            },
          },
        },
      });
    } else {
      // Multiple tokens - use multicast
      await messaging.sendMulticast({
        tokens,
        notification: {
          title,
          body,
        },
        data: {
          ...data,
        },
        android: {
          priority: 'high',
          notification: {
            sound: 'default',
            channelId: 'coachie_messages',
          },
        },
        apns: {
          payload: {
            aps: {
              sound: 'default',
              badge: 1,
            },
          },
        },
      });
    }
    console.log(`Sent notification to ${tokens.length} device(s): ${title}`);
  } catch (error) {
    console.error('Error sending FCM notification:', error);
    throw error;
  }
}

/**
 * Triggered when a friend request is created
 */
export const onFriendRequestCreated = functions.firestore
  .document('friendRequests/{requestId}')
  .onCreate(async (snap, context) => {
    const requestData = snap.data();
    const fromUserId = requestData.fromUserId as string;
    const toUserId = requestData.toUserId as string;
    const status = requestData.status as string;

    // Only notify if status is pending
    if (status !== 'pending') {
      return;
    }

    try {
      // Get sender's name
      const senderName = await getUserName(fromUserId);
      
      // Get receiver's FCM tokens
      const tokens = await getUserFcmTokens(toUserId);
      
      if (tokens.length === 0) {
        console.log(`No FCM tokens found for user ${toUserId}`);
        return;
      }

      // Check if it's a circle invitation
      const message = requestData.message as string | undefined;
      const isCircleInvite = message?.includes('invited to join the circle') || false;
      
      const title = isCircleInvite 
        ? 'Circle Invitation' 
        : 'New Friend Request';
      
      const body = isCircleInvite
        ? `${senderName} invited you to join a circle`
        : `${senderName} sent you a friend request`;

      await sendFCMNotification(tokens, title, body, {
        type: 'friend_request',
        requestId: snap.id,
        fromUserId,
        toUserId,
        ...(isCircleInvite && { isCircleInvite: 'true' }),
      });

      console.log(`Friend request notification sent to ${toUserId} from ${fromUserId}`);
    } catch (error) {
      console.error('Error sending friend request notification:', error);
    }
  });

/**
 * Triggered when a message is created in a conversation
 */
export const onMessageCreated = functions.firestore
  .document('conversations/{conversationId}/messages/{messageId}')
  .onCreate(async (snap, context) => {
    const messageData = snap.data();
    const senderId = messageData.senderId as string;
    const receiverId = messageData.receiverId as string;
    const content = (messageData.content as string) || '';

    // Don't notify if sender is the same as receiver (shouldn't happen, but safety check)
    if (senderId === receiverId) {
      return;
    }

    try {
      // Get sender's name
      const senderName = await getUserName(senderId);
      
      // Get receiver's FCM tokens
      const tokens = await getUserFcmTokens(receiverId);
      
      if (tokens.length === 0) {
        console.log(`No FCM tokens found for user ${receiverId}`);
        return;
      }

      const conversationId = context.params.conversationId;
      const title = `New message from ${senderName}`;
      const body = content.length > 100 ? content.substring(0, 100) + '...' : content;

      await sendFCMNotification(tokens, title, body, {
        type: 'message',
        conversationId,
        senderId,
        receiverId,
        messageId: snap.id,
      });

      console.log(`Message notification sent to ${receiverId} from ${senderId}`);
    } catch (error) {
      console.error('Error sending message notification:', error);
    }
  });

/**
 * Triggered when a circle post is created (FCM version - notifies circle members)
 */
export const onCirclePostCreatedFCM = functions.firestore
  .document('circles/{circleId}/feed/{postId}')
  .onCreate(async (snap, context) => {
    const postData = snap.data();
    const authorId = postData.createdBy as string;
    const content = (postData.content as string) || '';
    const circleId = context.params.circleId;

    try {
      // Get circle data to find members
      const circleDoc = await db.collection('circles').doc(circleId).get();
      if (!circleDoc.exists) {
        console.error(`Circle ${circleId} not found`);
        return;
      }

      const circleData = circleDoc.data();
      const members = (circleData?.members as string[]) || [];
      
      // Get author's name
      const authorName = await getUserName(authorId);

      // Notify all members except the author
      const membersToNotify = members.filter((memberId: string) => memberId !== authorId);

      if (membersToNotify.length === 0) {
        console.log(`No members to notify for circle ${circleId}`);
        return;
      }

      // Get all FCM tokens for all members
      const tokenPromises = membersToNotify.map(memberId => getUserFcmTokens(memberId));
      const tokenArrays = await Promise.all(tokenPromises);
      const allTokens = tokenArrays.flat();

      if (allTokens.length === 0) {
        console.log(`No FCM tokens found for circle members`);
        return;
      }

      const title = `${authorName} posted in your circle`;
      const body = content.length > 100 ? content.substring(0, 100) + '...' : content;

      await sendFCMNotification(allTokens, title, body, {
        type: 'circle_post',
        circleId,
        postId: snap.id,
        authorId,
      });

      console.log(`Circle post notification sent to ${membersToNotify.length} members`);
    } catch (error) {
      console.error('Error sending circle post notification:', error);
    }
  });

/**
 * Triggered when a circle post is commented on
 */
export const onCirclePostCommented = functions.firestore
  .document('circles/{circleId}/feed/{postId}/comments/{commentId}')
  .onCreate(async (snap, context) => {
    const commentData = snap.data();
    const commenterId = commentData.userId as string;
    const commentContent = (commentData.content as string) || '';
    const circleId = context.params.circleId;
    const postId = context.params.postId;

    try {
      // Get the post to find the author
      const postDoc = await db.collection('circles').doc(circleId)
        .collection('feed').doc(postId).get();
      
      if (!postDoc.exists) {
        console.error(`Post ${postId} not found`);
        return;
      }

      const postData = postDoc.data();
      const postAuthorId = postData?.createdBy as string;

      // Don't notify if commenter is the post author
      if (commenterId === postAuthorId) {
        return;
      }

      // Get commenter's name
      const commenterName = await getUserName(commenterId);
      
      // Get post author's FCM tokens
      const tokens = await getUserFcmTokens(postAuthorId);
      
      if (tokens.length === 0) {
        console.log(`No FCM tokens found for user ${postAuthorId}`);
        return;
      }

      const title = `${commenterName} commented on your post`;
      const body = commentContent.length > 100 ? commentContent.substring(0, 100) + '...' : commentContent;

      await sendFCMNotification(tokens, title, body, {
        type: 'circle_comment',
        circleId,
        postId,
        commentId: snap.id,
        commenterId,
      });

      console.log(`Circle comment notification sent to ${postAuthorId} from ${commenterId}`);
    } catch (error) {
      console.error('Error sending circle comment notification:', error);
    }
  });

