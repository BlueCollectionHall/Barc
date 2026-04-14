<script setup lang="ts">
import "@/style/Background.css";
import {onMounted, ref} from "vue";
import type {StudentImpl} from "@/interfaces/BaImpl.ts";
import {useRoute} from "vue-router";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import HomeBgComp from "@/components/Home/HomeBgComp.vue";
const route = useRoute();

const student = ref<StudentImpl | null>(null);

const fetchStudent = async (studentId: string) => {
  try {
    const response = await baseHttp.get("/api/student/only", {params: {student_id: studentId}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      student.value = data.data;
    } else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误！");
  }
}

onMounted(async () => {
  const studentId: string = route.query.student_id as string;
  if (studentId) {
    await fetchStudent(studentId);
  }
})
</script>

<template>
  <div class="bg_box inside">
    <img v-if="student" class="body" :src="student.body_image" alt="bgi"/>
  </div>
  <HomeBgComp class="home_bg"/>
</template>

<style scoped>
@keyframes body_in {
  0% {
    opacity: 0;
    right: -10%;
  }
  100% {
    opacity: 1;
    right: 0;
  }
}
@keyframes home_bg_in {
  0% {
    opacity: 1;
  }
  100% {
    opacity: .3;
  }
}
.inside {
  height: 100vh;
  overflow: hidden;
  z-index: -2;
}
.body {
  transform: translate(0, 10rem);
  position: absolute;
  z-index: -1;
  height: 130%;
  animation: body_in .8s forwards ease;
}
.home_bg {
  animation: home_bg_in .8s forwards ease;
}
</style>
