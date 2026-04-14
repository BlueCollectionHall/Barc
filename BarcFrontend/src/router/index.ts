import { createRouter, createWebHistory } from 'vue-router'
import HomeRouter from "@/router/HomeRouter.ts";
import SignRouter from "@/router/SignRouter.ts";
import MessageBoardRouter from "@/router/MessageBoardRouter.ts";
import AccountRouter from "@/router/AccountRouter.ts";
import StudentRouter from "@/router/StudentRouter.ts";
import ServiceRouter from "@/router/ServiceRouter.ts";
import FeedbackRouter from "@/router/FeedbackRouter.ts";
import JoinRouter from "@/router/JoinRouter.ts";
import UploadRouter from "@/router/UploadRouter.ts";
import ManageRouter from "@/router/ManageRouter.ts";
import UpdateRouter from "@/router/UpdateRouter.ts";
import NoticeRouter from "@/router/NoticeRouter.ts";
import WorkRouter from "@/router/WorkRouter.ts";
import CategoryRouter from "@/router/CategoryRouter.ts";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    HomeRouter,
    SignRouter,
    MessageBoardRouter,
    AccountRouter,
    StudentRouter,
    ServiceRouter,
    FeedbackRouter,
    JoinRouter,
    UploadRouter,
    ManageRouter,
    UpdateRouter,
    NoticeRouter,
    WorkRouter,
    CategoryRouter,
  ],
})

// 全局前置路由守卫
router.beforeEach((to, from, next) => {
  // 如果是从/work下的子路由跳转到另一个/work下的子路由
  if (from.path.startsWith("/work") && to.path.startsWith("/work")) {
    // 保留from开源的query参数，除非to去向已经拥有了query参数
    // 当to去向没有query 并且from来源拥有query参数
    if (Object.keys(to.query).length === 0 && Object.keys(from.query).length > 0) {
      next({
        path: to.path,
        query: from.query,
      });
      return;
    }
  }
  next();
})

export default router
