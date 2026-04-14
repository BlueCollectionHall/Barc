<script setup lang="ts">
import "@/style/Header.css";
import {onMounted, ref} from "vue";
import type {CategoryImpl} from "@/interfaces/CategoryImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
const userPinia = useUserPinia();
import {useRouter} from "vue-router";
const router = useRouter();

const category = ref<Array<CategoryImpl> | null>(null);
const {userArchive, userBasic} = storeToRefs(userPinia);

const fetchCategory = async () => {
  await baseHttp ({
    url: "/api/category/all_top_categories",
    method: "GET"
  }).then(res => {
    if (res.data.code === 0) {
      category.value = res.data.data;
    }
  })
}

const toAccountView = (username: string) => {
  router.push({name: "Account", query: {username: username}});
}
onMounted(() => {
  fetchCategory();
})
</script>

<template>
  <header class="header">
    <!--  logo  -->
    <div class="logo">
      <router-link style="text-decoration: none; color: #000" to="/">B.A.R.C</router-link>
    </div>

    <!--  menu  -->
    <div class="menu-container" v-if="category">
      <router-link v-for="item in category" :key="item.id" class="menu-item" :to="`/category/${item.id}`">{{item.name}}</router-link>
<!--      <router-link class="menu-item" to="/" active-class="active">主页</router-link>-->
<!--      <router-link class="menu-item" to="/works" active-class="active">作品</router-link>-->
<!--      <router-link class="menu-item" to="/about" active-class="active">关于</router-link>-->
<!--      <router-link class="menu-item" to="/more" active-class="active">更多</router-link>-->
<!--      <router-link class="menu-item" to="/test" active-class="active">接口测试</router-link>-->
      <router-link class="menu-item" to="/sign" active-class="active" v-if="userArchive === null || userBasic === null">登录 | 注册</router-link>
<!--      <router-link class="menu-item" to="/user" active-class="active" v-else>{{ userArchive?.nickname }}</router-link>-->
      <img @click="toAccountView(userBasic.username)" v-else :src="userArchive.avatar || 'null'" alt="avatar" style="width: 48px; border: #deefff 2px solid; border-radius: 20px"/>
    </div>

  </header>
</template>

<style scoped>

</style>
