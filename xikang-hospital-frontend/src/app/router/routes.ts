
import type { RouteRecordRaw } from 'vue-router'
import AppShell from '@/app/layouts/AppShell.vue'
import DashboardHome from '@/modules/dashboard/DashboardHome.vue'
import LoginPage from '@/modules/auth/LoginPage.vue'
import PhysicianQueuePage from '@/modules/physician/pages/PhysicianQueuePage.vue'
import PhysicianRecordPage from '@/modules/physician/pages/PhysicianRecordPage.vue'
import PhysicianOrdersPage from '@/modules/physician/pages/PhysicianOrdersPage.vue'
import PhysicianResultsPage from '@/modules/physician/pages/PhysicianResultsPage.vue'
import PhysicianDiagnosisPage from '@/modules/physician/pages/PhysicianDiagnosisPage.vue'
import PhysicianPrescriptionPage from '@/modules/physician/pages/PhysicianPrescriptionPage.vue'
import MedtechCheckQueuePage from '@/modules/medtech/pages/MedtechCheckQueuePage.vue'
import MedtechCheckStartPage from '@/modules/medtech/pages/MedtechCheckStartPage.vue'
import MedtechCheckResultPage from '@/modules/medtech/pages/MedtechCheckResultPage.vue'
import RouteGroupView from '@/shared/components/RouteGroupView.vue'
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
        component: RouteGroupView,
        redirect: '/physician/queue',
        meta: { title: '门诊诊疗', description: '接诊 → 病历 → 申请 → 结果 → 确诊 → 处方', icon: 'FirstAidKit', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending' },
        children: [
          {
            path: 'queue',
            name: 'PhysicianQueue',
            component: PhysicianQueuePage,
            meta: { title: '① 待诊接诊', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending', step: 1 },
          },
          {
            path: 'record',
            name: 'PhysicianRecord',
            component: PhysicianRecordPage,
            meta: { title: '② 病历与初步诊断', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending', step: 2 },
          },
          {
            path: 'orders',
            name: 'PhysicianOrders',
            component: PhysicianOrdersPage,
            meta: { title: '③ 开立检查检验', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending', step: 3 },
          },
          {
            path: 'results',
            name: 'PhysicianResults',
            component: PhysicianResultsPage,
            meta: { title: '④ 查看结果', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending', step: 4 },
          },
          {
            path: 'diagnosis',
            name: 'PhysicianDiagnosis',
            component: PhysicianDiagnosisPage,
            meta: { title: '⑤ 门诊确诊', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending', step: 5 },
          },
          {
            path: 'prescription',
            name: 'PhysicianPrescription',
            component: PhysicianPrescriptionPage,
            meta: { title: '⑥ 开立处方', roles: ['physician', 'admin'], requiresAuth: true, owner: 'A', group: 'attending', step: 6 },
          },
        ],
      },
      {
        path: 'medtech',
        name: 'Medtech',
        component: RouteGroupView,
        redirect: '/medtech/check-queue',
        meta: { title: '检查管理', description: '检查申请 → 开始检查 → 结果录入', icon: 'Operation', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
        children: [
          {
            path: 'check-queue',
            name: 'MedtechCheckQueue',
            component: MedtechCheckQueuePage,
            meta: { title: '① 检查申请', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 1 },
          },
          {
            path: 'check-start',
            name: 'MedtechCheckStart',
            component: MedtechCheckStartPage,
            meta: { title: '② 开始检查', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 2 },
          },
          {
            path: 'check-result',
            name: 'MedtechCheckResult',
            component: MedtechCheckResultPage,
            meta: { title: '③ 结果录入', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 3 },
          },
        ],
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
