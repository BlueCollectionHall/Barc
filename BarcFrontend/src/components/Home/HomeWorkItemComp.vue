<script setup lang="ts">
import {ref} from "vue";

const selectTitle = ref<string>("new");
</script>

<template>
  <div class="work_box">
    <div class="title">
      <h2 :class="selectTitle === 'new'? 'select_title title_item': 'title_item'" @click="selectTitle = 'new'">最近收录</h2>
      <h2 :class="selectTitle === 'hot'? 'select_title title_item': 'title_item'" @click="selectTitle = 'hot'">热度排行</h2>
    </div>
    <div class="works">
      <div v-for="item in 8" :key="item" class="work">
        <div class="cover_box">

        </div>
        <div class="work_title">这是测试标题这是测试标题这是</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.work_box {
  background-color: #ffffff80;
  width: 100%;
  padding: 1rem 2rem 3rem 2rem;
}
.title {
  display: flex;
  justify-content: space-between;
  font-weight: normal;
}
.select_title {
  color: #1677ff;
}
.title_item {
  transition: .3s ease;
}
.title_item:hover {
  cursor: pointer;
  transform: translateY(-.2rem);
}
.works {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}
.work {
  .cover_box {
    background-color: white;
    width: 250px;
    height: 140px;
  }
}
</style>
