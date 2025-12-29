'use client'

import { useAuth } from '../../../lib/contexts/AuthContext'
import { useRouter, useParams } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../../components/LoadingScreen'
import toast from 'react-hot-toast'

export default function ForumDetailPage() {
  const { user } = useAuth()
  const router = useRouter()
  const params = useParams()
  const postId = params.postId as string
  const [loading, setLoading] = useState(true)
  const [post, setPost] = useState<any>(null)
  const [comment, setComment] = useState('')

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadPost()
    }
  }, [user, router, postId])

  const loadPost = async () => {
    if (!user || !postId) return
    try {
      const ForumsService = (await import('../../../lib/services/forumsService')).default
      const forumsService = ForumsService.getInstance()
      const loadedPost = await forumsService.getPost(postId)
      setPost(loadedPost)
    } catch (error) {
      console.error('Error loading post:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleComment = async () => {
    // TODO: Add comment to post
    setComment('')
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto py-8 px-4">
        {post ? (
          <div className="space-y-6">
            <div className="bg-white rounded-lg shadow-sm p-6">
              <h1 className="text-2xl font-bold text-gray-900 mb-2">{post.title}</h1>
              <p className="text-gray-600 mb-4">{post.content}</p>
              <p className="text-sm text-gray-500">By {post.author} • {new Date(post.createdAt).toLocaleDateString()}</p>
            </div>

            <div className="bg-white rounded-lg shadow-sm p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Comments</h2>
              <div className="space-y-4 mb-4">
                {post.comments?.map((comment: any, index: number) => (
                  <div key={index} className="border-b border-gray-200 pb-4">
                    <p className="text-gray-700">{comment.content}</p>
                    <p className="text-xs text-gray-500 mt-1">By {comment.author}</p>
                  </div>
                ))}
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  placeholder="Add a comment..."
                  className="flex-1 px-4 py-2 border border-gray-300 rounded-lg"
                />
                <button
                  onClick={handleComment}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Post
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <p className="text-gray-600">Post not found</p>
          </div>
        )}
      </div>
    </div>
  )
}
