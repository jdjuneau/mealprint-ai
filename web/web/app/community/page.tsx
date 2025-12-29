'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import { collection, query, orderBy, limit, getDocs, where, addDoc, serverTimestamp } from 'firebase/firestore'
import { db } from '../../lib/firebase'
import CirclesService, { type Circle } from '../../lib/services/circlesService'

interface Forum {
  id: string
  title: string
  description: string
  category: string
  postCount: number
  lastPostAt?: Date
}

export default function CommunityPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [selectedTab, setSelectedTab] = useState(0) // 0 = Circles, 1 = Forums, 2 = Shared Recipes
  const [circles, setCircles] = useState<Circle[]>([])
  const [forums, setForums] = useState<Forum[]>([])
  const [sharedRecipes, setSharedRecipes] = useState<any[]>([])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadData()
    }
  }, [user, router])

  const createDefaultForums = async () => {
    try {
      // First check what forums already exist
      const forumsRef = collection(db, 'forums')
      let allForumsSnap
      try {
        allForumsSnap = await getDocs(forumsRef)
      } catch (error) {
        console.error('Error checking existing forums:', error)
        return // Can't proceed if we can't read forums
      }
      
      const existingTitles = new Set(
        allForumsSnap.docs.map(doc => doc.data().title?.toLowerCase().trim()).filter(Boolean)
      )

      // Define default forums
      const defaultForums = [
        {
          title: 'Coachie News',
          description: 'App updates, new feature announcements, roadmaps, and development updates from the Coachie team',
          category: 'news',
          createdBy: 'system',
          createdByName: 'Coachie Team',
          isActive: true,
          postCount: 0,
        },
        {
          title: 'Feature Requests',
          description: 'Suggest new features and improvements for Coachie',
          category: 'feature_request',
          createdBy: 'system',
          createdByName: 'Coachie Team',
          isActive: true,
          postCount: 0,
        },
        {
          title: 'Bugs & Feedback',
          description: 'Report bugs and provide feedback about the app',
          category: 'bugs_feedback',
          createdBy: 'system',
          createdByName: 'Coachie Team',
          isActive: true,
          postCount: 0,
        },
        {
          title: 'Recipe Sharing',
          description: 'Share your favorite recipes, meal ideas, and nutrition tips with the community',
          category: 'recipes',
          createdBy: 'system',
          createdByName: 'Coachie Team',
          isActive: true,
          postCount: 0,
        },
        {
          title: 'General Discussion',
          description: 'Talk about anything fitness or wellness related',
          category: 'general',
          createdBy: 'system',
          createdByName: 'Coachie Team',
          isActive: true,
          postCount: 0,
        },
      ]

      let createdCount = 0
      for (const forum of defaultForums) {
        const normalizedTitle = forum.title.toLowerCase().trim()
        if (existingTitles.has(normalizedTitle)) {
          console.log(`Forum "${forum.title}" already exists, skipping`)
          continue
        }

        try {
          await addDoc(forumsRef, {
            ...forum,
            createdAt: serverTimestamp(),
            updatedAt: serverTimestamp(),
          })
          console.log(`✅ Created forum: "${forum.title}"`)
          createdCount++
        } catch (error: any) {
          console.error(`❌ Failed to create forum "${forum.title}":`, error)
          // Log specific error details
          if (error.code) {
            console.error(`  Error code: ${error.code}`)
          }
          if (error.message) {
            console.error(`  Error message: ${error.message}`)
          }
        }
      }

      if (createdCount > 0) {
        console.log(`✅ Created ${createdCount} default forums`)
        // Wait a moment for Firestore to index
        await new Promise(resolve => setTimeout(resolve, 2000))
      } else {
        console.log('ℹ️ All default forums already exist')
      }
    } catch (error: any) {
      console.error('❌ Error creating default forums:', error)
      if (error.code) {
        console.error(`  Error code: ${error.code}`)
      }
      if (error.message) {
        console.error(`  Error message: ${error.message}`)
      }
    }
  }

  const loadData = async () => {
    if (!user) return

    try {
      // Always create default forums first
      await createDefaultForums()

      // Load circles
      const circlesService = CirclesService.getInstance()
      const loadedCircles = await circlesService.getUserCircles(user.uid)
      setCircles(loadedCircles)

      // Load forums - try multiple query strategies
      const forumsRef = collection(db, 'forums')
      let forumsSnap
      try {
        // Try with orderBy first (requires index)
        const forumsQuery = query(forumsRef, where('isActive', '==', true), orderBy('title'))
        forumsSnap = await getDocs(forumsQuery)
      } catch (error: any) {
        // If orderBy fails (no index), try without orderBy
        if (error.code === 'failed-precondition') {
          console.warn('Forums index missing, trying without orderBy')
          try {
            const forumsQuery = query(forumsRef, where('isActive', '==', true))
            forumsSnap = await getDocs(forumsQuery)
          } catch (error2: any) {
            // If that also fails, try getting ALL forums and filtering client-side
            console.warn('Forums query with isActive filter failed, trying all forums:', error2)
            forumsSnap = await getDocs(forumsRef)
          }
        } else {
          // Other error, try without orderBy
          try {
            const forumsQuery = query(forumsRef, where('isActive', '==', true))
            forumsSnap = await getDocs(forumsQuery)
          } catch (error2: any) {
            // Last resort: get all forums
            console.warn('Forums query failed, getting all forums:', error2)
            forumsSnap = await getDocs(forumsRef)
          }
        }
      }
      
      const loadedForums: Forum[] = []
      for (const docSnap of forumsSnap.docs) {
        const data = docSnap.data()
        
        // Filter out inactive forums if we got all forums (client-side filter)
        // If isActive field doesn't exist, assume it's active
        if (data.isActive === false) {
          continue
        }
        
        // Get post count
        const postsRef = collection(db, 'forums', docSnap.id, 'posts')
        let postsSnap
        try {
          postsSnap = await getDocs(postsRef)
        } catch (error) {
          console.warn(`Could not get posts for forum ${docSnap.id}:`, error)
          postsSnap = { size: data.postCount || 0, docs: [] } as any
        }
        
        // Get last post date
        let lastPostAt: Date | undefined
        try {
          const postsQuery = query(postsRef, orderBy('createdAt', 'desc'), limit(1))
          const lastPostSnap = await getDocs(postsQuery)
          lastPostAt = lastPostSnap.empty ? undefined : lastPostSnap.docs[0].data().createdAt?.toDate()
        } catch (error) {
          // If no posts or query fails, try using lastPostAt from forum document
          if (data.lastPostAt) {
            lastPostAt = data.lastPostAt.toDate ? data.lastPostAt.toDate() : new Date(data.lastPostAt)
          }
        }

        loadedForums.push({
          id: docSnap.id,
          title: data.title || 'Untitled Forum',
          description: data.description || '',
          category: data.category || 'general',
          postCount: postsSnap.size || data.postCount || 0,
          lastPostAt,
        })
      }
      
      // Sort forums in desired order
      const defaultForumTitles = ['Coachie News', 'Feature Requests', 'Bugs & Feedback', 'Recipe Sharing', 'General Discussion']
      loadedForums.sort((a, b) => {
        const aIndex = defaultForumTitles.indexOf(a.title)
        const bIndex = defaultForumTitles.indexOf(b.title)
        if (aIndex === -1 && bIndex === -1) return a.title.localeCompare(b.title)
        if (aIndex === -1) return 1
        if (bIndex === -1) return -1
        return aIndex - bIndex
      })
      
      setForums(loadedForums)

      // Load shared recipes
      const recipesRef = collection(db, 'sharedRecipes')
      const recipesQuery = query(recipesRef, orderBy('createdAt', 'desc'), limit(20))
      const recipesSnap = await getDocs(recipesQuery)
      
      setSharedRecipes(
        recipesSnap.docs.map((doc) => {
          const data = doc.data()
          let createdAt = new Date()
          if (data.createdAt) {
            // Handle Firestore Timestamp
            if (data.createdAt.toDate && typeof data.createdAt.toDate === 'function') {
              createdAt = data.createdAt.toDate()
            }
            // Handle already converted Date
            else if (data.createdAt instanceof Date) {
              createdAt = data.createdAt
            }
            // Handle timestamp number
            else if (typeof data.createdAt === 'number') {
              createdAt = new Date(data.createdAt)
            }
            // Handle Firestore Timestamp with toMillis
            else if (data.createdAt.toMillis && typeof data.createdAt.toMillis === 'function') {
              createdAt = new Date(data.createdAt.toMillis())
            }
          }
          return {
            id: doc.id,
            ...data,
            createdAt,
          }
        })
      )
    } catch (error) {
      console.error('Error loading community data:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  const gradientClass = userProfile.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4">
        <button
          onClick={() => router.back()}
          className="mb-4 text-white/90 hover:text-white flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </button>

        <div className="mb-6">
          <h1 className="text-2xl font-bold text-white mb-2">Community</h1>
          <p className="text-white/80">Connect with others on their fitness journey</p>
        </div>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 border-b border-white/20">
          <button
            onClick={() => setSelectedTab(0)}
            className={`px-4 py-2 font-medium transition-colors ${
              selectedTab === 0
                ? 'text-white border-b-2 border-white'
                : 'text-white/70 hover:text-white'
            }`}
          >
            Circles
          </button>
          <button
            onClick={() => setSelectedTab(1)}
            className={`px-4 py-2 font-medium transition-colors ${
              selectedTab === 1
                ? 'text-white border-b-2 border-white'
                : 'text-white/70 hover:text-white'
            }`}
          >
            Forums
          </button>
          <button
            onClick={() => setSelectedTab(2)}
            className={`px-4 py-2 font-medium transition-colors ${
              selectedTab === 2
                ? 'text-white border-b-2 border-white'
                : 'text-white/70 hover:text-white'
            }`}
          >
            Shared Recipes
          </button>
        </div>

        {/* Tab Content */}
        {selectedTab === 0 && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
              <button
                onClick={() => router.push('/circle-join')}
                className="p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors text-left"
              >
                <div className="text-2xl mb-2">👥</div>
                <div className="font-semibold text-white">Join Circle</div>
                <div className="text-sm text-white/70">Join a fitness circle</div>
              </button>

              <button
                onClick={() => router.push('/circle-create')}
                className="p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors text-left"
              >
                <div className="text-2xl mb-2">➕</div>
                <div className="font-semibold text-white">Create Circle</div>
                <div className="text-sm text-white/70">Start your own circle</div>
              </button>

              <button
                onClick={() => router.push('/friends-list')}
                className="p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors text-left"
              >
                <div className="text-2xl mb-2">👫</div>
                <div className="font-semibold text-white">Friends</div>
                <div className="text-sm text-white/70">Manage your friends</div>
              </button>

              <button
                onClick={() => router.push('/messaging')}
                className="p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors text-left"
              >
                <div className="text-2xl mb-2">💬</div>
                <div className="font-semibold text-white">Messages</div>
                <div className="text-sm text-white/70">Chat with friends</div>
              </button>
            </div>

            <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-6 border border-white/20">
              <h2 className="text-lg font-semibold text-white mb-4">Your Circles</h2>
              {circles.length === 0 ? (
                <div className="text-center py-8 text-white/70">
                  <p>Your circles will appear here</p>
                  <button
                    onClick={() => router.push('/circle-join')}
                    className="mt-4 px-4 py-2 bg-white/20 text-white rounded-lg hover:bg-white/30 font-medium"
                  >
                    Join a Circle
                  </button>
                </div>
              ) : (
                <div className="space-y-3">
                  {circles.map((circle) => (
                    <button
                      key={circle.id}
                      onClick={() => router.push(`/circle-detail/${circle.id}`)}
                      className="w-full text-left p-4 bg-white/5 rounded-lg border border-white/10 hover:bg-white/10 transition-colors"
                    >
                      <div className="font-semibold text-white mb-1">{circle.name}</div>
                      <div className="text-sm text-white/70">{circle.goal}</div>
                      <div className="text-xs text-white/60 mt-2">
                        {circle.memberIds?.length || 0} members
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </>
        )}

        {selectedTab === 1 && (
          <div className="space-y-4">
            <h2 className="text-xl font-bold text-white mb-4">Community Forums</h2>
            {forums.length === 0 ? (
              <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-12 text-center border border-white/20">
                <div className="text-4xl mb-4">💬</div>
                <h3 className="text-lg font-semibold text-white mb-2">No forums available</h3>
                <p className="text-white/70">Forums will appear here once they're created</p>
              </div>
            ) : (
              forums.map((forum) => (
                <button
                  key={forum.id}
                  onClick={() => router.push(`/forum/${forum.id}`)}
                  className="w-full text-left p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors"
                >
                  <h3 className="font-semibold text-white mb-2">{forum.title}</h3>
                  <p className="text-sm text-white/70 mb-3">{forum.description}</p>
                  <div className="flex items-center justify-between text-xs text-white/60">
                    <span>{forum.postCount} posts</span>
                    {forum.lastPostAt && (
                      <span>Last post: {forum.lastPostAt.toLocaleDateString()}</span>
                    )}
                  </div>
                </button>
              ))
            )}
          </div>
        )}

        {selectedTab === 2 && (
          <div className="space-y-4">
            <h2 className="text-xl font-bold text-white mb-4">Shared Recipes</h2>
            {sharedRecipes.length === 0 ? (
              <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-12 text-center border border-white/20">
                <div className="text-4xl mb-4">🍽️</div>
                <h3 className="text-lg font-semibold text-white mb-2">No shared recipes</h3>
                <p className="text-white/70">Recipes shared by the community will appear here</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {sharedRecipes.map((recipe) => (
                  <button
                    key={recipe.id}
                    onClick={() => router.push(`/recipe-detail/${recipe.id}`)}
                    className="p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors text-left"
                  >
                    <h3 className="font-semibold text-white mb-2">{recipe.name || recipe.title}</h3>
                    <p className="text-sm text-white/70 mb-2">By {recipe.authorName || recipe.author || 'Unknown'}</p>
                    <p className="text-xs text-white/60">
                      {recipe.calories || 0} cal • {recipe.servings || 2} servings
                    </p>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
