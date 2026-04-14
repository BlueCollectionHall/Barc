import { createRouter, createWebHistory } from 'vue-router';
import App from "@/App.vue";
import HomeRouter from "@/Routers/Router/HomeRouter.ts";
import SignRouter from "@/Routers/Router/SignRouter.ts";
import UserRouter from "@/Routers/Router/UserRouter.ts";
import WorkRouter from "@/Routers/Router/WorkRouter.ts";
import MessageRouter from "@/Routers/Router/MessageRouter.ts";
import NoticeRouter from "@/Routers/Router/NoticeRouter.ts";

const routes = [HomeRouter, SignRouter, UserRouter, WorkRouter, MessageRouter, NoticeRouter];

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

export default router;