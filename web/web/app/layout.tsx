import './globals.css'
import { Inter } from 'next/font/google'
import { AuthProvider } from '../lib/contexts/AuthContext'
import { Toaster } from 'react-hot-toast'
import ServiceWorkerRegistration from '../components/ServiceWorkerRegistration'

const inter = Inter({ subsets: ['latin'] })

export const metadata = {
  title: 'Mealprint AI - Smart Meal Planning Made Social',
  description: 'AI-powered meal planning with social features. Discover recipes, plan meals, and connect with food lovers.',
  icons: {
    icon: '/favicon.ico',
    shortcut: '/favicon.ico',
    apple: '/icon-192.png',
  },
  manifest: '/manifest.json',
  themeColor: '#2563eb',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={inter.className}>
        {/* Temporarily disabled service worker to fix loading issues */}
        {/* <ServiceWorkerRegistration /> */}
        <AuthProvider>
          {children}
          <Toaster
            position="top-right"
            toastOptions={{
              duration: 4000,
              style: {
                background: '#363636',
                color: '#fff',
              },
            }}
          />
        </AuthProvider>
      </body>
    </html>
  )
}