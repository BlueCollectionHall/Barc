<script setup lang="ts">
import {onMounted, ref} from "vue";
import {
  AlertOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  ExclamationCircleOutlined,
  HeartOutlined,
  RedoOutlined,
  SearchOutlined,
} from "@ant-design/icons-vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage, successMessage} from "@/utils/MessageAlert.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";
import {useRouter} from "vue-router";
import {
  type ManageWorkFilterState,
  type OwnerWorkListSection,
  buildOwnerWorkListParams,
} from "@/utils/manageWorkEditHelpers.ts";
import {
  buildWorkAppealFeedback,
  getManageWorkSecondaryAction,
  resubmitRejectedWork,
  shouldShowWorkAppealEmailInput,
  updateOwnerWorkVisibility,
} from "@/utils/manageWorkStatusActions.ts";
const userPinia = useUserPinia();
const router = useRouter();

const {userArchive, userBasic} = storeToRefs(userPinia);
const workList = ref<Array<WorkImpl>>([]);
const menuStatus = ref<string>("PUBLIC");
const sectionLabel = (section: string): string => ({
  PUBLIC: "公开作品",
  PRIVATE: "私有作品",
  PENDING: "审核中",
  REJECTED: "审核未通过",
  OFF: "下架作品",
  BAN: "封禁作品",
}[section] || section);
const filterState = ref<ManageWorkFilterState>({type: "keyword", value: ""});
const actionPendingWorkId = ref<string | null>(null);
const appealOpen = ref<boolean>(false);
const appealSubmitting = ref<boolean>(false);
const appealWork = ref<WorkImpl | null>(null);
const appealContent = ref<string>("");
const appealEmail = ref<string>("");

const fetchWorkList = async (status: string) => {
  menuStatus.value = status;
  if (!userArchive.value?.uuid) return;
  try {
    const params = buildOwnerWorkListParams(status as OwnerWorkListSection, filterState.value);
    const response = await baseHttp("/api/work/works_by_me", {
      params,
      headers: {Authorization: window.localStorage.getItem("token") || ""},
    })
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      workList.value = data.data;
    } else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误");
  }
}

const searchCurrentStatus = async () => {
  // 搜索只作用于当前状态列表，避免切换公开/私有时误带旧状态。
  await fetchWorkList(menuStatus.value);
}

const resetSearch = async () => {
  filterState.value.value = "";
  await fetchWorkList(menuStatus.value);
}

const editWork = (workId: string) => {
  router.push({name: "ManageWorkEdit", query: {work_id: workId}});
}

const openAppeal = (work: WorkImpl) => {
  appealWork.value = work;
  appealContent.value = "";
  appealEmail.value = userBasic.value?.email || "";
  appealOpen.value = true;
}

const handleSecondaryAction = async (work: WorkImpl) => {
  const action = getManageWorkSecondaryAction(work.status);
  if (!action) return;
  if (action.kind === "appeal") {
    openAppeal(work);
    return;
  }

  const token: string | null = window.localStorage.getItem("token");
  if (!token || !action.targetStatus) {
    errorMessage("请先登录");
    return;
  }
  actionPendingWorkId.value = work.id;
  try {
    await updateOwnerWorkVisibility(work.id, action.targetStatus, token);
    successMessage(action.targetStatus === "PUBLIC" ? "作品已设为公开" : "作品已设为私有");
    await fetchWorkList(menuStatus.value);
  } catch (e) {
    errorMessage(e instanceof Error ? e.message : "状态更新失败");
  } finally {
    actionPendingWorkId.value = null;
  }
}

const handleReviewResubmit = async (work: WorkImpl) => {
  const token: string | null = window.localStorage.getItem("token");
  if (!token) {
    errorMessage("请先登录");
    return;
  }
  actionPendingWorkId.value = work.id;
  try {
    await resubmitRejectedWork(work.id, token);
    successMessage("已再次提交审核，请在“审核中”查看进度");
    await fetchWorkList(menuStatus.value);
  } catch (e) {
    errorMessage(e instanceof Error ? e.message : "再次提审失败");
  } finally {
    actionPendingWorkId.value = null;
  }
}

const submitAppeal = async () => {
  if (!appealWork.value) return;
  if (!appealContent.value.trim()) {
    errorMessage("请填写申诉说明");
    return;
  }
  appealSubmitting.value = true;
  try {
    const feedbackForm = buildWorkAppealFeedback({
      workId: appealWork.value.id,
      workTitle: appealWork.value.title,
      content: appealContent.value,
      author: userBasic.value?.uuid || userArchive.value?.uuid || null,
      email: appealEmail.value.trim() || userBasic.value?.email || null,
    });
    const response = await baseHttp.post("/feedback/upload", feedbackForm);
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      successMessage(data.data || "申诉已提交，等待管理员复核");
      appealOpen.value = false;
      appealWork.value = null;
      appealContent.value = "";
    } else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误");
  } finally {
    appealSubmitting.value = false;
  }
}

onMounted(async () => {
  const token: string | null = window.localStorage.getItem("token")
  if (token) {
    await userPinia.fetchUserInfo(token);
    await fetchWorkList(menuStatus.value);
  }

})

</script>

<template>
  <a-modal
    v-model:open="appealOpen"
    title="提交作品申诉"
    ok-text="提交申诉"
    cancel-text="先不提交"
    :confirm-loading="appealSubmitting"
    @ok="submitAppeal">
    <div class="appeal_modal_body">
      <div class="appeal_hint">
        <AlertOutlined />
        <span>这会作为作品申诉/投诉提交给管理员复核，不会直接恢复作品状态。</span>
      </div>
      <div class="appeal_work_title">作品：{{appealWork?.title || '未选择作品'}}</div>
      <a-input
        v-if="shouldShowWorkAppealEmailInput(userBasic)"
        v-model:value="appealEmail"
        type="email"
        placeholder="联系邮箱（选填）" />
      <a-textarea
        v-model:value="appealContent"
        placeholder="请说明申诉原因、已补充或修正的内容，以及希望管理员复核的重点～"
        :rows="5"
        :maxlength="600" />
    </div>
  </a-modal>
  <el-container class="manage_work_box">
    <el-aside class="side_box box">
      <el-menu :default-active="menuStatus" class="side_menu">
        <el-menu-item index="PUBLIC" class="side_menu_item" @click="fetchWorkList('PUBLIC')">
          <el-icon><EyeOutlined /></el-icon>
          <span>公开作品</span>
        </el-menu-item>
        <el-menu-item index="PRIVATE" class="side_menu_item" @click="fetchWorkList('PRIVATE')">
          <el-icon><EyeInvisibleOutlined /></el-icon>
          <span>私有作品</span>
        </el-menu-item>
        <el-menu-item index="PENDING" class="side_menu_item" @click="fetchWorkList('PENDING')">
          <el-icon><ClockCircleOutlined /></el-icon>
          <span>审核中</span>
        </el-menu-item>
        <el-menu-item index="REJECTED" class="side_menu_item" @click="fetchWorkList('REJECTED')">
          <el-icon><CloseCircleOutlined /></el-icon>
          <span>审核未通过</span>
        </el-menu-item>
        <el-menu-item index="OFF" class="side_menu_item" @click="fetchWorkList('OFF')">
          <el-icon><ExclamationCircleOutlined /></el-icon>
          <span>下架作品</span>
        </el-menu-item>
        <el-menu-item index="BAN" class="side_menu_item" @click="fetchWorkList('BAN')">
          <el-icon><AlertOutlined /></el-icon>
          <span>封禁作品</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="container box">
      <div class="search_bar">
        <div class="search_hint">在“{{sectionLabel(menuStatus)}}”里找作品</div>
        <el-select class="filter_select" v-model="filterState.type">
          <el-option label="关键词" value="keyword" />
          <el-option label="学园" value="school" />
          <el-option label="部团" value="club" />
          <el-option label="学生" value="student" />
        </el-select>
        <el-input
          class="input"
          v-model="filterState.value"
          placeholder="输入一点线索就好～"
          clearable
          @keyup.enter="searchCurrentStatus" />
        <el-button class="search_button" type="primary" @click="searchCurrentStatus"><SearchOutlined />搜索</el-button>
        <el-button class="reset_button" @click="resetSearch">清空</el-button>
      </div>
      <div class="work_item_box">
        <div class="work_item" v-for="item in workList" :key="item.id">
          <div class="cover_image_box">
            <img class="cover_image" :src="item.cover_image" alt="cover_image"/>
            <span
              v-if="item.review_status && item.review_status !== 'APPROVED'"
              class="review_badge"
              :class="item.review_status.toLowerCase()">
              {{item.review_status === 'PENDING' ? '审核中' : '审核未通过'}}
            </span>
          </div>
          <div class="info">
            <span class="title">{{item.title}}</span>
            <span class="updated_at">{{timestampToCn(item.updated_at)}}</span>
            <div v-if="item.review_status === 'REJECTED'" class="review_reason">
              拒绝原因：{{item.review_reason || '管理员未填写原因'}}
            </div>
            <div class="data_bar">
              <div class="view_count_box">
                <EyeOutlined />&nbsp;{{item.view_count}}
              </div>
              <div class="like_count_box">
                <HeartOutlined/>&nbsp;{{item.like_count}}
              </div>
            </div>
          </div>
          <div class="button_box">
            <el-button class="button" type="primary" @click="editWork(item.id)"><EditOutlined />编辑</el-button>
            <el-button
              v-if="item.review_status === 'REJECTED'"
              class="button"
              type="primary"
              :loading="actionPendingWorkId === item.id"
              @click="handleReviewResubmit(item)">
              <RedoOutlined />再次提审
            </el-button>
            <el-button
              v-if="getManageWorkSecondaryAction(item.status)"
              class="button secondary_action_button"
              :type="getManageWorkSecondaryAction(item.status)?.kind === 'appeal' ? 'warning' : ''"
              :loading="actionPendingWorkId === item.id"
              @click="handleSecondaryAction(item)">
              <EyeInvisibleOutlined v-if="getManageWorkSecondaryAction(item.status)?.targetStatus === 'PRIVATE'" />
              <EyeOutlined v-else-if="getManageWorkSecondaryAction(item.status)?.targetStatus === 'PUBLIC'" />
              <AlertOutlined v-else />
              {{getManageWorkSecondaryAction(item.status)?.label}}
            </el-button>
          </div>
        </div>
        <div class="empty_box" v-if="workList.length === 0">
          <img class="empty_icon" src="https://static.kivo.wiki/images/gallery/E1.%E5%AE%98%E6%96%B9%E8%A1%A8%E6%83%85%E5%8C%85/Default/cafabb328c6564d3445ebaa00e1c510f.gif" alt="empty" />
          <span>这个状态里暂时没有匹配作品</span>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<style scoped>
.manage_work_box {
  padding: 4rem 4rem;
  gap: 1rem;
}
.box {
  border-radius: .5rem;
  box-shadow: #9b9b9b 0 0 .5rem;
}

.side_menu {
  background-color: #ffffff;
  height: calc(100vh - 8rem - 70px);
}
.container {
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
  padding: 2rem 2rem;
  overflow: auto;
  max-height: calc(100vh - 8rem - 70px);
}
.search_bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  width: 90%;
  margin: 0 auto;
  gap: .6rem;
  padding: .8rem 1rem;
  border-radius: .8rem;
  background-color: #f8fcff;
  border: #d9f3ff 1px solid;
}
.search_hint {
  white-space: nowrap;
  color: #00AEEC;
  font-weight: bold;
}
.filter_select {
  width: 7rem;
}
.search_button, .reset_button {
  min-width: 5rem;
}
.work_item_box {
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.work_item {
  padding: 2rem;
  display: grid;
  grid-template-columns: 16rem minmax(0, 1fr) max-content;
  column-gap: 1rem;
  border-bottom: #d1d1d1 1px solid;
}
.work_item:last-child {
  border-bottom: none;
}
.cover_image_box {
  position: relative;
  overflow: hidden;
  width: calc(16 * 1rem);
  height: calc(9 * 1rem);

  .cover_image {
    width: 100%;
    border-radius: .5rem;
  }
}
.review_badge {
  position: absolute;
  top: .55rem;
  right: .55rem;
  padding: .28rem .7rem;
  border: 1px solid rgba(255, 255, 255, .72);
  border-radius: 999px;
  color: #fff;
  font-size: .78rem;
  font-weight: 700;
  box-shadow: 0 .3rem 1rem rgba(19, 46, 64, .18);
  backdrop-filter: blur(8px);
}
.review_badge.pending {
  background: rgba(0, 174, 236, .86);
}
.review_badge.rejected {
  background: rgba(254, 75, 123, .9);
}
.review_reason {
  max-width: 90%;
  padding: .55rem .75rem;
  border: 1px solid #ffd3de;
  border-radius: .6rem;
  color: #c73b62;
  background: #fff5f8;
  line-height: 1.45;
}
.info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  .title {
    font-size: 1.2rem;
  }
  .data_bar {
    display: flex;
    flex-direction: row;
    gap: 1rem;
  }
}
.button_box {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: flex-end;
  justify-self: end;
  gap: .75rem;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.button_box > .button {
  min-width: 6.5rem;
  height: 2rem;
  margin-left: 0;
  border-radius: var(--el-border-radius-base);
}
.empty_box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  color: #787878;
  padding: 3rem;
  .empty_icon {
    width: 8rem;
  }
}
.appeal_modal_body {
  display: flex;
  flex-direction: column;
  gap: .8rem;
}
.appeal_hint {
  display: flex;
  align-items: center;
  gap: .5rem;
  padding: .8rem 1rem;
  border-radius: .8rem;
  color: #fe4b7b;
  background-color: #fff5f8;
  border: #ffd3de 1px solid;
}
.appeal_work_title {
  color: #00AEEC;
  font-weight: bold;
}
</style>
