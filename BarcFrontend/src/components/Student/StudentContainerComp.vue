<script setup lang="ts">
import {useRoute, useRouter} from "vue-router";
import {onMounted, ref} from "vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {EyeOutlined, HeartOutlined} from "@ant-design/icons-vue";
import type {PageRequestImpl, PageResultImpl} from "@/interfaces/PageImpl.ts";

const route = useRoute();
const router = useRouter();

const works = ref<Array<WorkImpl>>([]);
const requestPage = ref<PageRequestImpl>({page_num: 1, page_size: 12});
const resultPage = ref<PageResultImpl<WorkImpl> | null>(null);

const fetchWorks = async (student_id: string) => {
  requestPage.value.params = {student_id};
  console.log("this is params ed requestPage: ", requestPage.value);
  try {
    const response = await baseHttp.post("/api/work/works_by_page", requestPage.value, {
      params: {status: "PUBLIC"}
    });
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      resultPage.value = data.data;
      works.value = resultPage.value?.list || [];
    } else infoMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络异常");
  }
}

const workItemClicked = (work_id: string) => {
  router.push({name: "WorkDetail", query: {work_id}});
}

onMounted(async () => {
  const studentId: string = route.query.student_id as string;
  if (studentId) {
    await fetchWorks(studentId);
  }
})
</script>

<template>
  <div class="container" v-if="works.length > 0">
    <div class="items">
      <div class="item" v-for="item in works" :key="item.id" @click="workItemClicked(item.id)">
        <div class="cover_box">
          <img class="cover_image" :src="item.cover_image" alt="cover"/>
          <div class="cover_z">
            <EyeOutlined /> {{item.view_count}} &nbsp;&nbsp; <HeartOutlined /> {{item.like_count}}
          </div>
        </div>
        {{item.title}}
      </div>
    </div>

  </div>
  <div class="container no_list" v-else>
    <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E8%A1%A8%E6%83%85%E5%8C%85/%E7%8E%A9%E6%89%8B%E6%9C%BA_09.gif" alt="icon"/>
    <span>暂无数据</span>
  </div>
</template>

<style scoped>
@keyframes container_in {
  0% {
    opacity: 0;
  }
  100% {
    opacity: 1;
  }

}
.container {
  opacity: 0;
  animation: container_in 1s .5s ease forwards;
  box-shadow: 0 0 .05rem .05rem rgb(0 0 0 / 5%);
  margin: 2rem auto 0 auto;
  width: 70%;
  min-height: 20rem;
  background-color: #ffffff50;
  backdrop-filter: blur(2px);
  display: flex;
  justify-content: center;
  padding: 2rem 0 2rem 0;
}
.items {
  display: grid;
  grid-template-columns: repeat(4, 1fr);;
  justify-content: start; /* 改为左对齐 */
  gap: 3rem 3rem;
}
.item:hover {
  cursor: pointer;
  transform: translateY(-.5rem);
}
.item {
  transition: .3s ease;
  flex: 0 0 calc(20% - 1.5rem);
}
.cover_box {
  border-radius: .5rem;
  width: calc(16 * .9vw);
  height: calc(9 * .9vw);
  overflow: hidden;
  position: relative;
  .cover_image {
    width: 100%;
    z-index: -2;
  }
}
.cover_z {
  color: white;
  width: 100%;
  background: linear-gradient(180deg,rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, .8) 150%);
  position: absolute;
  bottom: 0;
  padding: .4rem 0 .4rem .8rem;
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
