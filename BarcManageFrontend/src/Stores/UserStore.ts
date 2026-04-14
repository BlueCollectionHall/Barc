import {defineStore} from "pinia";
import type {UserArchiveImpl, UserBasicImpl} from "@/Interfaces/UserImpl.ts";
import {ref} from "vue";
import {baseHttp} from "@/Utils/Request.ts";
import type {ResponseImpl} from "@/Interfaces/ResponseImpl.ts";

export const useUserStore = defineStore("userStore", () => {
    const userArchive = ref<UserArchiveImpl | null>(null);
    const userBasic = ref<UserBasicImpl | null>(null);

    const fetchUserInfo = async (token: string) => {
        try {
            const responseArchive = await baseHttp(
                "/user/current_me",
                {
                    headers: {
                        Authorization: token
                    }
                });
            const responseBasic = await baseHttp(
                "/user/basic_me",
                {
                    headers: {
                        Authorization: token
                    }
                });
            const dataArchive: ResponseImpl = responseArchive.data;
            const dataBasic: ResponseImpl = responseBasic.data;
            if (dataArchive.code === 0 && dataBasic.code === 0) {
                userArchive.value = dataArchive.data;
                userBasic.value = dataBasic.data;
            }
            return;
        } catch {
            window.localStorage.removeItem("token");
            return;
        }
    }

    const userLogout = () => {
        userArchive.value = null;
        userBasic.value = null;
    }

    return {userArchive, userBasic, fetchUserInfo, userLogout}
});