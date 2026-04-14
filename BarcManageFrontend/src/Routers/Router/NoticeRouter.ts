import NoticeView from "@/Views/NoticeView.vue";
import NoticeListComp from "@/Components/Notice/NoticeListComp.vue";
import NoticeUploadComp from "@/Components/Notice/NoticeUploadComp.vue";
import NoticeEditComp from "@/Components/Notice/NoticeEditComp.vue";

export default {
    path: "/notice",
    name: "Notice",
    component: NoticeView,
    redirect: {name: "NoticeList"},
    children: [
        {
            path: "list",
            name: "NoticeList",
            component: NoticeListComp
        },
        {
            path: "upload",
            name: "NoticeUpload",
            component: NoticeUploadComp
        },
        {
            path: "edit",
            name: "NoticeEdit",
            component: NoticeEditComp
        }
    ]
}