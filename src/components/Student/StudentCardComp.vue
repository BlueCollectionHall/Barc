<script setup lang="ts">
import {onMounted, ref} from "vue";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import type {ClubImpl, SchoolImpl, StudentImpl} from "@/interfaces/BaImpl.ts";
import {useRoute} from "vue-router";
const route = useRoute();

const student = ref<StudentImpl | null>(null);
const school = ref<SchoolImpl | null>(null);
const club = ref<ClubImpl | null>(null);

const fetchStudent = async (studentId: string) => {
  try {
    const response = await baseHttp.get("/api/student/only", {params: {student_id: studentId}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      student.value = data.data;
      if (!student.value) {
        errorMessage("赋值失败！");
        return;
      }
      const responseSchool = await baseHttp.get("/api/school/only", {params: {school_id: student.value.school}})
      const dataSchool: ResponseImpl = responseSchool.data;
      if (dataSchool.code === 0) {
        school.value = dataSchool.data;
        if (!school.value) {
          errorMessage("赋值失败！");
          return;
        }
        const responseClub = await baseHttp.get("/api/club/only", {params: {club_id: student.value.club}});
        const dataClub: ResponseImpl = responseClub.data;
        if (dataClub.code === 0) {
          club.value = dataClub.data;
        } else infoMessage(dataClub.msg);
      }
      else infoMessage(dataSchool.msg);
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
  <div class="student_card_box" v-if="student">
    <img class="avatar" :src="student.avatar_rectangle" alt="avatar"/>
    <div class="info">
      <div class="name">
        <h1>{{student.cn_name}}</h1>
        <h2>{{student.en_name}}</h2>
      </div>
<!--      <hr/>-->
      <div class="school_box" v-if="school">
        <img v-if="school.logo" class="school_logo" :src="school.logo" alt="logo"/>
        <span>{{school.cn_name}}</span>
      </div>
      <div class="club_box" v-if="club">
        <img v-if="club.logo" class="school_logo" :src="club.logo" alt="logo"/>
        <span>{{club.cn_name}}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.student_card_box {
  width: 70%;
  height: 17rem;
  display: flex;
  gap: 1rem;
  margin: 0 auto;
  padding: 2rem 0;
}
.avatar {
  height: 15rem;
  border-radius: 1rem;
  border: rgba(17, 33, 52, .2) 4px solid;
  background-color: #ffffff80;
}
.info {
  flex: 1;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  hr {
    width: 100%;
    border: #9b9b9b20 2px solid;
  }
  .name {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 3rem;
  }
  .school_box {
    display: flex;
    align-items: center;
    font-size: 1.5rem;
    gap: .8rem;
    .school_logo {
      width: 3rem;
    }
  }
  .club_box {
    display: flex;
    align-items: center;
    font-size: 1.2rem;
    gap: .8rem;
    .school_logo {
      width: 3rem;
    }
  }
}

</style>
