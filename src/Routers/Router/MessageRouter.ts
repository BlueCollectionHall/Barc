import MessageView from "@/Views/MessageView.vue";
import MessageDefaultComp from "@/Components/Message/MessageDefaultComp.vue";

export default {
    path: "/message",
    component: MessageView,
    children: [
        {
            path: "",
            name: "Message",
            component: MessageDefaultComp,
        }
    ]
}