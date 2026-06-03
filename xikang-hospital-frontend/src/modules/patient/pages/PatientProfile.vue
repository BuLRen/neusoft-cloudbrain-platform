<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import GlassCard from '@/shared/components/GlassCard.vue'
import StatusTag from '@/shared/components/StatusTag.vue'
import { useAuthStore } from '@/app/stores/auth'

const authStore = useAuthStore()

// 用户信息
const userInfo = reactive({
  name: '张三',
  phone: '138****6789',
  idCard: '330***********1234',
  gender: '男',
  birthDate: '1990-05-15',
  age: 36,
  bloodType: 'O型',
  emergencyContact: '李四（妻子）',
  emergencyPhone: '139****9876',
})

// 编辑状态
const isEditing = ref(false)
const editForm = reactive({ ...userInfo })

// 账号安全
const securityInfo = reactive({
  bindPhone: '138****6789',
  bindEmail: 'zhang***@example.com',
  lastLogin: '2026-05-29 08:30',
  loginDevice: 'Chrome / Windows',
})

// 就诊人管理
const familyMembers = ref([
  {
    id: 1,
    name: '张三（本人）',
    relation: '本人',
    idCard: '330***********1234',
    phone: '138****6789',
    isDefault: true,
  },
  {
    id: 2,
    name: '李四',
    relation: '妻子',
    idCard: '330***********5678',
    phone: '139****9876',
    isDefault: false,
  },
])

function startEdit() {
  Object.assign(editForm, userInfo)
  isEditing.value = true
}

function cancelEdit() {
  isEditing.value = false
}

function saveEdit() {
  Object.assign(userInfo, editForm)
  isEditing.value = false
  ElMessage.success('个人信息已更新')
}

function setDefaultMember(id: number) {
  familyMembers.value.forEach(m => {
    m.isDefault = m.id === id
  })
  ElMessage.success('已设为默认就诊人')
}

function removeMember(id: number) {
  const index = familyMembers.value.findIndex(m => m.id === id)
  if (index !== -1) {
    familyMembers.value.splice(index, 1)
    ElMessage.success('已删除就诊人')
  }
}

function logout() {
  authStore.logout()
  window.location.href = '/login'
}
</script>

<template>
  <div class="patient-profile">
    <!-- 个人信息 -->
    <GlassCard class="profile-card">
      <div class="card-header">
        <span class="card-icon">👤</span>
        <span>个人信息</span>
        <button v-if="!isEditing" class="btn-edit" @click="startEdit">
          编辑
        </button>
        <div v-else class="edit-actions">
          <button class="btn-cancel" @click="cancelEdit">取消</button>
          <button class="btn-save" @click="saveEdit">保存</button>
        </div>
      </div>

      <div class="profile-content">
        <div class="profile-avatar">
          <div class="avatar-circle">{{ userInfo.name.charAt(0) }}</div>
          <span class="avatar-name">{{ userInfo.name }}</span>
        </div>

        <div class="profile-fields">
          <template v-if="!isEditing">
            <div class="field-row">
              <div class="field-item">
                <label>姓名</label>
                <span>{{ userInfo.name }}</span>
              </div>
              <div class="field-item">
                <label>手机号</label>
                <span>{{ userInfo.phone }}</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>性别</label>
                <span>{{ userInfo.gender }}</span>
              </div>
              <div class="field-item">
                <label>年龄</label>
                <span>{{ userInfo.age }} 岁</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>出生日期</label>
                <span>{{ userInfo.birthDate }}</span>
              </div>
              <div class="field-item">
                <label>血型</label>
                <span>{{ userInfo.bloodType }}</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item full">
                <label>身份证号</label>
                <span>{{ userInfo.idCard }}</span>
              </div>
            </div>
            <div class="field-row">
              <div class="field-item full">
                <label>紧急联系人</label>
                <span>{{ userInfo.emergencyContact }} {{ userInfo.emergencyPhone }}</span>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="field-row">
              <div class="field-item">
                <label>姓名</label>
                <input v-model="editForm.name" class="field-input" />
              </div>
              <div class="field-item">
                <label>手机号</label>
                <input v-model="editForm.phone" class="field-input" />
              </div>
            </div>
            <div class="field-row">
              <div class="field-item">
                <label>性别</label>
                <input v-model="editForm.gender" class="field-input" />
              </div>
              <div class="field-item">
                <label>血型</label>
                <input v-model="editForm.bloodType" class="field-input" />
              </div>
            </div>
            <div class="field-row">
              <div class="field-item full">
                <label>紧急联系人</label>
                <input v-model="editForm.emergencyContact" class="field-input" />
              </div>
            </div>
          </template>
        </div>
      </div>
    </GlassCard>

    <!-- 就诊人管理 -->
    <GlassCard class="family-card">
      <div class="card-header">
        <span class="card-icon">👨‍👩‍👧</span>
        <span>就诊人管理</span>
        <button class="btn-add">+ 添加就诊人</button>
      </div>

      <div class="family-list">
        <div
          v-for="member in familyMembers"
          :key="member.id"
          class="family-item"
        >
          <div class="member-main">
            <span class="member-name">{{ member.name }}</span>
            <StatusTag v-if="member.isDefault" tone="primary">默认</StatusTag>
            <span class="member-relation">{{ member.relation }}</span>
          </div>
          <div class="member-info">
            <span>{{ member.idCard }}</span>
            <span>{{ member.phone }}</span>
          </div>
          <div class="member-actions">
            <button
              v-if="!member.isDefault"
              class="btn-link"
              @click="setDefaultMember(member.id)"
            >
              设为默认
            </button>
            <button
              v-if="!member.isDefault"
              class="btn-link danger"
              @click="removeMember(member.id)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 账号安全 -->
    <GlassCard class="security-card">
      <div class="card-header">
        <span class="card-icon">🔒</span>
        <span>账号安全</span>
      </div>

      <div class="security-list">
        <div class="security-item">
          <div class="security-main">
            <span class="security-title">手机号绑定</span>
            <span class="security-desc">{{ securityInfo.bindPhone }}</span>
          </div>
          <button class="btn-outline">更换</button>
        </div>
        <div class="security-item">
          <div class="security-main">
            <span class="security-title">邮箱绑定</span>
            <span class="security-desc">{{ securityInfo.bindEmail }}</span>
          </div>
          <button class="btn-outline">更换</button>
        </div>
        <div class="security-item">
          <div class="security-main">
            <span class="security-title">登录密码</span>
            <span class="security-desc">已设置</span>
          </div>
          <button class="btn-outline">修改</button>
        </div>
        <div class="security-item">
          <div class="security-main">
            <span class="security-title">最近登录</span>
            <span class="security-desc">
              {{ securityInfo.lastLogin }} · {{ securityInfo.loginDevice }}
            </span>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- 退出登录 -->
    <GlassCard class="logout-card">
      <button class="btn-logout" @click="logout">
        退出登录
      </button>
    </GlassCard>
  </div>
</template>

<style scoped>
.patient-profile {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  width: 80%;
  margin: 0 10%;
}

.profile-card,
.family-card,
.security-card,
.logout-card {
  padding: var(--space-5);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
  font-size: 16px;
  font-weight: 600;
}

.card-icon {
  font-size: 18px;
}

.edit-actions {
  margin-left: auto;
  display: flex;
  gap: var(--space-2);
}

.btn-edit,
.btn-cancel,
.btn-save,
.btn-add {
  margin-left: auto;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-edit,
.btn-cancel {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-muted);
}

.btn-edit:hover,
.btn-cancel:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.btn-save,
.btn-add {
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  color: white;
}

.btn-add:hover {
  opacity: 0.9;
}

/* 个人信息 */
.profile-content {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--space-6);
}

.profile-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.avatar-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 32px;
  font-weight: 600;
}

.avatar-name {
  font-weight: 600;
}

.profile-fields {
  display: grid;
  gap: var(--space-4);
}

.field-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-5);
}

.field-item {
  display: grid;
  gap: var(--space-1);
}

.field-item.full {
  grid-column: 1 / -1;
}

.field-item label {
  font-size: 12px;
  color: var(--color-text-muted);
}

.field-item span {
  font-size: 14px;
}

.field-input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  font-size: 14px;
}

.field-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

/* 就诊人 */
.family-list {
  display: grid;
  gap: var(--space-3);
}

.family-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.member-main {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.member-name {
  font-weight: 600;
}

.member-relation {
  color: var(--color-text-muted);
  font-size: 13px;
}

.member-info {
  flex: 1;
  display: flex;
  gap: var(--space-4);
  font-size: 13px;
  color: var(--color-text-muted);
}

.member-actions {
  display: flex;
  gap: var(--space-2);
}

.btn-link {
  padding: var(--space-1) var(--space-3);
  background: transparent;
  border: none;
  color: var(--color-primary);
  font-size: 13px;
  cursor: pointer;
}

.btn-link.danger {
  color: var(--color-danger);
}

.btn-link:hover {
  text-decoration: underline;
}

/* 账号安全 */
.security-list {
  display: grid;
  gap: var(--space-4);
}

.security-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--color-surface);
  border-radius: var(--radius-md);
}

.security-main {
  flex: 1;
  display: grid;
  gap: var(--space-1);
}

.security-title {
  font-weight: 600;
  font-size: 14px;
}

.security-desc {
  font-size: 13px;
  color: var(--color-text-muted);
}

/* 退出登录 */
.logout-card {
  text-align: center;
}

.btn-logout {
  padding: var(--space-3) var(--space-8);
  background: transparent;
  border: 1px solid var(--color-danger);
  border-radius: var(--radius-md);
  color: var(--color-danger);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-logout:hover {
  background: var(--color-danger);
  color: white;
}

.btn-outline {
  padding: var(--space-2) var(--space-4);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-standard);
}

.btn-outline:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>