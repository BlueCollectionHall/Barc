import HomeView from "@/Views/HomeView.vue";
import HomeDefaultComp from "@/Components/Home/HomeDefaultComp.vue";

export default {
    path: "/",
    component: HomeView,
    children: [
        {
            path: "",
            name: "Home",
            component: HomeDefaultComp
        }
    ]
}