
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElButton, ElIcon, ElMenu, ElMenuItem, ElRadioButton, ElRadioGroup } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './app/router'
import './app/styles/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElButton)
app.use(ElIcon)
app.use(ElMenu)
app.use(ElMenuItem)
app.use(ElRadioGroup)
app.use(ElRadioButton)

app.mount('#app')
