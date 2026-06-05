
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElButton, ElIcon, ElMenu, ElMenuItem, ElRadioButton, ElRadioGroup, ElMessage, ElForm, ElFormItem, ElInput, ElDropdown, ElDropdownMenu, ElDropdownItem, ElDialog, ElSelect, ElOption } from 'element-plus'
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
app.use(ElMessage)
app.use(ElForm)
app.use(ElFormItem)
app.use(ElInput)
app.use(ElDropdown)
app.use(ElDropdownMenu)
app.use(ElDropdownItem)
app.use(ElDialog)
app.use(ElSelect)
app.use(ElOption)

app.mount('#app')
