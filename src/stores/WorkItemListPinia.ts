import {defineStore} from "pinia";
import {ref} from "vue";
import type {PageRequestImpl, PageResultImpl} from "@/interfaces/PageImpl.ts";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import {errorMessage} from "@/utils/MessageAlert.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {useRoute} from "vue-router";



export const useWorkItemListPinia = defineStore("work_item_list", () => {
  const workList = ref<Array<WorkImpl>>([]);
  const statusInPinia = ref<"PUBLIC" | "PRIVATE" | "BAN" | "OFF">("PUBLIC");
  const pageResult = ref<PageResultImpl<WorkImpl> | null>(null);
  const pageRequest = ref<PageRequestImpl>({page_num: 1, page_size: 24});

  const route = useRoute();
  /**
   * 获取作品列表以及分页返回信息
   * @param status 作品状态
   * */
  const fetchWorkList = async (status: "PUBLIC" | "PRIVATE" | "BAN" | "OFF") => {
    // 设定获取作品的状态
    statusInPinia.value = status;
    // 获取路由名
    const routerName = route.name as string | undefined;
    if (!routerName) {
      errorMessage("路径异常 -> WorkList");
      return;
    }
    // 获取关键词
    const keyword: string | undefined = route.query.keyword as string | undefined;
    if (!keyword) {
      errorMessage("参数异常 -> WorkList");
      return;
    }
    // 获取分页信息
    const pageNum: string | undefined = route.query.page_num as string | undefined;
    if (pageNum) pageRequest.value.page_num = Number(pageNum);
    else pageRequest.value.page_num = 1;
    // 整合params
    _packageParams(routerName, keyword);
    try {
      const response = await baseHttp.post("/api/work/works_by_page", pageRequest.value, {
        params: {
          status
        }
      });
      const data: ResponseImpl = response.data;
      if (data.code === 0) {
        pageResult.value = data.data;
        workList.value = pageResult.value?.list || [];
      }
    } catch (error) {
      console.error(error);
      errorMessage("获取作品信息时出现网络问题");
    }
  }
  /**
   * 封装PageRequest中params对象内容
   * @param routerName 路由名
   * @param keyword 关键词
   * */
  const _packageParams = (routerName: string, keyword: string): void => {
    switch (routerName) {
      case "WorkAll": {
        pageRequest.value.params = {keyword};
        break;
      } case "WorkStudent": {
        pageRequest.value.params = {student: keyword};
        break;
      } case "WorkSchool": {
        pageRequest.value.params = {school: keyword};
        break;
      } case "WorkClub": {
        pageRequest.value.params = {club: keyword};
        break;
      }
    }
  }
  return {workList, pageResult, pageRequest, fetchWorkList};
});
