import NoticeView from "@/views/NoticeView.vue";
import NoticeListComp from "@/components/Notice/NoticeListComp.vue";
import NoticeDetailComp from "@/components/Notice/NoticeDetailComp.vue";

export default {
  path: '/notice',
  name: 'Notice',
  component: NoticeView,
  redirect: {name: "NoticeList"},
  children: [
    {
      path: 'list',
      name: 'NoticeList',
      component: NoticeListComp
    },
    {
      path: 'detail',
      name: 'NoticeDetail',
      component: NoticeDetailComp
    }
  ]
}
