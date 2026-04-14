<script setup lang="ts">
import "@/Styles/Background.css";
import {onMounted, ref} from "vue";

const bg_box = ref<HTMLDivElement | null>(null);

onMounted(() => {
  if (bg_box.value) {
    bg_box.value.style.backgroundImage = `url(https://naigos.cn/images/barc/yuuka2.jpg)`;
  }
})
</script>

<template>
  <div ref="bg_box" class="bg_box">

  </div>
</template>

<style scoped>
.bg_box {
  min-width: 100%;
  background-size: cover;
}
</style>