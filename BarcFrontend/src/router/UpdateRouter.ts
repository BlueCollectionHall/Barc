import UpdateView from "@/views/UpdateView.vue";
import UpdateAccountComp from "@/components/Update/UpdateAccountComp.vue";
import UpdateAccountBasicComp from "@/components/Update/UpdateAccountBasicComp.vue";
import UpdateAccountArchiveComp from "@/components/Update/UpdateAccountArchiveComp.vue";
import UpdateAccountPasswordComp from "@/components/Update/UpdateAccountPasswordComp.vue";

export default {
  path: "/update",
  component: UpdateView,
  children: [
    {
      path: "account",
      name: "UpdateAccount",
      component: UpdateAccountComp,
      children: [
        {
          path: "password",
          name: "UpdateAccountPassword",
          component: UpdateAccountPasswordComp,
        },
        {
          path: "basic",
          name: "UpdateAccountBasic",
          component: UpdateAccountBasicComp,
        },
        {
          path: "archive",
          name: "UpdateAccountArchive",
          component: UpdateAccountArchiveComp
        }
      ]
    }
  ]
}
