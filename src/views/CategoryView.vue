<script setup lang="ts">
import {useRoute, useRouter} from "vue-router";
import WorkCategorySearchBarComp from "@/components/Work/Category/WorkCategorySearchBarComp.vue";
import WorkCategoryTypeComp from "@/components/Work/Category/WorkCategoryTypeComp.vue";
import HeaderComp from "@/components/HeaderFooter/HeaderComp.vue";
import WorkCategoryGeneralComp from "@/components/Work/Category/WorkCategoryGeneralComp.vue";

const route = useRoute();
const router = useRouter();


</script>

<template>
  <HeaderComp/>
  <div class="container">
    <WorkCategorySearchBarComp/>
    <RouterView/>
  </div>
</template>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rem;
  border-bottom: #9b9b9b50 1px solid;
  margin-bottom: 2rem;
}
</style>
