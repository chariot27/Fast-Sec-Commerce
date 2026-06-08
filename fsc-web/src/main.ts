import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Keycloak from 'keycloak-js'
import './style.css'
import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

// Web Crypto API requer contexto seguro (https:// ou localhost).
// Em acesso via IP local em desenvolvimento, monta sem autenticacao.
const isSecureCtx = window.isSecureContext ||
  window.location.hostname === 'localhost' ||
  window.location.hostname === '127.0.0.1'

function mountApp(kc: Keycloak | null) {
  app.provide('keycloak', kc)
  app.mount('#app')
}

if (!isSecureCtx) {
  console.warn('[FSC] Contexto nao seguro - Keycloak desativado (modo dev)')
  mountApp(null)
} else {
  const keycloak = new Keycloak({
    url: 'http://localhost:8080',
    realm: 'fsc',
    clientId: 'fsc-vue'
  })

  keycloak.init({ onLoad: 'login-required', checkLoginIframe: false })
    .then((authenticated) => {
      if (authenticated) {
        setInterval(() => {
          keycloak.updateToken(70).catch(() =>
            console.error('Falha ao atualizar token')
          )
        }, 60000)
        mountApp(keycloak)
      } else {
        window.location.reload()
      }
    })
    .catch((err) => {
      // Keycloak offline — monta sem auth para nao deixar tela preta
      console.error('[FSC] Keycloak indisponivel, montando sem auth:', err)
      mountApp(null)
    })
}
