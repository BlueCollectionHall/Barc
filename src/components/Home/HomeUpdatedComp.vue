<script setup lang="ts">
import {BellOutlined} from '@ant-design/icons-vue'
import {onMounted, ref} from "vue";
import type {StudentImpl} from "@/interfaces/BaImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {useRouter} from "vue-router";
const router = useRouter();

const studentList = ref<Array<StudentImpl>>([]);

const fetchUpdatedStudents = async () => {
  try {
    const response = await baseHttp.get("/api/work/new", {params: {day: 30, is_student_list: true}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) studentList.value = data.data;
    else infoMessage(data.msg);
  } catch {
    errorMessage("网络出错！");
  }
}

const toStudent = (student_id: string) => {
  router.push({name: "Student", query: {student_id: student_id}});
}

onMounted(async () => {
  await fetchUpdatedStudents();
})
</script>

<template>
  <div class="updated_box">
    <div class="title">
      <BellOutlined/>
      最新收录作品对应的学生
    </div>
    <div class="students no_list" v-if="studentList.length === 0">
<!--      <FolderOpenOutlined />-->
      <img class="icon" src="https://static.kivo.wiki/images/gallery/E1.%E5%AE%98%E6%96%B9%E8%A1%A8%E6%83%85%E5%8C%85/Default/cafabb328c6564d3445ebaa00e1c510f.gif" alt="icon"/>
      <span class="text">近一个月内暂无最新收录作品对应的学生</span>
    </div>
    <div class="students" v-else>
      <div class="student" v-for="item in studentList" :key="item.id" @click="toStudent(item.id)">
        <img class="avatar" :src="item.avatar_rectangle" alt="student"/>
        <span class="name">{{item.cn_name}}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.updated_box {
  padding: 1rem;
  width: 100%;
  height: 30rem;
  background-color: #ffffff90;
  box-shadow: 0 0 .05rem .05rem rgb(0 0 0 / 5%);
  display: flex;
  flex-direction: column;
  overflow: auto;
}
.title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: .5rem;
  font-size: 1.2rem;
}
.students {
  margin-top: 1rem;
  flex: 1;
  gap: 2rem;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  .student:hover {
    background-color: #fff;
    cursor: pointer;
  }
  .student {
    transition: .3s ease;
    border: white .2rem solid;
    border-radius: .5rem;
    width: 96px;
    height: 128px;
    position: relative;
    .avatar {
      width: 100%;
    }
    .name {
      width: 100%;
      position: absolute;
      bottom: 0;
      left: 0;
      padding: .2rem;
      background-color: #ffffff90;
      font-weight: bold;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }
}
.no_list {
  gap: 1rem;
  flex-direction: column;
  .icon {
    width: 5rem;
  }
  .text {
    font-weight: bold;
    color: #56508c;
    letter-spacing: .1rem;
    font-size: 1.3rem;
  }
}
</style>
