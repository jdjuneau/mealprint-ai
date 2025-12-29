'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { doc, getDoc, collection, query, orderBy, getDocs, addDoc, updateDoc, Timestamp } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface ForumPost {
  id: string
  title: string
  content: string
  authorId: string
  authorName: string
  upvotes: number
  comments: ForumComment[]
  createdAt: Date
  category: string
}

interface ForumComment {
  id: string
  authorId: string
  authorName: string
  content: string
  createdAt: Date
}

export default function ForumDetail({ postId }: { postId: string }) {
  const { user } = useAuth()
  const [post, setPost] = useState<ForumPost | null>(null)
  const [comments, setComments] = useState<ForumComment[]>([])
  const [commentInput, setCommentInput] = useState('')
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (user && postId) {
      loadPost()
      loadComments()
    }
  }, [user, postId])

  const loadPost = async () => {
    if (!postId) return

    try {
      const postDoc = await getDoc(doc(db, 'forumPosts', postId))
      if (postDoc.exists()) {
        const data = postDoc.data()
        setPost({
          id: postDoc.id,
          title: data.title || '',
          content: data.content || '',
          authorId: data.authorId || '',
          authorName: data.authorName || 'Anonymous',
          upvotes: data.upvotes || 0,
          comments: [],
          createdAt: data.createdAt?.toDate() || new Date(),
          category: data.category || 'general'
        })
      }
    } catch (error) {
      console.error('Error loading post:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const loadComments = async () => {
    if (!postId) return

    try {
      const commentsRef = collection(db, 'forumPosts', postId, 'comments')
      const q = query(commentsRef, orderBy('createdAt', 'asc'))
      const snapshot = await getDocs(q)
      
      const loadedComments: ForumComment[] = []
      snapshot.forEach(doc => {
        const data = doc.data()
        loadedComments.push({
          id: doc.id,
          authorId: data.authorId || '',
          authorName: data.authorName || 'Anonymous',
          content: data.content || '',
          createdAt: data.createdAt?.toDate() || new Date()
        })
      })
      
      setComments(loadedComments)
    } catch (error) {
      console.error('Error loading comments:', error)
    }
  }

  const addComment = async () => {
    if (!user || !postId || !commentInput.trim()) return

    try {
      const commentsRef = collection(db, 'forumPosts', postId, 'comments')
      await addDoc(commentsRef, {
        authorId: user.uid,
        authorName: user.displayName || 'Anonymous',
        content: commentInput.trim(),
        createdAt: Timestamp.now()
      })
      
      setCommentInput('')
      loadComments()
    } catch (error) {
      console.error('Error adding comment:', error)
    }
  }

  const upvotePost = async () => {
    if (!user || !post) return

    try {
      const postRef = doc(db, 'forumPosts', post.id)
      await updateDoc(postRef, {
        upvotes: (post.upvotes || 0) + 1
      })
      setPost({ ...post, upvotes: (post.upvotes || 0) + 1 })
    } catch (error) {
      console.error('Error upvoting post:', error)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">💬</div>
          <p className="text-gray-600">Loading post...</p>
        </div>
      </div>
    )
  }

  if (!post) {
    return (
      <div className="max-w-4xl mx-auto">
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">❌</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Post Not Found</h2>
            <p className="text-gray-600">This post doesn't exist or has been removed.</p>
          </div>
        </CoachieCard>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Post */}
      <CoachieCard>
        <div className="p-6">
          <div className="flex items-start justify-between mb-4">
            <div className="flex-1">
              <h1 className="text-3xl font-bold text-gray-900 mb-2">{post.title}</h1>
              <div className="flex items-center gap-3 text-sm text-gray-500">
                <span>By {post.authorName}</span>
                <span>•</span>
                <span>{post.createdAt.toLocaleDateString()}</span>
                <span>•</span>
                <span>{post.category}</span>
              </div>
            </div>
            <button
              onClick={upvotePost}
              className="flex flex-col items-center px-4 py-2 hover:bg-gray-50 rounded-lg"
            >
              <span className="text-2xl">⬆️</span>
              <span className="text-sm font-semibold">{post.upvotes}</span>
            </button>
          </div>
          <p className="text-gray-700 whitespace-pre-wrap">{post.content}</p>
        </div>
      </CoachieCard>

      {/* Comments */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">Comments ({comments.length})</h2>
          
          {/* Add Comment */}
          <div className="mb-6">
            <textarea
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="Add a comment..."
              className="w-full px-4 py-3 border border-gray-300 rounded-lg mb-3 focus:outline-none focus:ring-2 focus:ring-primary-500"
              rows={3}
            />
            <CoachieButton onClick={addComment} disabled={!commentInput.trim()}>
              Post Comment
            </CoachieButton>
          </div>

          {/* Comments List */}
          <div className="space-y-4">
            {comments.length === 0 ? (
              <p className="text-gray-600 text-center py-8">No comments yet. Be the first to comment!</p>
            ) : (
              comments.map((comment) => (
                <div key={comment.id} className="p-4 bg-gray-50 rounded-lg">
                  <div className="flex items-start justify-between mb-2">
                    <p className="font-semibold text-gray-900">{comment.authorName}</p>
                    <p className="text-sm text-gray-500">
                      {comment.createdAt.toLocaleDateString()}
                    </p>
                  </div>
                  <p className="text-gray-700">{comment.content}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </CoachieCard>
    </div>
  )
}

