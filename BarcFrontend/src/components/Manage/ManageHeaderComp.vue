<script setup lang="ts">
import "@/style/Header.css";
import {useRouter} from "vue-router";
const router = useRouter();
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {onMounted, ref, watch} from "vue";
const userPinia = useUserPinia();

const {userArchive} = storeToRefs(userPinia);

interface ItemImpl {label: string; value: string; identity: string; permission: number; icon?: string;}
const items: Array<ItemImpl> = [
  {label: "作品", value: "ManageWork", identity: "USER", permission: 1, icon: "HighlightOutlined"},
  {label: "文章", value: "ManageArticle", identity: "USER", permission: 1, icon: "FileTextOutlined"},
  {label: "公告", value: "ManageNotice", identity: "MANAGER", permission: 1, icon: "SoundOutlined"}
]

const itemClicked = (value: string) => {
  router.replace({name: value});
}

onMounted(() => {
})
</script>

<template>
  <header class="header">
    <div class="anotherInfo">
      B.A.R.C
    </div>
    <div class="menu-container" v-if="userArchive">
      <div class="menu-item"
           v-for="item in items"
           :key="item.value"
           @click="itemClicked(item.value)">
        <div v-if="item.identity === 'USER' && item.permission <= userArchive.permission">
          <component :is="item.icon" />
          {{item.label}}
        </div>
        <div v-else-if="item.identity === userArchive.identity && item.permission <= userArchive.permission">
          <component :is="item.icon" />
          {{item.label}}
        </div>
      </div>
    </div>
    <div v-else>加载中…</div>
  </header>
</template>

<style scoped>

</style>
