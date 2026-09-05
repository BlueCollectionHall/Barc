<script setup lang="ts">
import "@/style/Background.css";
import { computed, onMounted, ref } from "vue";
import { fetchModuleBackgrounds } from "@/utils/backgroundApi.ts";

const bgUrl = ref<string>("");
const bgStyle = computed(() => bgUrl.value ? { backgroundImage: `url("${bgUrl.value}")` } : undefined);

onMounted(async () => {
  const list = await fetchModuleBackgrounds("messageboard");
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
  background: url("https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/FX_TEX_CH0071_Prop_03.png") no-repeat;
  background-size: cover;
}
</style>
