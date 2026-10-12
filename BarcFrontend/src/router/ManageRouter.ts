import ManageView from "@/views/ManageView.vue";
import ManageWorkComp from "@/components/Manage/ManageWorkComp.vue";
import ManageWorkEditComp from "@/components/Manage/ManageWorkEditComp.vue";

export default {
  path: "/manage",
  name: "Manage",
  component: ManageView,
  redirect: {name: "ManageWork"},
  children: [
    // 本人申请记录独立保留，作品下架或认领完成后仍可查看。
    {path: "claims", name: "ManageClaims", component: () => import("@/components/Manage/ManageClaimsComp.vue")},
    {
      path: "work",
      name: "ManageWork",
      component: ManageWorkComp
    },
    {
      path: "work/edit",
      name: "ManageWorkEdit",
      component: ManageWorkEditComp
    }
  ]
}
