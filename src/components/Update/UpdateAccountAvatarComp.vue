<script setup lang="ts">
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {ref} from "vue";

const userPinia = useUserPinia();

const {userArchive} = storeToRefs(userPinia);

const newAvatarData = ref<File | null>(null);

</script>

<template>
  <div class="edit_avatar_box" v-if="userArchive">
    <div class="cover">
      更换头像
    </div>
    <img :src="userArchive.avatar || ''" alt="avatar" class="avatar"/>
  </div>
</template>

<style scoped>
.edit_avatar_box:hover {
  cursor: pointer;
}
.edit_avatar_box {
  position: relative;
  border: #deefff solid .3rem;
  border-radius: 50%;
  box-shadow: white 0 0 5px;
  overflow: hidden;
}
.avatar {
  width: 100%;
}
.cover:hover {
  opacity: 1;
}
.cover {
  color: white;
  width: 100%;
  height: 100%;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #9b9b9b80;
  z-index: 1;
  opacity: 0;
  transition: opacity 0.3s ease;
}
.new_avatar {
  background-color: #ffffff60;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 10rem;
  height: 10rem;
  border: #deefff solid .3rem;
  border-radius: 50%;
  box-shadow: white 0 0 5px;
}
</style>
