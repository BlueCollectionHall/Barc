export interface PageRequestImpl {
    page_num: number; // 当前页码
    page_size: number; // 每页条数
    sort_field?: string; // 排序字段
    sort_order?: string; // 排序方向
    params?: any; // 查询参数
}
export interface PageResultImpl<T> {
    page_num: number;
    page_size: number;
    total: number; // 总记录数
    total_page: number; // 总页面数
    list: Array<T>;
}