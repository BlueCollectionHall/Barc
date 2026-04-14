import CategoryView from "@/views/CategoryView.vue";
import WorkCategoryGeneralComp from "@/components/Work/Category/WorkCategoryGeneralComp.vue";

export default {
  path: "/category",
  name: "Category",
  component: CategoryView,
  children: [
    {
      path: ":category_id",
      // props: true,
      name: "CategoryOnly",
      component: WorkCategoryGeneralComp
    }
  ]
}
