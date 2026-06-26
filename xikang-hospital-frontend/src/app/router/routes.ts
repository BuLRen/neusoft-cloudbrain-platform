
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
import PatientVisitRecord from '@/modules/patient/pages/PatientVisitRecord.vue'
import PatientClinicalProfile from '@/modules/patient/pages/PatientClinicalProfile.vue'
import PatientFollowup from '@/modules/patient/pages/PatientFollowup.vue'
import PatientProfile from '@/modules/patient/pages/PatientProfile.vue'
import PatientPrescription from '@/modules/patient/pages/PatientPrescription.vue'
import PatientDepartmentDetail from '@/modules/patient/pages/PatientDepartmentDetail.vue'
import RegistrationWorkspace from '@/modules/registration/RegistrationWorkspace.vue'
import PharmacyDispensingPage from '@/modules/pharmacy/pages/DispensingPage.vue'
import PharmacyInventoryPage from '@/modules/pharmacy/pages/InventoryPage.vue'
import PharmacyBatchInboundPage from '@/modules/pharmacy/pages/BatchInboundPage.vue'
import PharmacyTransactionsPage from '@/modules/pharmacy/pages/TransactionsPage.vue'
import PharmacyFollowUpPage from '@/modules/pharmacy/pages/FollowUpPage.vue'
import PharmacyDrugDictionaryPage from '@/modules/pharmacy/pages/DrugDictionaryPage.vue'
import PharmacyPrescriptionQueryPage from '@/modules/pharmacy/pages/PrescriptionQueryPage.vue'
import PharmacyStatisticsPage from '@/modules/pharmacy/pages/StatisticsPage.vue'
import AdminWorkspace from '@/modules/admin/AdminWorkspace.vue'
import ScheduleManagement from '@/modules/admin/pages/ScheduleManagement.vue'
import MasterDataManagement from '@/modules/admin/pages/MasterDataManagement.vue'
import UserPermissionManagement from '@/modules/admin/pages/UserPermissionManagement.vue'
import PersonnelManagement from '@/modules/admin/pages/PersonnelManagement.vue'
import MedtechItemsManagement from '@/modules/admin/pages/MedtechItemsManagement.vue'
import OperationsCenter from '@/modules/admin/pages/OperationsCenter.vue'
import PhysicianQueuePage from '@/modules/physician/pages/PhysicianQueuePage.vue'
import PhysicianRecordPage from '@/modules/physician/pages/PhysicianRecordPage.vue'
import PhysicianOrdersPage from '@/modules/physician/pages/PhysicianOrdersPage.vue'
import PhysicianResultsPage from '@/modules/physician/pages/PhysicianResultsPage.vue'
import PhysicianDiagnosisPage from '@/modules/physician/pages/PhysicianDiagnosisPage.vue'
import PhysicianPrescriptionPage from '@/modules/physician/pages/PhysicianPrescriptionPage.vue'
import MedtechCheckQueuePage from '@/modules/medtech/pages/MedtechCheckQueuePage.vue'
import MedtechCheckStartPage from '@/modules/medtech/pages/MedtechCheckStartPage.vue'
import MedtechCheckResultPage from '@/modules/medtech/pages/MedtechCheckResultPage.vue'
import MedtechInspectionStartPage from '@/modules/medtech/pages/MedtechInspectionStartPage.vue'
import MedtechDisposalStartPage from '@/modules/medtech/pages/MedtechDisposalStartPage.vue'
import RouteGroupView from '@/shared/components/RouteGroupView.vue'
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
        path: 'departments/:departmentId',
        name: 'PatientDepartmentDetail',
        component: PatientDepartmentDetail,
        meta: { title: '科室详情', requiresAuth: true, roles: ['patient', 'admin'] },
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
        path: 'records/:registerId',
        name: 'PatientVisitRecord',
        component: PatientVisitRecord,
        meta: { title: '就诊病历详情', requiresAuth: true, roles: ['patient', 'admin'] },
      },
      {
        path: 'clinical-profile',
        name: 'PatientClinicalProfile',
        component: PatientClinicalProfile,
        meta: { title: '长期健康档案', requiresAuth: true, roles: ['patient', 'admin'] },
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
        meta: { title: '挂号收费', description: '窗口挂号、收费、退费、费用记录', icon: 'Tickets', roles: ['registration'], requiresAuth: true, owner: 'B' },
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
        meta: { title: '医技管理', description: '医技申请 → 检查/检验/处置执行', icon: 'Operation', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
        children: [
          {
            path: 'check-queue',
            name: 'MedtechCheckQueue',
            component: MedtechCheckQueuePage,
            meta: { title: '① 医技申请', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 1 },
          },
          {
            path: 'follow-up',
            name: 'MedtechFollowUp',
            component: RouteGroupView,
            redirect: '/medtech/follow-up/dashboard',
            meta: { title: '② 随访系统', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 2 },
            children: [
              {
                path: 'dashboard',
                name: 'MedtechFollowUpDashboard',
                component: RoutePlaceholder,
                meta: { title: '随访工作台', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
              },
              {
                path: 'outcome',
                name: 'MedtechFollowUpOutcome',
                component: RoutePlaceholder,
                meta: { title: '疗效评估', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
              },
              {
                path: 'communication',
                name: 'MedtechFollowUpCommunication',
                component: RoutePlaceholder,
                meta: { title: '医患沟通', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
              },
              {
                path: 'plans',
                name: 'MedtechFollowUpPlans',
                component: RoutePlaceholder,
                meta: { title: '随访计划', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
              },
              {
                path: 'records',
                name: 'MedtechFollowUpRecords',
                component: RoutePlaceholder,
                meta: { title: '随访记录', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam' },
              },
            ],
          },
          {
            path: 'check-start',
            name: 'MedtechCheckStart',
            component: MedtechCheckStartPage,
            meta: { title: '② 检查执行', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 2, hidden: true },
          },
          {
            path: 'inspection-start',
            name: 'MedtechInspectionStart',
            component: MedtechInspectionStartPage,
            meta: { title: '② 检验执行', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 2, hidden: true },
          },
          {
            path: 'disposal-start',
            name: 'MedtechDisposalStart',
            component: MedtechDisposalStartPage,
            meta: { title: '② 处置执行', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', step: 2, hidden: true },
          },
          {
            path: 'check-result',
            name: 'MedtechCheckResult',
            component: MedtechCheckResultPage,
            meta: { title: '结果录入', roles: ['medtech', 'admin'], requiresAuth: true, owner: 'B', group: 'exam', hidden: true },
          },
        ],
      },
      {
        path: 'pharmacy',
        name: 'Pharmacy',
        component: RouteGroupView,
        redirect: '/pharmacy/dispensing',
        meta: { title: '药房管理', description: '发药、退药、药库、交易记录', icon: 'Box', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B' },
        children: [
          {
            path: 'dispensing',
            name: 'PharmacyDispensing',
            component: PharmacyDispensingPage,
            meta: { title: '① 发药工作台', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 1 },
          },
          {
            path: 'inventory',
            name: 'PharmacyInventory',
            component: PharmacyInventoryPage,
            meta: { title: '② 药库与库存', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 2 },
          },
          {
            path: 'batch-inbound',
            name: 'PharmacyBatchInbound',
            component: PharmacyBatchInboundPage,
            meta: { title: '②·批量入库', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 2, hidden: true },
          },
          {
            path: 'transactions',
            name: 'PharmacyTransactions',
            component: PharmacyTransactionsPage,
            meta: { title: '③ 出入库流水', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 3 },
          },
          {
            path: 'follow-up',
            name: 'PharmacyFollowUp',
            component: PharmacyFollowUpPage,
            meta: { title: '④ 患者随访', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 4 },
          },
          {
            path: 'drug-dictionary',
            name: 'PharmacyDrugDictionary',
            component: PharmacyDrugDictionaryPage,
            meta: { title: '⑤ 药品字典', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 5 },
          },
          {
            path: 'prescription-query',
            name: 'PharmacyPrescriptionQuery',
            component: PharmacyPrescriptionQueryPage,
            meta: { title: '⑥ 处方追溯', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 6 },
          },
          {
            path: 'statistics',
            name: 'PharmacyStatistics',
            component: PharmacyStatisticsPage,
            meta: { title: '⑦ 消耗统计', roles: ['pharmacy', 'admin'], requiresAuth: true, owner: 'B', step: 7 },
          },
        ],
      },
      {
        path: 'admin',
        name: 'Admin',
        component: RouteGroupView,
        redirect: '/admin/triage',
        meta: { title: '管理员', description: '分诊、排班、医生与基础数据治理', icon: 'Setting', roles: ['admin'], requiresAuth: true, owner: 'B' },
        children: [
          {
            path: 'triage',
            name: 'AdminTriage',
            component: AdminWorkspace,
            meta: { title: 'AI 分诊台', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'schedule',
            name: 'ScheduleManagement',
            component: ScheduleManagement,
            meta: { title: '智能排班', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'personnel',
            name: 'PersonnelManagement',
            component: PersonnelManagement,
            meta: { title: '人员管理', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'master-data',
            name: 'MasterDataManagement',
            component: MasterDataManagement,
            meta: { title: '基础资料', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'medtech-items',
            name: 'MedtechItemsManagement',
            component: MedtechItemsManagement,
            meta: { title: '医技项目', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'operations',
            name: 'OperationsCenter',
            component: OperationsCenter,
            meta: { title: '运营中心', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'users',
            name: 'UserPermissionManagement',
            component: UserPermissionManagement,
            meta: { title: '用户权限', roles: ['admin'], requiresAuth: true, owner: 'B' },
          },
          {
            path: 'physicians',
            redirect: { path: '/admin/personnel', query: { tab: 'physicians' } },
            meta: { hidden: true, roles: ['admin'], requiresAuth: true },
          },
          {
            path: 'medtech-employees',
            redirect: { path: '/admin/personnel', query: { tab: 'medtech' } },
            meta: { hidden: true, roles: ['admin'], requiresAuth: true },
          },
          {
            path: 'check-equipment',
            redirect: { path: '/admin/medtech-items', query: { tab: 'catalog' } },
            meta: { hidden: true, roles: ['admin'], requiresAuth: true },
          },
          {
            path: 'result-form',
            redirect: (to) => ({
              path: '/admin/medtech-items',
              query: {
                tab: 'result-form',
                ...(to.query.techId ? { techId: to.query.techId } : {}),
              },
            }),
            meta: { hidden: true, roles: ['admin'], requiresAuth: true },
          },
          {
            path: 'monitoring',
            redirect: { path: '/admin/operations', query: { tab: 'monitoring' } },
            meta: { hidden: true, roles: ['admin'], requiresAuth: true },
          },
          {
            path: 'reports',
            redirect: { path: '/admin/operations', query: { tab: 'reports' } },
            meta: { hidden: true, roles: ['admin'], requiresAuth: true },
          },
        ],
      },
      {
        path: 'schedule',
        redirect: '/admin/schedule',
        meta: { hidden: true, roles: ['admin'], requiresAuth: true },
      },
      {
        path: 'ai',
        name: 'AiComponents',
        component: placeholder,
        meta: { title: 'AI 组件区', description: 'AI 结果卡片和嵌入组件预留区', icon: 'MagicStick', roles: ['physician', 'registration', 'medtech', 'pharmacy', 'patient'], requiresAuth: true, owner: '共同' },
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
