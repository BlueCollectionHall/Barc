<script setup lang="ts">
import {ReadOutlined} from '@ant-design/icons-vue';
</script>

<template>
  <div class="article_box">
    <div class="title">
<!--      <img class="icon" src="https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/ClanChat_Emoji_Tab_0_2.png" alt="icon"/>-->
      <ReadOutlined />
      文章
    </div>
    <div class="articles no_list" >
      <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E8%A1%A8%E6%83%85%E5%8C%85/%E6%95%B4%E5%A4%87_11.gif" alt="icon"/>
      <span>暂无数据</span>
    </div>
  </div>
</template>

<style scoped>
.article_box {
  background-color: white;
  padding: 1rem;
  min-height: 10rem;
}
.title {
  display: flex;
  align-items: center;
  gap: .5rem;
  font-size: 1.2rem;
  .icon {
    height: 3rem;
  }
}
.no_list {
  display: flex;
  gap: .5rem;
  font-size: 1.1rem;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  .icon {
    width: 5rem;
  }
}
</style>
