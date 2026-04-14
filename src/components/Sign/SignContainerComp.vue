<script setup lang="ts">
import {ref} from "vue";
import SignSigninComp from "@/components/Sign/SignSigninComp.vue";
import SignSignupComp from "@/components/Sign/SignSignupComp.vue";

const itemList: Array<{label: string; value: string; logo: string}> = [
  {label: "登录", value: "in", logo: "KeyOutlined"},
  {label: "注册", value: "up", logo: "HeartOutlined"}
]

const signInOrUp = ref<string>("in");
</script>

<template>
  <div class="container">
    <SignSigninComp v-if="signInOrUp === 'in'"/>
    <SignSignupComp v-else-if="signInOrUp === 'up'"/>
    <div class="select_in_up_items items">
      <button :class="`item${signInOrUp === item.value? ' item_select': ''}`" v-for="item in itemList" :key="item.value" @click="signInOrUp = item.value">
        <component :is="item.logo"/>
        {{item.label}}
      </button>
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: 100vw;
  height: calc(100vh - 10rem);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  gap: 1rem;
}
.items {
  display: flex;
  flex-direction: row;
  gap: 1rem;
  .item {
    height: 4rem;
    width: 4rem;
    background-color: #ffffff50;
    border: white 2px solid;
    border-radius: 1rem;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    color: #f9a5e5;
    transition: background-color, border, color .5s ease;
  }
  z-index: 1;
}
.item:hover {
  cursor: pointer;
  background-color: #fff;
  border: #383838 2px solid;
  color: #383838;
}
.item_select {
  border: #f9a5e5 2px solid !important;
}

</style>
