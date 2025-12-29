'use client'

import { useState } from 'react'
import CoachieButton from './ui/CoachieButton'

interface SharePlatformDialogProps {
  onDismiss: () => void
  onShareToPlatform: (platform: string | null) => void
  photoUrl?: string | null
  onCapturePhoto?: () => void
  onSelectPhoto?: () => void
  showFriendsOption?: boolean
  onShareWithFriends?: (friendIds: string[]) => void
  friends?: Array<{ id: string; name: string }>
}

export default function SharePlatformDialog({
  onDismiss,
  onShareToPlatform,
  photoUrl,
  onCapturePhoto,
  onSelectPhoto,
  showFriendsOption = false,
  onShareWithFriends,
  friends = []
}: SharePlatformDialogProps) {
  const [selectedFriends, setSelectedFriends] = useState<Set<string>>(new Set())
  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50" onClick={onDismiss}>
      <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4 max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-xl font-bold text-gray-900 mb-4">Share Your Accomplishment</h2>
        
        {/* Photo section */}
        {!photoUrl && (
          <div className="mb-6">
            <p className="text-sm font-semibold text-gray-700 mb-3">Add a photo (optional)</p>
            <div className="flex gap-3">
              {onCapturePhoto && (
                <CoachieButton onClick={onCapturePhoto} className="flex-1">
                  📷 Take Photo
                </CoachieButton>
              )}
              {onSelectPhoto && (
                <CoachieButton onClick={onSelectPhoto} variant="outline" className="flex-1">
                  🖼️ Choose from Gallery
                </CoachieButton>
              )}
            </div>
          </div>
        )}

        {photoUrl && (
          <div className="mb-6">
            <p className="text-sm text-green-600 font-medium mb-2">✓ Photo ready</p>
            {onCapturePhoto && (
              <CoachieButton onClick={onCapturePhoto} variant="outline" size="sm">
                Change Photo
              </CoachieButton>
            )}
          </div>
        )}

        {/* Share with Friends Section */}
        {showFriendsOption && onShareWithFriends && (
          <div className="mb-6">
            <p className="text-sm font-semibold text-gray-700 mb-3">Share with Friends:</p>
            {friends.length === 0 ? (
              <p className="text-sm text-gray-500 mb-3">No friends yet. Add friends to share!</p>
            ) : (
              <div className="max-h-40 overflow-y-auto space-y-2 mb-3">
                {friends.map((friend) => (
                  <label key={friend.id} className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedFriends.has(friend.id)}
                      onChange={(e) => {
                        const newSelected = new Set(selectedFriends)
                        if (e.target.checked) {
                          newSelected.add(friend.id)
                        } else {
                          newSelected.delete(friend.id)
                        }
                        setSelectedFriends(newSelected)
                      }}
                      className="w-4 h-4"
                    />
                    <span className="text-sm text-gray-700">{friend.name}</span>
                  </label>
                ))}
              </div>
            )}
            {selectedFriends.size > 0 && (
              <button
                onClick={() => {
                  onShareWithFriends(Array.from(selectedFriends))
                  setSelectedFriends(new Set())
                }}
                className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium"
              >
                Share with {selectedFriends.size} {selectedFriends.size === 1 ? 'Friend' : 'Friends'}
              </button>
            )}
            <div className="border-t border-gray-200 my-4"></div>
          </div>
        )}

        <div className="mb-6">
          <p className="text-sm font-semibold text-gray-700 mb-3">Share to Social Media:</p>
          
          <div className="space-y-2">
            <button
              onClick={() => onShareToPlatform('facebook')}
              className="w-full px-4 py-3 bg-[#1877F2] text-white rounded-lg hover:bg-[#166FE5] font-medium"
            >
              Share to Facebook
            </button>
            
            <button
              onClick={() => onShareToPlatform('instagram-feed')}
              className="w-full px-4 py-3 bg-[#E4405F] text-white rounded-lg hover:bg-[#D32A4A] font-medium"
            >
              Share to Instagram Feed
            </button>
            
            <button
              onClick={() => onShareToPlatform('instagram-story')}
              className="w-full px-4 py-3 bg-[#833AB4] text-white rounded-lg hover:bg-[#7229A3] font-medium"
            >
              Share to Instagram Story
            </button>
            
            <button
              onClick={() => onShareToPlatform('tiktok')}
              className="w-full px-4 py-3 bg-black text-white rounded-lg hover:bg-gray-900 font-medium"
            >
              Share to TikTok
            </button>
            
            <button
              onClick={() => onShareToPlatform('x')}
              className="w-full px-4 py-3 bg-black text-white rounded-lg hover:bg-gray-900 font-medium"
            >
              Share to X (Twitter)
            </button>
            
            <button
              onClick={() => onShareToPlatform(null)}
              className="w-full px-4 py-3 border-2 border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 font-medium"
            >
              Other Options
            </button>
          </div>
        </div>

        <div className="flex justify-end">
          <button
            onClick={onDismiss}
            className="px-4 py-2 text-gray-600 hover:text-gray-800"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
