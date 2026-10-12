import {defineStore} from "pinia";
import {onScopeDispose, ref} from "vue";
import type {UserArchiveImpl, UserBasicImpl} from "@/interfaces/UserImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";

export const useUserPinia = defineStore('user', () => {
  const userArchive = ref<UserArchiveImpl | null>(null);
  const userBasic = ref<UserBasicImpl | null>(null);

  /** 改密、改绑或认证过期后同时清理持久令牌与已显示的用户资料。 */
  const clearSession = () => {
    window.localStorage.removeItem('token');
    userArchive.value = null;
    userBasic.value = null;
  };
  if (typeof window !== 'undefined') {
    window.addEventListener('barc:auth-expired', clearSession);
    onScopeDispose(() => window.removeEventListener('barc:auth-expired', clearSession));
  }

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
      const dataArchive: ResponseImpl<UserArchiveImpl> = responseArchive.data;
      const dataBasic: ResponseImpl<UserBasicImpl> = responseBasic.data;
      if (dataArchive.code === 0 && dataBasic.code === 0) {
        userArchive.value = dataArchive.data;
        userBasic.value = dataBasic.data;
      }
      return;
    } catch {
      clearSession();
      return;
    }
  }

  return {userArchive, userBasic, fetchUserInfo, clearSession}
})
