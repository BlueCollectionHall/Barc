<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from "vue";

const currentTime = ref(new Date());
const timeSelect = ref<string>("local");
const templateShow = ref<string>("");

const formatTime = (date: Date, timezone: number | null = null) => {
  let targetDate = date;
  // 非本地
  if (timezone !== null) {
    const utc = date.getTime() + (date.getTimezoneOffset() * 60000);
    // 西方
    if (timezone <= 0) {
      targetDate = new Date(utc - (3600000 * timezone));
    }
    // 东方
    else {
      targetDate = new Date(utc + (3600000 * timezone));
    }
  }
  const year = targetDate.getFullYear()
  const month = (targetDate.getMonth() + 1).toString().padStart(2, '0')
  const day = targetDate.getDate().toString().padStart(2, '0')
  const hours = targetDate.getHours().toString().padStart(2, '0')
  const minutes = targetDate.getMinutes().toString().padStart(2, '0')
  const seconds = targetDate.getSeconds().toString().padStart(2, '0')
  return `${year}年${month}月${day}日 ${hours}:${minutes}:${seconds}`
}

const formatLocalTime = computed(() => formatTime(currentTime.value));
const formatChinaTime = computed(() => formatTime(currentTime.value, 8));
const formatJapanTime = computed(() => formatTime(currentTime.value, 9));

const showTime = (target: string = "local"): void => {
  switch (target) {
    case "local": templateShow.value = `本地时间 ${formatLocalTime.value}`; break;
    case "china": templateShow.value = `中国时间 ${formatChinaTime.value}`; break;
    case "japan": templateShow.value = `日本时间 ${formatJapanTime.value}`; break;
    default: templateShow.value = "Time Error"; break;
  }
}
let timer: number;
onMounted(() => {
  timer = setInterval(() => {
    currentTime.value = new Date();
    showTime(timeSelect.value);
  }, 1000);
})
onUnmounted(() => {
  clearInterval(timer);
})
</script>

<template>
  <div class="time_box">
    <div class="buttons">
      <a-button class="button" :type="timeSelect === 'local'? 'primary': 'default'" @click="timeSelect = 'local'">本地时间</a-button>
      <a-button class="button" :type="timeSelect === 'china'? 'primary': 'default'" @click="timeSelect = 'china'">中国时间</a-button>
      <a-button class="button" :type="timeSelect === 'japan'? 'primary': 'default'" @click="timeSelect = 'japan'">日本时间</a-button>
    </div>
    <div class="content">{{templateShow}}</div>
  </div>
</template>

<style scoped>
.time_box {
  position: relative;
  height: 4rem;
  background-color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}
.buttons {
  position: absolute;
  top: -1rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  flex-direction: row;
  gap: 1rem;
  .button {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 1.8rem;
    font-size: 1rem;
  }
}
</style>
