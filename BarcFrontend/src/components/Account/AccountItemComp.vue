<script lang="ts">
import {onMounted, ref, watch} from "vue";
import type {LocationQuery, LocationQueryRaw, Router} from "vue-router";

export interface ItemImpl {label: string; value: string; icon: string; color: string}

export const accountItemList: Array<ItemImpl> = [
  {label: "作品集", value: "works", icon: "HighlightOutlined", color: "#0EB350"},
  {label: "收录集", value: "collections", icon: "ReadOutlined", color: "#40C5F1"},
  {label: "喜欢", value: "likes", icon: "HeartOutlined", color: "#F85A54"},
];

interface AccountRouteLike {query: LocationQuery}
interface AccountRouterLike {replace: Router["replace"]}

const getSelectedAccountItemValue = (type: unknown): string => {
  if (typeof type === "string" && accountItemList.some(item => item.value === type)) {
    return type;
  }
  return "works";
}

const getRouteUsername = (username: unknown): string | null => {
  if (typeof username === "string" && username.length > 0) return username;
  return null;
}

const buildAccountItemQuery = (query: LocationQuery, target: string): LocationQueryRaw => {
  const nextQuery: LocationQueryRaw = {...query, type: target};
  delete nextQuery.page_num;
  return nextQuery;
}

export const useAccountItemController = ({
  route,
  router,
  fetchWorks,
  reportError,
}: {
  route: AccountRouteLike;
  router: AccountRouterLike;
  fetchWorks: (username: string) => unknown;
  reportError: (message: string) => unknown;
}) => {
  const selectedItem = ref<string>(getSelectedAccountItemValue(route.query.type));

  const reportMissingUsername = () => reportError("页面缺少重要数据！");

  const fetchCurrentRouteWorks = () => {
    const username = getRouteUsername(route.query.username);
    if (!username) {
      reportMissingUsername();
      return;
    }
    fetchWorks(username);
  }

  const changeSelect = (target: string) => {
    selectedItem.value = target;
    const username = getRouteUsername(route.query.username);
    if (username) {
      router.replace({query: buildAccountItemQuery(route.query, target)});
    } else reportMissingUsername();
  }

  onMounted(() => {
    selectedItem.value = getSelectedAccountItemValue(route.query.type);
    const username = getRouteUsername(route.query.username);
    if (!username) {
      reportMissingUsername();
      return;
    }
    router.replace({query: {...route.query, type: selectedItem.value}});
    fetchWorks(username);
  })

  watch(() => route.query, () => {
    selectedItem.value = getSelectedAccountItemValue(route.query.type);
    fetchCurrentRouteWorks();
  })

  return {selectedItem, changeSelect};
}
</script>

<script setup lang="ts">
import {useAccountWorkItemPinia} from "@/stores/AccountWorkItemListPinia.ts";
import {useRoute, useRouter} from "vue-router";
import {errorMessage} from "@/utils/MessageAlert.ts";

const accountWorkItemPinia = useAccountWorkItemPinia();

const route = useRoute();
const router = useRouter();

const itemList = accountItemList;

// const {selectedItemValue} = storeToRefs(accountWorkItemPinia);
const {selectedItem, changeSelect} = useAccountItemController({
  route,
  router,
  fetchWorks: accountWorkItemPinia.fetchWorks,
  reportError: errorMessage,
});
</script>

<template>
  <div class="account_item_box">
    <div class="items">
      <div class="item" v-for="item in itemList" :key="item.value" @click="changeSelect(item.value)">
        <component class="icon" :is="item.icon" :style="{'color': item.color}"/>
        <div :class="selectedItem === item.value? 'item_selected': ''">{{item.label}}</div>
      </div>
<!--      <div style="margin-right: auto">施工中</div>-->
    </div>
  </div>
</template>

<style scoped>
.account_item_box {
  height: 4rem;
  width: 100%;
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  position: relative;
  padding: 0 10% 0 10%;
}
.items {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 1rem;
}
.item {
  transition: .3s ease;
  display: flex;
  flex-direction: row;
  align-items: center;
  font-size: 1rem;
  gap: .4rem;
  color: #18191C;
  .icon {
    font-size: 1.6rem;
  }
}
.item_selected {
  color: #00AEEC;
}
.item:hover {
  color: #fe4b7b;
  cursor: pointer;
}
</style>
