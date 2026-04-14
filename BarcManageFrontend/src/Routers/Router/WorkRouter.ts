import WorkView from "@/Views/WorkView.vue";
import WorkDefaultComp from "@/Components/Work/WorkDefaultComp.vue";

export default {
    path: "/work",
    component: WorkView,
    children: [
        {
            path: "",
            name: "Work",
            component: WorkDefaultComp,
        }
    ]
}