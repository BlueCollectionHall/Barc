<script setup lang="ts">
import "@/style/Background.css";
import { onMounted, ref } from "vue";
import { fetchModuleBackgrounds } from "@/utils/backgroundApi.ts";

interface ImageNameImpl {
  day: Array<string>;
  eventing: Array<string>;
  night: Array<string>;
}

const imagesName: ImageNameImpl = {
  day: ['bg_day_purana_1.jpg'],
  eventing: ['bg_evening_seia_1.jpg'],
  night: ['bg_night_hina_1.jpg'],
}

const newYearImagesName: Array<string> = ['newyear_1.jpg', 'newyear_2.jpg'];

const baseUrl: string = "https://file.naigos.cn:52011/barctemp/"

const imageName = ref<string | null>(null);
const bg_box = ref<HTMLDivElement | null>(null);

const fallbackSelect = () => {
  const date: Date = new Date();
  const hour: number = date.getHours();
  if (hour < 6) {
    imageName.value = baseUrl + imagesName.night[Math.floor(Math.random() * imagesName.night.length)];
  } else if (hour < 17) {
    imageName.value = baseUrl + imagesName.day[Math.floor(Math.random() * imagesName.day.length)];
  } else if (hour < 19) {
    imageName.value = baseUrl + imagesName.eventing[Math.floor(Math.random() * imagesName.eventing.length)];
  } else {
    imageName.value = baseUrl + imagesName.night[Math.floor(Math.random() * imagesName.night.length)];
  }
  // 大于等于12月 或者小于等于2月（新年期间）
  if (11 <= date.getMonth() || date.getMonth() <= 1) {
    imageName.value = baseUrl + newYearImagesName[Math.floor(Math.random() * newYearImagesName.length)];
  }
}

onMounted(async () => {
  const list = await fetchModuleBackgrounds("home");
  if (list.length > 0) {
    imageName.value = list[Math.floor(Math.random() * list.length)];
  } else {
    fallbackSelect();
  }
  if (bg_box.value && imageName.value) {
    bg_box.value.style.backgroundImage = `url("${imageName.value}")`;
  }
})
</script>

<template>
  <div ref="bg_box" class="bg_box">
  </div>
</template>

<style scoped>
.bg_box {
  background-repeat: no-repeat;
  background-size: cover;
}
</style>
