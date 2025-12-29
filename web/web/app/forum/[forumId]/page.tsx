'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { doc, getDoc, collection, query, orderBy, getDocs, where } from 'firebase/firestore'
import { db } from '../../../lib/firebase'
import LoadingScreen from '../../../components/LoadingScreen'
import CoachieCard from '../../../components/ui/CoachieCard'
import CoachieButton from '../../../components/ui/CoachieButton'

interface Forum {
  id: string
  title: string
  description?: string
  category?: string
  postCount?: number
}

interface ForumPost {
  id: string
  title: string
  content: string
  authorId: string
  authorName: string
  upvotes: number
  likes: string[]
  createdAt: any
  forumId: string
  forumTitle: string
}

export default function ForumPage({ params }: { params: Promise<{ forumId: string }> }) {
  const { user } = useAuth()
  const router = useRouter()
  const [forum, setForum] = useState<Forum | null>(null)
  const [posts, setPosts] = useState<ForumPost[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [sortBy, setSortBy] = useState<'top' | 'new'>('top')
  const [forumId, setForumId] = useState<string>('')

  useEffect(() => {
    params.then((p) => {
      setForumId(p.forumId)
    })
  }, [params])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
      return
    }
    if (forumId) {
      loadForum()
      loadPosts()
    }
  }, [user, forumId])

  const loadForum = async () => {
    if (!forumId) return
    try {
      const forumDoc = await getDoc(doc(db, 'forums', forumId))
      if (forumDoc.exists()) {
        const data = forumDoc.data()
        setForum({
          id: forumDoc.id,
          title: data.title || 'Untitled Forum',
          description: data.description,
          category: data.category,
          postCount: data.postCount || 0
        })
      }
    } catch (error) {
      console.error('Error loading forum:', error)
    }
  }

  const loadPosts = async () => {
    if (!forumId) return
    try {
      setIsLoading(true)
      // Posts are stored in forums/{forumId}/posts
      const postsRef = collection(db, 'forums', forumId, 'posts')
      let postsQuery
      
      if (sortBy === 'top') {
        // Sort by upvotes (descending)
        try {
          postsQuery = query(postsRef, orderBy('upvoteCount', 'desc'))
        } catch (e: any) {
          // If index doesn't exist, try without orderBy
          if (e.code === 'failed-precondition') {
            postsQuery = query(postsRef)
          } else {
            throw e
          }
        }
      } else {
        // Sort by date (newest first)
        try {
          postsQuery = query(postsRef, orderBy('createdAt', 'desc'))
        } catch (e: any) {
          if (e.code === 'failed-precondition') {
            postsQuery = query(postsRef)
          } else {
            throw e
          }
        }
      }

      const snapshot = await getDocs(postsQuery)
      const loadedPosts: ForumPost[] = []
      
      snapshot.forEach((doc) => {
        const data = doc.data()
        loadedPosts.push({
          id: doc.id,
          title: data.title || '',
          content: data.content || '',
          authorId: data.authorId || '',
          authorName: data.authorName || 'Unknown',
          upvotes: data.upvoteCount || 0,
          likes: data.likes || [],
          createdAt: data.createdAt,
          forumId: forumId,
          forumTitle: forum?.title || ''
        })
      })

      // If sorting by top and orderBy failed, sort in memory
      if (sortBy === 'top' && loadedPosts.length > 0 && !loadedPosts[0].upvotes) {
        loadedPosts.sort((a, b) => (b.upvotes || 0) - (a.upvotes || 0))
      }

      setPosts(loadedPosts)
    } catch (error) {
      console.error('Error loading posts:', error)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (forumId) {
      loadPosts()
    }
  }, [sortBy, forumId])

  if (isLoading && !forum) {
    return <LoadingScreen />
  }

  if (!forum) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4">
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">💬</div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Forum Not Found</h2>
            <p className="text-gray-600 mb-6">The forum you're looking for doesn't exist</p>
            <CoachieButton onClick={() => router.push('/community')}>
              Back to Community
            </CoachieButton>
          </div>
        </CoachieCard>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      {/* Header */}
      <div className="mb-6">
        <button
          onClick={() => router.push('/community')}
          className="text-blue-600 hover:text-blue-700 mb-4"
        >
          ← Back to Community
        </button>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">{forum.title}</h1>
        {forum.description && (
          <p className="text-gray-600 mb-4">{forum.description}</p>
        )}
        <div className="flex items-center justify-between">
          <div className="flex gap-4 text-sm text-gray-500">
            {forum.category && (
              <span className="px-2 py-1 bg-gray-100 rounded">{forum.category}</span>
            )}
            <span>{posts.length} posts</span>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setSortBy('top')}
              className={`px-4 py-2 rounded-lg text-sm font-medium ${
                sortBy === 'top'
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              Top
            </button>
            <button
              onClick={() => setSortBy('new')}
              className={`px-4 py-2 rounded-lg text-sm font-medium ${
                sortBy === 'new'
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              New
            </button>
          </div>
        </div>
      </div>

      {/* Posts */}
      {isLoading ? (
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-4xl mb-4">💬</div>
            <p className="text-gray-600">Loading posts...</p>
          </div>
        </CoachieCard>
      ) : posts.length === 0 ? (
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">💬</div>
            <h3 className="text-xl font-semibold text-gray-900 mb-2">No Posts Yet</h3>
            <p className="text-gray-600 mb-6">Be the first to start a discussion!</p>
            <CoachieButton onClick={() => {/* TODO: Create post dialog */}}>
              Create Post
            </CoachieButton>
          </div>
        </CoachieCard>
      ) : (
        <div className="space-y-4">
          {posts.map((post) => (
            <CoachieCard
              key={post.id}
              className="cursor-pointer hover:shadow-lg transition-shadow"
              onClick={() => router.push(`/forum-detail/${post.id}`)}
            >
              <div className="p-6">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex-1">
                    <h3 className="text-xl font-bold text-gray-900 mb-2">{post.title}</h3>
                    <p className="text-gray-600 line-clamp-2">{post.content}</p>
                  </div>
                </div>
                <div className="flex items-center justify-between text-sm text-gray-500">
                  <div className="flex items-center gap-4">
                    <span>by {post.authorName}</span>
                    <span>↑ {post.upvotes} upvotes</span>
                    <span>❤️ {post.likes.length} likes</span>
                  </div>
                  {post.createdAt && (
                    <span>
                      {post.createdAt.toDate
                        ? new Date(post.createdAt.toDate()).toLocaleDateString()
                        : new Date(post.createdAt).toLocaleDateString()}
                    </span>
                  )}
                </div>
              </div>
            </CoachieCard>
          ))}
        </div>
      )}
    </div>
  )
}
