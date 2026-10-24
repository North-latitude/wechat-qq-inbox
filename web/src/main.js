import { createApp } from 'vue'
import App from './App.vue'
import './styles.css'

createApp(App).mount('#app')

if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Offline shell is optional; the inbox keeps working online.
    })
  })
}
