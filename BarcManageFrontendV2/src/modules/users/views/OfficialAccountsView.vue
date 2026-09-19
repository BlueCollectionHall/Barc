<script setup lang="ts">
/**
 * 官方账号（管理端「用户 → 官方账号」）
 *
 * 功能：
 * 1、列出全部官方业务项及其绑定账号；未配置项必须强提示，因为对应的写链路已被后端阻断；
 * 2、为业务绑定 / 更换官方账号，更换前二次确认并说明会被重新归属的未认领作品；
 * 3、不提供「解绑」：绑定一旦建立只能更换，避免业务被误置为未配置状态。
 */
import { ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import {
  bindOfficialAccount,
  fetchOfficialAccounts,
  searchOfficialAccountCandidates,
} from '@/modules/users/api/officialAccount.service'
import { getErrorMessage } from '@/shared/types/api'
import type { OfficialAccountCandidate, OfficialAccountItem } from '@/shared/types/officialAccount'
import { showError, showSuccess } from '@/shared/utils/message'

const loading = ref(false)
const saving = ref(false)
const items = ref<OfficialAccountItem[]>([])
const dialogVisible = ref(false)
const candidateLoading = ref(false)
const candidates = ref<OfficialAccountCandidate[]>([])

/** 当前正在编辑的业务（更换场景下复用同一表单，只是标题与确认文案不同） */
const editing = reactive({
  business_key: '',
  display_name: '',
  isReplace: false,
  bound_uuid: '',
  user_uuid: '',
  remark: '',
})

/** 未配置（含悬空绑定）的业务数量，用于页面顶部提示 */
const unconfiguredCount = computed(() => items.value.filter((item) => !item.bound).length)
const submitDisabled = computed(() => saving.value || !editing.user_uuid)
const dialogTitle = computed(() =>
  editing.isReplace
    ? `更换「${editing.display_name}」的官方账号`
    : `绑定「${editing.display_name}」的官方账号`,
)

async function loadItems(): Promise<void> {
  loading.value = true
  try {
    items.value = await fetchOfficialAccounts()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

/**
 * 打开绑定 / 更换弹窗。
 * 已绑定业务会把当前账号预置进候选项，否则 el-select 只能显示裸 UUID。
 */
function openDialog(item: OfficialAccountItem): void {
  editing.business_key = item.business_key
  editing.display_name = item.display_name
  editing.isReplace = item.bound
  editing.bound_uuid = item.user_uuid ?? ''
  editing.user_uuid = item.user_uuid ?? ''
  editing.remark = item.remark ?? ''
  candidates.value = item.bound && item.user_uuid
    ? [{
      uuid: item.user_uuid,
      nickname: item.nickname,
      username: item.username,
      avatar: item.avatar,
    }]
    : []
  dialogVisible.value = true
}

async function searchCandidates(keyword: string): Promise<void> {
  if (!keyword) {
    candidates.value = []
    return
  }
  candidateLoading.value = true
  try {
    candidates.value = await searchOfficialAccountCandidates(keyword)
  } catch (error) {
    candidates.value = []
    showError(getErrorMessage(error))
  } finally {
    candidateLoading.value = false
  }
}

/** 更换绑定前的二次确认：明确告知这是一个会批量改归属的动作 */
async function confirmReplace(): Promise<boolean> {
  if (!editing.isReplace || editing.user_uuid === editing.bound_uuid) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      '更换后，该业务此前写入的未认领作品的归属账号会一并改到新账号；已认领作品不受影响。是否继续？',
      '确认更换官方账号',
      {
        type: 'warning',
        confirmButtonText: '确认更换',
        cancelButtonText: '取消',
      },
    )
    return true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return false
    }
    showError(getErrorMessage(error))
    return false
  }
}

async function submitBind(): Promise<void> {
  if (submitDisabled.value) {
    return
  }
  if (!(await confirmReplace())) {
    return
  }

  saving.value = true
  try {
    const result = await bindOfficialAccount({
      business_key: editing.business_key,
      user_uuid: editing.user_uuid,
      remark: editing.remark || null,
    })
    const migrated = result?.migrated_works ?? 0
    showSuccess(
      migrated > 0
        ? `官方账号已更新，并重新归属了 ${migrated} 篇未认领作品`
        : '官方账号已更新',
    )
    dialogVisible.value = false
    await loadItems()
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    saving.value = false
  }
}

function closeDialog(): void {
  if (!saving.value) {
    dialogVisible.value = false
  }
}

onMounted(loadItems)
</script>

<template>
  <RoutePageShell title="官方账号" eyebrow="Official Accounts">
    <template #actions>
      <span class="header-meta">
        未配置业务：{{ unconfiguredCount }} 项
      </span>
    </template>

    <section class="glass-panel page-card">
      <p class="intro">
        为官方业务指定平台内使用的账号。例如「未认领收录作品临时归属」决定了搬运收录上传与管理员撤销认领时，
        作品 <code>author</code> 字段临时指向哪个账号。
      </p>
      <p class="intro intro--warning">
        未配置（或绑定账号已被删除）时，对应业务的写入会被后端直接阻断，不会写入临时 UUID。
        绑定只能更换，不能解绑。
      </p>
    </section>

    <section class="glass-panel page-card page-card--table">
      <el-table v-loading="loading" :data="items" row-key="business_key">
        <el-table-column label="业务" min-width="200">
          <template #default="scope">
            <div class="business">
              <span class="business__name">{{ scope.row.display_name }}</span>
              <span class="business__key">{{ scope.row.business_key }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="当前绑定账号" min-width="260">
          <template #default="scope">
            <div v-if="scope.row.bound" class="account">
              <el-avatar :size="28" :src="scope.row.avatar || undefined" />
              <span class="account__nickname">{{ scope.row.nickname }}</span>
              <span class="account__username">@{{ scope.row.username }}</span>
            </div>
            <el-tag v-else-if="scope.row.dangling" type="danger">
              绑定账号已不存在（{{ scope.row.user_uuid }}），请重新绑定
            </el-tag>
            <el-tag v-else type="danger">未配置 —— 该业务写入已被阻断</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="说明" min-width="240">
          <template #default="scope">
            <span class="description">{{ scope.row.description }}</span>
          </template>
        </el-table-column>

        <el-table-column label="最后更新" min-width="180">
          <template #default="scope">
            <span v-if="scope.row.updated_at">
              {{ scope.row.updated_at }}
              <span v-if="scope.row.updated_by_nickname" class="operator">
                · {{ scope.row.updated_by_nickname }}
              </span>
            </span>
            <span v-else class="operator">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openDialog(scope.row)">
              {{ scope.row.bound ? '更换' : '绑定' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" width="520px" align-center :title="dialogTitle">
      <el-form label-position="top" @submit.prevent="submitBind">
        <el-form-item label="官方账号">
          <el-select
            v-model="editing.user_uuid"
            placeholder="搜索用户 UUID / 用户名 / 昵称"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="searchCandidates"
            :loading="candidateLoading"
            style="width: 100%"
          >
            <el-option
              v-for="candidate in candidates"
              :key="candidate.uuid"
              :label="candidate.nickname || candidate.uuid"
              :value="candidate.uuid"
            >
              <div class="candidate">
                <el-avatar :size="24" :src="candidate.avatar || undefined" />
                <span>{{ candidate.nickname }}</span>
                <span class="candidate__username">@{{ candidate.username }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="备注（可选）">
          <el-input v-model="editing.remark" maxlength="255" show-word-limit />
        </el-form-item>

        <p class="dialog-tip">
          一个账号只能绑定一个业务；更换后，该业务此前的未认领作品会一并改到新账号名下。
        </p>

        <div class="dialog-actions">
          <el-button :disabled="saving" @click="closeDialog">取消</el-button>
          <el-button
            type="primary"
            native-type="submit"
            :loading="saving"
            :disabled="submitDisabled"
          >
            确认
          </el-button>
        </div>
      </el-form>
    </el-dialog>
  </RoutePageShell>
</template>

<style scoped>
.page-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.85rem 1.25rem;
}

.page-card--table {
  min-height: 320px;
}

.intro {
  margin: 0;
  color: var(--barc-text-soft);
  line-height: 1.7;
}

.intro--warning {
  color: var(--barc-accent-strong);
}

.business {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.business__name {
  font-weight: 600;
}

.business__key,
.operator {
  color: var(--barc-text-soft);
  font-size: 0.78rem;
}

.account {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.account__nickname {
  font-weight: 600;
}

.account__username,
.candidate__username {
  color: var(--barc-text-soft);
  font-size: 0.8rem;
}

.description {
  color: var(--barc-text-soft);
  line-height: 1.6;
}

.candidate {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.dialog-tip {
  margin: 0 0 var(--barc-space-4);
  color: var(--barc-text-soft);
  line-height: 1.7;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}
</style>
