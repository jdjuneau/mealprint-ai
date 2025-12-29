/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    domains: ['firebasestorage.googleapis.com'],
  },
  // Disable all caching in development
  async headers() {
    if (process.env.NODE_ENV === 'development') {
      return [
        {
          source: '/:path*',
          headers: [
            {
              key: 'Cache-Control',
              value: 'no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0',
            },
          ],
        },
      ]
    }
    return []
  },
  // For deployment to subdirectory (e.g., /coachieAI)
  // Uncomment if deploying to a subdirectory:
  // basePath: process.env.NODE_ENV === 'production' ? '/coachieAI' : '',
  // trailingSlash: true,
}

module.exports = nextConfig
