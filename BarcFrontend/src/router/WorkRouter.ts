import WorkView from "@/views/WorkView.vue";
import WorkGeneralComp from "@/components/Work/WorkGeneralComp.vue";
import WorkUserComp from "@/components/Work/WorkUserComp.vue";
import WorkDetailComp from "@/components/Work/WorkDetailComp.vue";

export default {
  path: "/work",
  component: WorkView,
  redirect: {name: "WorkAll"},
  children: [
    {
      path: "all",
      name: "WorkAll",
      component: WorkGeneralComp
    },
    {
      path: "school",
      name: "WorkSchool",
      component: WorkGeneralComp
    },
    {
      path: "club",
      name: "WorkClub",
      component: WorkGeneralComp
    },
    {
      path: "student",
      name: "WorkStudent",
      component: WorkGeneralComp
    },
    {
      path: "user",
      name: "WorkUser",
      component: WorkUserComp
    },
    {
      path: "detail",
      name: "WorkDetail",
      component: WorkDetailComp
    },
  ]
}
