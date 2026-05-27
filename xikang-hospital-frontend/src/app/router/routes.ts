
import type { RouteRecordRaw } from 'vue-router'
import AppShell from '@/app/layouts/AppShell.vue'
import DashboardHome from '@/modules/dashboard/DashboardHome.vue'
import LoginPage from '@/modules/auth/LoginPage.vue'
import PhysicianWorkspace from '@/modules/physician/PhysicianWorkspace.vue'
import RoutePlaceholder from '@/shared/components/RoutePlaceholder.vue'
import ForbiddenPage from '@/modules/error/ForbiddenPage.vue'
import NotFoundPage from '@/modules/error/NotFoundPage.vue'

const placeholder = RoutePlaceholder

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: LoginPage,
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: AppShell,
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: DashboardHome,
        meta: { title: '仪表盘', icon: 'DataBoard', requiresAuth: true, owner: '共同' },
      },
      {
        path: 'patient',
        name: 'Patient',
        component: placeholder,
        meta: { title: '患者端', description: 'AI 导诊、预问诊、用药随访入口', icon: 'User', roles: ['patient', 'admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'registration',
        name: 'Registration',
        component: placeholder,
        meta: { title: '挂号收费', description: '窗口挂号、收费、退费、费用记录', icon: 'Tickets', roles: ['registration', 'admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'physician',
        name: 'Physician',
        component: PhysicianWorkspace,
        meta: { title: '医生工作站', description: '患者查看、病历、检查申请、确诊、处方', icon: 'FirstAidKit', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A' },
      },
      {
        path: 'medical-tech',
        name: 'MedicalTech',
        component: placeholder,
        meta: { title: '医技执行', description: '检查、检验、处置执行和结果录入', icon: 'Operation', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'pharmacy',
        name: 'Pharmacy',
        component: placeholder,
        meta: { title: '药房管理', description: '发药、退药、药库、交易记录', icon: 'Box', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'admin',
        name: 'Admin',
        component: placeholder,
        meta: { title: '管理员支撑', description: '医生排班、AI 分诊台、基础数据', icon: 'Setting', roles: ['admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'ai',
        name: 'AiComponents',
        component: placeholder,
        meta: { title: 'AI 组件区', description: 'AI 结果卡片和嵌入组件预留区', icon: 'MagicStick', roles: ['admin', 'physician', 'registration', 'medtech', 'pharmacy', 'patient'], requiresAuth: true, owner: '共同' },
      },
      {
        path: '403',
        name: 'Forbidden',
        component: ForbiddenPage,
        meta: { title: '无访问权限', hidden: true, requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFoundPage,
    meta: { title: '页面不存在', hidden: true },
  },
]
