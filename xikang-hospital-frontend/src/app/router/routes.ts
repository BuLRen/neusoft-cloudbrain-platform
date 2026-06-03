
import type { RouteRecordRaw } from 'vue-router'
import AppShell from '@/app/layouts/AppShell.vue'
import DashboardHome from '@/modules/dashboard/DashboardHome.vue'
import LoginPage from '@/modules/auth/LoginPage.vue'
import PatientLayout from '@/modules/patient/layouts/PatientLayout.vue'
import PatientOverview from '@/modules/patient/pages/PatientOverview.vue'
import PatientTriage from '@/modules/patient/pages/PatientTriage.vue'
import PatientRegistration from '@/modules/patient/pages/PatientRegistration.vue'
import PatientPrevisit from '@/modules/patient/pages/PatientPrevisit.vue'
import PatientRecords from '@/modules/patient/pages/PatientRecords.vue'
import PatientFollowup from '@/modules/patient/pages/PatientFollowup.vue'
import PatientProfile from '@/modules/patient/pages/PatientProfile.vue'
import PatientPrescription from '@/modules/patient/pages/PatientPrescription.vue'
import RegistrationWorkspace from '@/modules/registration/RegistrationWorkspace.vue'
import PhysicianWorkspace from '@/modules/physician/PhysicianWorkspace.vue'
import MedicalTechWorkspace from '@/modules/medical-tech/MedicalTechWorkspace.vue'
import PharmacyWorkspace from '@/modules/pharmacy/PharmacyWorkspace.vue'
import AdminWorkspace from '@/modules/admin/AdminWorkspace.vue'
import RoutePlaceholder from '@/shared/components/RoutePlaceholder.vue'
import ForbiddenPage from '@/modules/error/ForbiddenPage.vue'
import NotFoundPage from '@/modules/error/NotFoundPage.vue'

// 患者端独立布局路由（不经过 AppShell）
const patientRoutes: RouteRecordRaw[] = [
  {
    path: '/patient',
    component: PatientLayout,
    redirect: '/patient/overview',
    meta: { title: '患者端', description: 'AI 导诊、预问诊、用药随访入口', icon: 'User', roles: ['patient', 'admin'], requiresAuth: true },
    children: [
      {
        path: 'overview',
        name: 'PatientOverview',
        component: PatientOverview,
        meta: { title: '患者首页', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'triage',
        name: 'PatientTriage',
        component: PatientTriage,
        meta: { title: 'AI 导诊', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'registration',
        name: 'PatientRegistration',
        component: PatientRegistration,
        meta: { title: '我的挂号', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'previsit',
        name: 'PatientPrevisit',
        component: PatientPrevisit,
        meta: { title: 'AI 预问诊', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'records',
        name: 'PatientRecords',
        component: PatientRecords,
        meta: { title: '就诊记录', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'followup',
        name: 'PatientFollowup',
        component: PatientFollowup,
        meta: { title: '随访管理', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'prescription',
        name: 'PatientPrescription',
        component: PatientPrescription,
        meta: { title: '我的处方', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'profile',
        name: 'PatientProfile',
        component: PatientProfile,
        meta: { title: '个人中心', requiresAuth: true, roles: ['patient', 'admin'] },
      },
    ],
  },
]

const placeholder = RoutePlaceholder

export const routes: RouteRecordRaw[] = [
  ...patientRoutes,
  {
    path: '/login',
    name: 'Login',
    component: LoginPage,
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: AppShell,
    redirect: () => {
      // 动态重定向，根据是否有 token 决定目标页面
      // 未登录 -> /login
      // 已登录 -> /dashboard（完整逻辑在 guard.ts 中处理患者角色）
      const token = localStorage.getItem('access_token')
      if (!token) {
        return '/login'
      }
      return '/dashboard'
    },
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: DashboardHome,
        meta: { title: '仪表盘', icon: 'DataBoard', requiresAuth: true, owner: '共同' },
      },
      {
        path: 'registration',
        name: 'Registration',
        component: RegistrationWorkspace,
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
        component: MedicalTechWorkspace,
        meta: { title: '医技执行', description: '检查、检验、处置执行和结果录入', icon: 'Operation', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'pharmacy',
        name: 'Pharmacy',
        component: PharmacyWorkspace,
        meta: { title: '药房管理', description: '发药、退药、药库、交易记录', icon: 'Box', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B' },
      },
      {
        path: 'admin',
        name: 'Admin',
        component: AdminWorkspace,
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
