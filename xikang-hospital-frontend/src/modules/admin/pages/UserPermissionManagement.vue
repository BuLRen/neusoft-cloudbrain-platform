<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElMessage,
} from 'element-plus'
import PageHeader from '@/shared/components/PageHeader.vue'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { adminUsers, permissionScopes } from '@/shared/mock/admin'
import { roleOptions } from '@/shared/types/role'
import type { AdminUserRecord } from '@/shared/types/admin'

const keyword = ref('')
const selectedRole = ref('')
const permissionDialogVisible = ref(false)
const selectedUser = ref<AdminUserRecord | null>(null)
const users = ref(adminUsers.map((item) => ({ ...item })))

const filteredUsers = computed(() => users.value.filter((item) => {
  const matchesKeyword = !keyword.value || [item.username, item.realName, item.department].some((field) => field.toLowerCase().includes(keyword.value.toLowerCase()))
  const matchesRole = !selectedRole.value || item.role === selectedRole.value
  return matchesKeyword && matchesRole
}))

const currentPermissions = computed(() => {
  if (!selectedUser.value) return []
  return permissionScopes[selectedUser.value.role] || [
    { label: '基础工作台访问', enabled: true },
    { label: '高级治理能力', enabled: false },
  ]
})

function statusTone(status: AdminUserRecord['status']) {
  if (status === 'enabled') return 'success'
  if (status === 'locked') return 'danger'
  return 'warning'
}

function statusLabel(status: AdminUserRecord['status']) {
  if (status === 'enabled') return '启用中'
  if (status === 'locked') return '已锁定'
  return '已停用'
}

function openPermissions(user: AdminUserRecord) {
  selectedUser.value = user
  permissionDialogVisible.value = true
}

function toggleStatus(user: AdminUserRecord) {
  user.status = user.status === 'enabled' ? 'disabled' : 'enabled'
  ElMessage.success(user.status === 'enabled' ? '账号已启用' : '账号已停用')
}

function unlockUser(user: AdminUserRecord) {
  user.status = 'enabled'
  ElMessage.success('账号已解锁')
}
</script>

<template>
  <div class="user-permission-page u-page-grid">
    <PageHeader
      title="用户权限管理"
      description="统一查看账号、角色、科室归属和权限范围，补齐管理员侧的账户治理能力。"
      eyebrow="Role Admin / IAM"
    />

    <GlassCard class="panel">
      <div class="toolbar">
        <ElInput v-model="keyword" placeholder="搜索用户名、姓名或科室" class="field field--keyword" clearable />
        <ElSelect v-model="selectedRole" placeholder="全部角色" clearable class="field field--role">
          <ElOption v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
        </ElSelect>
        <StatusTag tone="primary">{{ filteredUsers.length }} 个账号</StatusTag>
      </div>

      <ElTable :data="filteredUsers">
        <ElTableColumn prop="username" label="用户名" min-width="140" />
        <ElTableColumn prop="realName" label="姓名" min-width="120" />
        <ElTableColumn prop="role" label="角色" min-width="120" />
        <ElTableColumn prop="department" label="所属科室" min-width="140" />
        <ElTableColumn label="账号状态" min-width="110">
          <template #default="{ row }">
            <StatusTag :tone="statusTone(row.status)">{{ statusLabel(row.status) }}</StatusTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="lastLoginAt" label="最近登录" min-width="160" />
        <ElTableColumn label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openPermissions(row)">查看权限</ElButton>
            <ElButton v-if="row.status === 'locked'" link type="success" @click="unlockUser(row)">解锁</ElButton>
            <ElButton v-else link :type="row.status === 'enabled' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'enabled' ? '停用' : '启用' }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </GlassCard>

    <ElDialog v-model="permissionDialogVisible" title="权限详情" width="680px">
      <template v-if="selectedUser">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="用户名">{{ selectedUser.username }}</ElDescriptionsItem>
          <ElDescriptionsItem label="姓名">{{ selectedUser.realName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="角色">{{ selectedUser.role }}</ElDescriptionsItem>
          <ElDescriptionsItem label="科室">{{ selectedUser.department }}</ElDescriptionsItem>
          <ElDescriptionsItem label="账号状态">{{ statusLabel(selectedUser.status) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="最近登录">{{ selectedUser.lastLoginAt }}</ElDescriptionsItem>
        </ElDescriptions>

        <div class="permission-section">
          <div class="section-header">
            <h3>权限范围</h3>
            <StatusTag tone="ai">后端接口可后补</StatusTag>
          </div>
          <div class="permission-list">
            <div v-for="item in currentPermissions" :key="item.label" class="permission-item">
              <span>{{ item.label }}</span>
              <StatusTag :tone="item.enabled ? 'success' : 'warning'">{{ item.enabled ? '已开放' : '未开放' }}</StatusTag>
            </div>
          </div>
        </div>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.panel {
  padding: var(--space-5);
}

.toolbar,
.section-header,
.permission-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.toolbar {
  margin-block-end: var(--space-4);
  flex-wrap: wrap;
}

.field--keyword {
  width: min(320px, 100%);
}

.field--role {
  width: 180px;
}

.permission-section {
  margin-block-start: var(--space-5);
}

.permission-list {
  display: grid;
  gap: var(--space-3);
  margin-block-start: var(--space-4);
}

.permission-item {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
}
</style>
