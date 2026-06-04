import axios from 'axios';

/** 默认 API 地址（兜底，防止 .env 缺失时崩溃） */
const DEFAULT_API_BASE_URL = 'https://api.barc.work';

export const baseHttp = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL,
  timeout: 10000,
});
