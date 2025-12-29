/**
 * Forums Service (Web Version)
 * Manages forum posts and comments
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  query,
  orderBy,
  Timestamp,
} from 'firebase/firestore'

export interface ForumPost {
  id: string
  authorId: string
  authorName: string
  title: string
  content: string
  category?: string
  createdAt: Date
  updatedAt: Date
  likes: number
  comments: ForumComment[]
}

export interface ForumComment {
  id: string
  postId: string
  authorId: string
  authorName: string
  content: string
  createdAt: Date
}

class ForumsService {
  private static instance: ForumsService

  private constructor() {}

  static getInstance(): ForumsService {
    if (!ForumsService.instance) {
      ForumsService.instance = new ForumsService()
    }
    return ForumsService.instance
  }

  /**
   * Get forum posts for a forum
   */
  async getForumPosts(forumId: string): Promise<ForumPost[]> {
    try {
      const postsRef = collection(db, 'forums', forumId, 'posts')
      let postsQuery
      try {
        postsQuery = query(postsRef, orderBy('createdAt', 'desc'))
      } catch (error: any) {
        // If orderBy fails (no index), get all posts and sort manually
        console.warn('Forums index missing, getting posts without orderBy')
        postsQuery = query(postsRef)
      }
      
      const postsSnap = await getDocs(postsQuery)
      const posts: ForumPost[] = []
      
      for (const postDoc of postsSnap.docs) {
        const data = postDoc.data()
        
        // Get comments for this post
        let comments: ForumComment[] = []
        try {
          const commentsRef = collection(db, 'forums', forumId, 'posts', postDoc.id, 'comments')
          const commentsQuery = query(commentsRef, orderBy('createdAt', 'asc'))
          const commentsSnap = await getDocs(commentsQuery)
          comments = commentsSnap.docs.map((doc) => {
            const commentData = doc.data()
            return {
              id: doc.id,
              postId: postDoc.id,
              authorId: commentData.authorId || '',
              authorName: commentData.authorName || 'Unknown',
              content: commentData.content || '',
              createdAt: commentData.createdAt?.toDate() || new Date(),
            } as ForumComment
          })
        } catch (error) {
          console.warn(`Could not load comments for post ${postDoc.id}:`, error)
        }
        
        posts.push({
          id: postDoc.id,
          authorId: data.authorId || '',
          authorName: data.authorName || 'Unknown',
          title: data.title || '',
          content: data.content || '',
          category: data.category,
          createdAt: data.createdAt?.toDate() || new Date(),
          updatedAt: data.updatedAt?.toDate() || new Date(),
          likes: Array.isArray(data.likes) ? data.likes.length : (data.upvotes?.length || 0),
          comments,
        } as ForumPost)
      }
      
      // Sort manually if we didn't use orderBy
      if (posts.length > 0 && !posts[0].createdAt) {
        posts.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
      }
      
      return posts
    } catch (error) {
      console.error('Error getting forum posts:', error)
      return []
    }
  }

  /**
   * Get a forum post by ID (for backward compatibility)
   */
  async getPost(postId: string): Promise<ForumPost | null> {
    // This method is deprecated - use getForumPosts instead
    // But keeping it for backward compatibility
    console.warn('getPost is deprecated - use getForumPosts instead')
    return null
  }

  /**
   * Add a comment to a post
   */
  async addComment(postId: string, userId: string, userName: string, content: string): Promise<string> {
    try {
      const commentsRef = collection(db, 'forumPosts', postId, 'comments')
      const commentRef = doc(commentsRef)

      await setDoc(commentRef, {
        authorId: userId,
        authorName: userName,
        content,
        createdAt: Timestamp.now(),
        platform: 'web',
      })

      return commentRef.id
    } catch (error) {
      console.error('Error adding comment:', error)
      throw error
    }
  }
}

export default ForumsService
