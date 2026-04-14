<script setup lang="ts">
import {useUserPinia} from "@/stores/UserPinia.ts";
import {onMounted, watch} from "vue";
import {storeToRefs} from "pinia";
const userPinia = useUserPinia();

onMounted(() => {
  const token: string | null = window.localStorage.getItem("token");
  if (token) {
    userPinia.fetchUserInfo(token);
  } else {
    console.log("未登录");
  }
})

watch(() => window.localStorage.getItem("token"), (newVal: string | null) => {
  if (newVal) {
    userPinia.fetchUserInfo(newVal);
  } else {
    console.log("未登录");
  }
})
// userPinia.$subscribe((mutation, state) => {
//   console.log("订阅变化", mutation, state);
// })
</script>

<template>
<!--  <h1>You did it!</h1>-->
<!--  <p>-->
<!--    Visit <a href="https://vuejs.org/" target="_blank" rel="noopener">vuejs.org</a> to read the-->
<!--    documentation-->
<!--  </p>-->
  <RouterView/>
</template>

<style scoped>

</style>
