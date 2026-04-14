import axios from 'axios';

export const baseHttp = axios.create(
    {
        baseURL: "https://api.barc.work",
        // baseURL: "http://localhost:51000",
        timeout: 10000
    }
);
