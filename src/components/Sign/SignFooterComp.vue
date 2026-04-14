<script setup lang="ts">
const itemList: Array<{label: string; value: string; logo: string}> = [
  {label: "BARC是什么？", value: "what", logo: "QuestionOutlined"},
  {label: "账号等级是什么样的？", value: "level", logo: "BranchesOutlined"},
  {label: "Naigos账号是什么？", value: "naigos", logo: "ApiOutlined"}
]
</script>

<template>
  <footer class="footer">
    <div class="items">
      <div class="item" v-for="item in itemList" :key="item.value" :title="item.label">
        <component :is="item.logo"></component>
      </div>
    </div>
    <div class="title">
      <a href="https://beian.miit.gov.cn/" target="_blank" style="font-size: 14px">黑ICP备2025036071号-2</a>
      <div class="text">B.A.R.C &copy; 2025 Created by SpringBoot V0.16-neo-beta</div>
    </div>

  </footer>
</template>

<style scoped>
.footer {
  width: 100%;
  height: 10rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 1rem;
}
.title {
  color: #56508c;
  display: flex;
  font-weight: bold;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: .5rem;
  a {
    color: #56508c;
  }
}
.items {
  display: flex;
  gap: 4rem;
  .item {
    color: #657fd1;
    padding: .5rem;
    font-size: 2rem;
    background-color: #ffffff80;
    border: #657fd1 2px solid;
    border-radius: 1rem;
    transition: background-color, color, border .5s ease;
  }
}
.item:hover {
  background-color: white;
  color: #383838;
  border: #383838 2px solid;
}
</style>
