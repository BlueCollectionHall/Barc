import UserView from "@/Views/UserView.vue";
import UserDefaultComp from "@/Components/User/UserDefaultComp.vue";
import UserPermissionComp from "@/Components/User/UserPermissionComp.vue";

export default {
    path: "/user",
    component: UserView,
    children: [
        {
            path: "",
            name: "User",
            component: UserDefaultComp,
        },
        {
            path: "permission",
            name: "UserPermission",
            component: UserPermissionComp,
        }
    ]
}