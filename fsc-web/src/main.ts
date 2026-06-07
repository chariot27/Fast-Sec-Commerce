import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Keycloak from 'keycloak-js'
import './style.css'
import App from './App.vue'

// Configuração do Keycloak
const keycloak = new Keycloak({
  url: 'http://localhost:8080',
  realm: 'fsc',
  clientId: 'fsc-vue'
});

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// Inicializa o Keycloak antes de montar o Vue
keycloak.init({ onLoad: 'login-required', checkLoginIframe: false })
  .then((authenticated) => {
    if (authenticated) {
      console.log('Autenticado com Keycloak!');
      // Guarda a instância e token no escopo global ou store do pinia
      app.provide('keycloak', keycloak);
      
      // Atualizar token periodicamente
      setInterval(() => {
        keycloak.updateToken(70).then((refreshed) => {
          if (refreshed) {
            console.log('Token atualizado');
          }
        }).catch(() => {
          console.error('Falha ao atualizar token');
        });
      }, 60000);

      app.mount('#app');
    } else {
      window.location.reload();
    }
  })
  .catch((err) => {
    console.error("Erro ao inicializar Keycloak", err);
  });
