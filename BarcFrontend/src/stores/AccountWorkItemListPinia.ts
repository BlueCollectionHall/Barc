import {defineStore} from "pinia";
import {ref} from "vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import type {PageRequestImpl, PageResultImpl} from "@/interfaces/PageImpl.ts";
import {useRoute, useRouter} from "vue-router";

export const useAccountWorkItemPinia = defineStore('account_work_list', () => {

  const route = useRoute();
  const router = useRouter();

  const pageRequest = ref<PageRequestImpl>({page_num: 1, page_size: 15});
  const pageResult = ref<PageResultImpl<WorkImpl> | null>(null);

  const fetchWorks = async (username: string) => {
    // try {
    //   const response = await baseHttp.get("/api/work/works_by_username", {params: {status: "PUBLIC", username: username}});
    //   const data: ResponseImpl = response.data;
    //   if (data.code === 0) workList.value = data.data;
    //   else infoMessage(data.msg);
    // } catch {
    //   errorMessage("网络错误！");
    // }

    // 整合分页请求时的params
    let fetchType: string | undefined = route.query.type as string | undefined;
    if (fetchType === undefined) {
      fetchType = 'works';
    }
    switch (fetchType) {
      case "works": {
        pageRequest.value.params = {author_username: username};
        break;
      } case "collections": {
        pageRequest.value.params = {uploader_username: username};
        break;
      } default: {
        errorMessage("客户端处理分类选择时出现异常问题！反馈点：AccountWorkItemError->2");
        return;
      }
    }
    // 获取分页信息
    const pageNum: string | undefined = route.query.page_num as string | undefined;
    if (pageNum) pageRequest.value.page_num = Number(pageNum);
    else pageRequest.value.page_num = 1;
    console.log('当前pinia中分页信息：', pageRequest);
    // 获取作品列表
    await baseHttp.post("/api/work/works_by_page", pageRequest.value, {params: {status: 'PUBLIC'}})
      .then(res => {
        const data: ResponseImpl = res.data;
        if (data.code === 0) {
          pageResult.value = data.data;
        } else infoMessage(data.msg);
      }).catch(error => {
        console.error(error);
        errorMessage("网络错误");
      })
  }
  return {pageRequest, pageResult, fetchWorks}
})
