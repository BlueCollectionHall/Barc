<script setup lang="ts">
import {onMounted, ref} from "vue";
import {useRouter, useRoute} from "vue-router";

const router = useRouter();
const route = useRoute();

interface ItemImpl {
  label: string; icon?: string; router: string;
}

const items: Array<ItemImpl> = [
  {label: "综合", router: "WorkAll"},
  {label: "学园", router: "WorkSchool"},
  {label: "部团", router: "WorkClub"},
  {label: "学生", router: "WorkStudent"},
  // {label: "用户", router: "WorkUser"},
]

const selectedRouter = ref<string>("WorkAll");

const itemClicked = (target: string) => {
  selectedRouter.value = target;
  if (route.query.keyword) {
    router.push({name: target, query: {keyword: route.query.keyword}});
  } else {
    router.push({name: target});
  }
}

onMounted(() => {
  const routerName = route.name as string | undefined;
  if (routerName) {
    selectedRouter.value = routerName;
  }
})
</script>

<template>
  <div class="items">
    <div
      :class="'item' + (selectedRouter === item.router ? ' selected' : '')"
      v-for="item in items"
      :key="item.router"
      @click="itemClicked(item.router)"
    >
      {{item.label}}
    </div>
  </div>
</template>

<style scoped>
.items {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 2rem;
  gap: 1rem;
  .item:hover {
    cursor: pointer;
  }
  .item {
    color: #364d79;
    font-size: 1.1rem;
  }
}
.selected {
  color: #bf61a1 !important;
}
</style>
