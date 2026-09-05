import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import { baseHttp } from "@/utils/https.ts";

type BackgroundMap = Record<string, string[]>;

let cache: BackgroundMap | null = null;

/** 拉取所有「启用」的背景图（按模块分组，已解析为 COS 签名 URL），带缓存 */
export async function fetchBackgrounds(): Promise<BackgroundMap> {
  if (cache) {
    return cache;
  }
  try {
    const response = await baseHttp.get("/api/background/all");
    const data: ResponseImpl = response.data;
    if (data.code === 0 && data.data && typeof data.data === "object") {
      cache = data.data as BackgroundMap;
      return cache;
    }
  } catch {
    // 网络异常时兜底为空，组件回退到写死图片
  }
  return {};
}

/** 获取某个模块的启用背景图 URL 列表 */
export async function fetchModuleBackgrounds(module: string): Promise<string[]> {
  const map = await fetchBackgrounds();
  return map[module] ?? [];
}
