<script setup lang="ts">
import "@/style/Background.css";
import { computed, onMounted, ref } from "vue";
import { fetchModuleBackgrounds } from "@/utils/backgroundApi.ts";

const bgUrl = ref<string>("");
const bgStyle = computed(() => bgUrl.value ? { backgroundImage: `url("${bgUrl.value}")` } : undefined);

onMounted(async () => {
  const list = await fetchModuleBackgrounds("feedback");
  if (list.length > 0) {
    bgUrl.value = list[Math.floor(Math.random() * list.length)];
  }
});
</script>

<template>
  <div class="bg_box" :style="bgStyle"></div>
</template>

<style scoped>
.bg_box {
  background: url("https://static.kivo.wiki/images/gallery/13/BG_ReceptionRoom.png") no-repeat;
  background-size: cover;
}
</style>
