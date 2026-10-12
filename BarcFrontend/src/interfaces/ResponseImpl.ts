/** 通用响应仅描述信封；调用方用泛型声明具体数据契约。 */
export interface ResponseImpl<T = unknown> {
  code: number;
  msg: string;
  data: T;
}
