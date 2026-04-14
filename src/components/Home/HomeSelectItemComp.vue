<script setup lang="ts">
const itemList: Array<{label: string; value: string; logo: string}> = [
  {label: "学园", value: "school", logo: "https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/Goods_Icon_Gem_490_Preset_2.png"},
  {label: "部团", value: "club", logo: "https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/Goods_Icon_Monthly_1.png"},
  {label: "学生", value: "student", logo: "https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/Goods_Icon_Monthly_0_Preset_2.png"}
];
</script>

<template>
  <div class="select_items">
    <div class="item" v-for="item in itemList" :key="item.value">
      <img :src="item.logo" alt="logo"/>
      {{item.label}}
    </div>
  </div>
</template>

<style scoped>
.select_items {
  display: flex;
  flex-direction: row;
  gap: 1rem;
  justify-content: space-evenly;
  background: linear-gradient(rgba(255, 255, 255, 1) 50%, rgba(255, 255, 255, 0) 90%);
}
.select_items .item {
  padding: .5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  font-weight: bold;
  /* 下划线样式 */
  background: linear-gradient(to right, #9b9b9b, #9b9b9b) no-repeat right bottom;
  background-size: 0 2px;
  transition: background-size .2s;
}
.item:hover {
  cursor: pointer;
  background-position: left bottom;
  background-size: 100% 2px;
}
.select_items .item img {
  width: 6rem;
}
</style>
