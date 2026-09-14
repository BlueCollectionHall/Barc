<script setup lang="ts">
import "@/style/Background.css";
import { onMounted, ref } from "vue";
import {
  fetchFestivals,
  fetchModuleBackgroundItems,
  pickBackgroundByScene,
  resolveFestivalForDate,
  resolveCurrentScene,
  TIME_PERIOD,
  type FestivalItem,
} from "@/utils/backgroundApi.ts";

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

/**
 * 兜底：当管理端没有任何启用背景图时，使用写死图片。
 * 时段判定复用 resolveCurrentScene，节日判定复用 resolveFestivalForDate（与主链路同源），
 * 避免两处逻辑不一致。
 */
const fallbackSelect = (festivals: FestivalItem[]) => {
  const now = new Date();
  const scene = resolveCurrentScene(now);
  if (resolveFestivalForDate(festivals, now)) {
    imageName.value = baseUrl + newYearImagesName[Math.floor(Math.random() * newYearImagesName.length)];
    return;
  }
  const pool =
    scene.time_period === TIME_PERIOD.DAY ? imagesName.day
      : scene.time_period === TIME_PERIOD.EVENTING ? imagesName.eventing
        : imagesName.night;
  imageName.value = baseUrl + pool[Math.floor(Math.random() * pool.length)];
}

onMounted(async () => {
  // 并行取「启用的首页背景图」与「启用中的节日」（节日由馆长在管理端维护，不再写死在用户端）
  const [items, festivals] = await Promise.all([
    fetchModuleBackgroundItems("home"),
    fetchFestivals(),
  ]);
  // 按当前时段与动态节日选出候选池后随机取一张（每次访问换一张）
  const picked = pickBackgroundByScene(items, new Date(), festivals);
  imageName.value = picked;
  if (!imageName.value) {
    fallbackSelect(festivals);
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
