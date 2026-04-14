import UploadView from "@/views/UploadView.vue";
import UploadWorkComp from "@/components/Upload/UploadWorkComp.vue";

export default {
  path: "/upload",
  component: UploadView,
  // redirect: {name: "UploadWork"},
  children: [
    {
      path: "work",
      name: "UploadWork",
      component: UploadWorkComp,
    }
  ]
}
