<script setup lang="ts">
import "@/style/Background.css";
import { onMounted, ref } from "vue";
import { fetchModuleBackgrounds } from "@/utils/backgroundApi.ts";

const bgUrl = ref<string>("");

onMounted(async () => {
  const list = await fetchModuleBackgrounds("join");
  if (list.length > 0) {
    bgUrl.value = list[Math.floor(Math.random() * list.length)];
  }
});
</script>

<template>
  <div class="bg_box">
    <img v-if="bgUrl" class="bgi" :src="bgUrl" alt="bg"/>
    <img v-else class="bgi" src="https://static.kivo.wiki/images/gallery/13/BG_View_Kivotos2.png" alt="bg"/>
  </div>
</template>

<style scoped>

</style>
