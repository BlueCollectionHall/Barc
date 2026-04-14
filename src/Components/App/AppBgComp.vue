<script setup lang="ts">
import "@/Styles/Background.css";
import {onMounted, ref} from "vue";

const bg_box = ref<HTMLDivElement | null>(null);

onMounted(() => {
  if (bg_box.value) {
    const now: number = new Date().getHours();
    if (21 <= now || now <= 6) {
      bg_box.value.style.backgroundImage = "url(https://naigos.cn/images/barc/yuuka-night.jpg)";
    } else if (6 < now && now <= 9) {
      bg_box.value.style.backgroundImage = "url(https://naigos.cn/images/barc/yuuka-morning.jpg)"
    } else {
      bg_box.value.style.backgroundImage = "url(https://static.kivo.wiki/images/gallery/12/BG_CS_PV4_018.png)";
    }
  }
})
</script>

<template>
  <div ref="bg_box" class="bg_box"></div>
</template>

<style scoped>
.bg_box {
  background-repeat: no-repeat;
  background-size: cover;
  min-width: 100%;
}
</style>