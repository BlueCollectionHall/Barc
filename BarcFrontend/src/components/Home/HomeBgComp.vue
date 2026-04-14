<script setup lang="ts">
import "@/style/Background.css";
import {onMounted, ref} from "vue";

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

const imageName = ref<string | null>(null);
const bg_box = ref<HTMLDivElement | null>(null);

const selectImage = () => {
  const date: Date = new Date();
  const hour: number = date.getHours();
  const baseUrl: string = "https://file.naigos.cn:52011/barctemp/"
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
  console.log(imageName.value);
}
onMounted(() => {
  selectImage();
  if (bg_box.value && imageName.value) {
    bg_box.value.style.backgroundImage = `url("${imageName.value}")`;
  }
})
</script>

<template>
  <div ref="bg_box" class="bg_box">
<!--    <img class="bgi" v-if="imageName" :src="imageName" alt="bg">-->
  </div>
</template>

<style scoped>
.bg_box {
  background-repeat: no-repeat;
  background-size: cover;
}
</style>
