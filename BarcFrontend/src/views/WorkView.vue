<script setup lang="ts">
import HeaderComp from "@/components/HeaderFooter/HeaderComp.vue";
import {useRoute, type LocationQuery} from "vue-router";
import {onMounted, ref, watch} from "vue";
import WorkSearchBarComp from "@/components/Work/WorkSearchBarComp.vue";
import WorkTypeComp from "@/components/Work/WorkTypeComp.vue";
import WorkNoQueryComp from "@/components/Work/WorkNoQueryComp.vue";

const route = useRoute();

const isQuery = ref<boolean>(false);
const isDetail = ref<boolean>(false);

// 查看组件是否含参
const seeQueryObject = () => {
  const queries: LocationQuery = route.query;
  isQuery.value = Object.keys(queries).length > 0;
}

const seeDetail = () => {
  const routerName = route.name as string | undefined;
  if (!routerName) {
    return;
  }
  if (routerName === 'WorkDetail') isDetail.value = true;
}

onMounted(() => {
  seeQueryObject();
  seeDetail();
})

watch(() => route.name, () => {
  isDetail.value = route.name === 'WorkDetail';
})
</script>

<template>
  <HeaderComp/>
  <div class="is_detail" v-if="isDetail">
    <RouterView/>
  </div>
  <div class="no_detail" v-else>
    <div class="is_query" v-if="isQuery">
      <div class="top_box">
        <WorkSearchBarComp/>
        <WorkTypeComp/>
      </div>
      <RouterView/>
    </div>
    <div class="no_query" v-else>
      <WorkNoQueryComp/>
    </div>
  </div>
</template>

<style scoped>
.top_box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rem;
  border-bottom: #9b9b9b50 1px solid;
  margin-bottom: 2rem;
}
.no_query {
  height: calc(100vh - 90px);
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
