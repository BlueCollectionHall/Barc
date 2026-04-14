<script setup lang="ts">
import {type LocationQuery, useRoute} from "vue-router";
import {onMounted, ref, watch} from "vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {errorMessage, infoMessage, successMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";
import {
  ClockCircleOutlined,
  SyncOutlined,
  UserSwitchOutlined,
  UserOutlined,
  UserAddOutlined,
  EyeOutlined,
  HeartOutlined,
  AlertOutlined,
  FlagOutlined,
} from "@ant-design/icons-vue";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import type {FeedBackImpl} from "@/interfaces/FeedbackImpl.ts";

const route = useRoute();
const userPinia = useUserPinia();

interface viewItemImpl {
  value: string;
  label: string;
}

const {userBasic} = storeToRefs(userPinia);

const work = ref<WorkImpl | null>(null);
const workImages = ref<Array<string>>([]);
const authorArchive = ref<UserArchiveImpl | null>(null);
const uploaderArchive = ref<UserArchiveImpl | null>(null);
const viewItemList = ref<Array<viewItemImpl>>([
  {value: "view", label: "显示内容"},
  {value: "image", label: "显示图集"},
]);
const viewSelected = ref<string>("view");


const fetchWork = async (work_id: string) => {
  await baseHttp.get("/api/work/only", {params: {work_id}})
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) {
        work.value = data.data;
        if (!work.value) {
          errorMessage("获取作品信息失败");
          return;
        }
        fetchWorkAuthor();
        if (!work.value.is_claim) {
          fetchWorkUploader();
        }
      } else infoMessage(data.data);
    }).catch(e => {
      console.error(e);
      errorMessage("网络异常");
    });

}

const fetchWorkImages = async (work_id: string) => {
  await baseHttp.get("/api/work/image/images_by_work", {params: {work_id}})
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) {
        workImages.value = data.data;
      } else infoMessage(data.data);
    }).catch(e => {
      console.error(e);
      errorMessage("网络异常");
    });
}

const fetchWorkAuthor = async () => {
  if (!work.value) {
    errorMessage("未获取到作品信息");
    return;
  }
  await baseHttp.get("/user/current_by_uuid", {params: {uuid: work.value.author}})
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) {
        authorArchive.value = data.data;
      } else infoMessage(data.data);
    }).catch(e => {
      console.error(e);
      errorMessage("网络异常");
    });
}

const fetchWorkUploader = async () => {
  if (!work.value) {
    errorMessage("未获取到作品信息");
    return;
  }
  await baseHttp.get("/user/current_by_uuid", {params: {uuid: work.value.uploader}})
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) {
        uploaderArchive.value = data.data;
      } else infoMessage(data.data);
    }).catch(e => {
      console.error(e);
      errorMessage("网络异常");
    });
}

onMounted(() => {
  const queries: LocationQuery = route.query;
  const workId: string | undefined = queries.work_id as string | undefined;
  if (workId) {
    fetchWork(workId);
  } else errorMessage("作品ID未找到！");
})

watch(() => viewSelected.value, (newVal: string) => {
  if (newVal === "image") {
    if (work.value) {
      fetchWorkImages(work.value.id);
    } else {
      console.log("没有作品信息");
    }
  }
})

// 投诉相关的程序
const open = ref<boolean>(false);
const feedbackForm = ref<FeedBackImpl>({
  id: "", target_id: "", ipv4: null, ipv6: null, author: null, email: null, content: "", echo: null, type: "WORK", status: "PENDING", created_at: new Date(), updated_at: new Date()
});

const handleOk = async () => {
  if (userBasic.value) {
    feedbackForm.value.email = userBasic.value.email;
    feedbackForm.value.author = userBasic.value.uuid;
  }
  if (work.value) {
    feedbackForm.value.target_id = work.value.id;
  }
  if (feedbackForm.value.content.length === 0) {
    errorMessage("反馈内容不能为空");
    return;
  }
  console.log('Feedback Form Content: ', feedbackForm.value);
  await baseHttp.post("/feedback/upload", feedbackForm.value)
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) {
        successMessage(data.data);
        feedbackForm.value.content = "";
        feedbackForm.value.email = "";
        feedbackForm.value.target_id = "";
      } else {
        successMessage(data.msg);
      }
    }).catch(e => {
      console.error(e);
      errorMessage("网络错误");
    });
}

// 作品认领相关的程序
const claimOpen = ref<boolean>(false);
</script>

<template>
  <a-modal v-model:open="open" title="您要反馈这个作品吗？" ok-text="投诉" cancel-text="关闭" @ok="handleOk">
    <h3>作品名：{{work?.title || '加载中…'}}</h3>
    <div v-if="userBasic === null">
      您未登录，若要跟踪进度，可以输入您的电子邮箱：
      <a-input v-model:value="feedbackForm.email" type="email" placeholder="电子邮箱"/>
    </div>
    <br/>
    <a-textarea v-model:value="feedbackForm.content"/>
  </a-modal>
  <a-modal v-model="claimOpen" title="您要认领这个作品吗？" ok-text="认领" cancel-text="关闭">
    暂时缺少功能接口
  </a-modal>
  <div class="work_detail_container" v-if="work">
    <div class="title">
      <div class="cover_box">
        <span :class="`claim_ribbon${work.is_claim? ' claimed': ''}`"><span>{{work.is_claim? '已认领': '待认领'}}</span></span>
        <img class="cover_image" :src="work.cover_image" alt="cover_image" />
      </div>
      <div class="title_other">
        <h1 class="title_text">{{work.title}}</h1>
        <div class="time">
          <span><ClockCircleOutlined />上传时间：{{timestampToCn(work.created_at)}}</span>
          <span><SyncOutlined />更新时间：{{timestampToCn(work.updated_at)}}</span>
        </div>
        <div class="author_box">
          <div class="claim" v-if="work.is_claim">
            <span><UserOutlined />作者：{{authorArchive?.nickname || '未知用户'}}</span>
          </div>
          <div class="no_claim" v-else>
            <span><UserSwitchOutlined />暂归属：{{authorArchive?.nickname || '未知用户'}}</span>
            <span><UserAddOutlined />收录者：{{uploaderArchive?.nickname || '未知用户'}}</span>
            <span><UserOutlined />作者：{{work.author_nickname || '未知用户'}}</span>
          </div>
        </div>
        <hr style="width: 100%" />
        <div class="items">
          <div class="view_box item"><EyeOutlined class="icon"/>{{work.view_count}}</div>
          <div class="like_box item"><HeartOutlined />{{work.like_count}}</div>
          <div class="claim_box item" v-if="!work.is_claim" @click="claimOpen = true"><FlagOutlined />认领</div>
          <div class="feedback_box item" @click="open = true"><AlertOutlined />投诉反馈</div>
        </div>
      </div>
    </div>
    <div class="view_items">
      <div :class="`item ${item.value === viewSelected? 'active': 'no_active'}`" v-for="item in viewItemList" :key="item.value" @click="viewSelected = item.value">{{item.label}}</div>
    </div>
    <div class="content" v-html="work.content" v-if="viewSelected === 'view'"/>
    <div class="content images" v-else-if="viewSelected === 'image' && workImages.length > 0">
      <a-image :width="200" v-for="(item, index) in workImages" :key="index" :src="item"/>
    </div>
  </div>
  <div  v-else></div>
</template>

<style scoped>
.work_detail_container {
  width: 70%;
  margin: 2rem auto 0 auto;
}
.title {
  display: grid;
  grid-template-columns: 30rem 1fr;
  gap: 1rem;
}
.title_other {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.title_text {
  font-weight: bold;
}
.time {
  display: flex;
  gap: 10rem;
}
.no_claim {
  display: flex;
  flex-direction: column;
  gap: .2rem;
}
.items {
  display: flex;
  gap: 1rem;
  margin-top: auto;
  .item:hover {
    border: 1px solid #384a87;
    color: #fff;
    background-color: #384a8780;
    cursor: pointer;
  }
  .item {
    width: 3rem;
    height: 3rem;
    border-radius: 1rem;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    border: 1px solid #384a87;
    color: #384a87;
    background-color: #384a8720;
    transition: .3s ease;
  }
  .view_box, .view_box:hover {
    border: 1px solid #3b8738;
    color: #3b8738;
    background-color: #3b873820;
  }
  .like_box:hover {
    background-color: #fda5bc80;
    color: #fff;
    cursor: pointer;
    border: 1px solid #fda5bc;
  }
  .like_box {
    border: 1px solid #fda5bc;
    color: #fda5bc;
    background-color: #fda5bc00;
  }
  .feedback_box:hover {
    background-color: #fe4b7b80;
    color: #fff;
    cursor: pointer;
    border: 1px solid #fe4b7b;
  }
  .feedback_box {
    margin-left: auto;
    width: 6rem;
    border: 1px solid #fe4b7b;
    color: #fe4b7b;
    background-color: #fe4b7b00;
    transition: .3s ease;
  }
  .view_box .icon, .like_box .icon {
    font-size: 1.3rem;
    font-weight: bold;
  }
}
.view_items {
  width: 100%;
  border-bottom: 1px solid #9b9b9b50;
  margin: 2rem auto;
  padding-bottom: 1rem;
  display: flex;
  flex-direction: row;
  gap: 1rem;
  .item:hover {
    cursor: pointer;
    border: 1px solid #364d7950;
  }
  .item {
    transition: .3s ease;
    border-radius: 1rem;
    text-align: center;
    width: 50%;
    padding: 0.5rem 0;
  }
  .active {
    color: #fff;
    border: 1px solid #364d7950;
    background-color: #364d7950;
  }
  .no_active {
    color: #364d79;
    border: 1px solid #9b9b9b50;
  }
}
.left {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.author_time_box {
  display: flex;
  flex-direction: column;
  .author {
    display: flex;
    flex-direction: column;
  }
}
.cover_box {
  width: calc(16 * 1.8rem);
  height: calc(9 * 1.8rem);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 1rem;
  box-shadow: #9b9b9b 0 0 5px;
  position: relative;
  .claimed {
    background-color: #2faa41 !important;
  }
  .claim_ribbon {
    position: absolute;
    background-color: #fe4b7b;
    padding: 0.5rem;
    width: 11rem;
    text-align: center;
    right: -3rem;
    top: 2rem;
    font-weight: bold;
    box-shadow: #000 0 0 5px;
    transform: rotateZ(45deg);
    span {
      width: 11rem;
      color: white;
      letter-spacing: .5rem;
    }
  }

  .cover_image {
    width: 100%;
  }
}
</style>
