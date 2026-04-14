<script setup lang="ts">
import AccountHeaderComp from "@/components/Account/AccountHeaderComp.vue";
import {onMounted, ref} from "vue";
import {errorMessage} from "@/utils/MessageAlert.ts";
import {useRoute} from "vue-router";
const route = useRoute();

const isToken = ref<boolean>(false);
const isRouterRight = ref<boolean>(false);

onMounted(() => {
  if (window.localStorage.getItem("token")) isToken.value = true;
  else errorMessage("未登录，无法使用上传功能！");
  if (route.path.split("/").pop() !== "upload") isRouterRight.value = true;
})
</script>

<template>
  <AccountHeaderComp/>
  <RouterView v-if="isRouterRight"/>
  <div v-else>
    <h1>你进入了一个不可到达的地方！</h1>
    <p>请不要自行篡改浏览器地址哦~</p>
  </div>
</template>

<style scoped>

</style>
