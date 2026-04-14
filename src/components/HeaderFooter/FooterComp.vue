<script setup lang="ts">
import {onMounted, ref} from "vue";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {useRouter} from "vue-router";
const router = useRouter();

interface ItemImpl {
  label: string;
  value: string;
}

const items: Array<ItemImpl> = [
  {label: "服务条款", value: "Service"},
  {label: "隐私政策", value: "Private"},
  {label: "投诉反馈", value: "Feedback"},
  {label: "加入我们", value: "Join"},
]
const copyright = ref<string | null>(null);
const icp = ref<string | null>(null);

const fetchIcp = async () => {
  try {
    const response = await baseHttp.get("/info/icp_num");
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      icp.value = data.data;
    } else infoMessage("一个不必要组件的信息获取异常");
  } catch {
    errorMessage("一个不必要组件的信息获取异常");
  }
}
const fetchCopyright = async () => {
  try {
    const response = await baseHttp.get("/info/copyright");
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      copyright.value = data.data;
    } else infoMessage("一个不必要组件的信息获取异常");
  } catch {
    errorMessage("一个不必要组件的信息获取异常");
  }
}
const itemClicked = (value: string) => {
  router.push({name: value});
}

onMounted(async () => {
  await Promise.all([fetchIcp(), fetchCopyright()]);
})
</script>

<template>
  <div class="container">
    <div class="text" v-if="copyright">{{ copyright }}</div>
    <div class="footer_items">
      <a-button class="item" type="primary" ghost
                v-for="item in items" :key="item.value"
                @click="itemClicked(item.value)">
        {{item.label}}
      </a-button>
    </div>
    <a href="https://beian.miit.gov.cn/" target="_blank">黑ICP备2025036071号-2</a>
  </div>
</template>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 100%;
  padding: 1.5rem 0;
  background-color: #383838;
  gap: .8rem;
  .text {
    color: #fff;
  }
  a {
    color: #deefff;
    text-decoration: none;
  }
}
.footer_items {
  display: flex;
  flex-direction: row;
  justify-content: center;
  align-items: center;
  gap: .5rem;
  color: white;
  .item {
    color: #fff;
    border: 1px solid #deefff;
  }
}
</style>
