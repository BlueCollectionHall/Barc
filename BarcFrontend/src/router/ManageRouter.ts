import ManageView from "@/views/ManageView.vue";
import ManageWorkComp from "@/components/Manage/ManageWorkComp.vue";

export default {
  path: "/manage",
  name: "Manage",
  component: ManageView,
  redirect: {name: "ManageWork"},
  children: [
    {
      path: "work",
      name: "ManageWork",
      component: ManageWorkComp
    }
  ]
}
