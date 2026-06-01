<template>
  <div class="entity-breadcrumb">
    <el-button v-if="showBack" type="primary" text :icon="ArrowLeft" @click="goBack">返回</el-button>
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ name: 'schools-list' }">学园列表</el-breadcrumb-item>
      <el-breadcrumb-item v-if="schoolName" :to="{ name: 'clubs-list', params: { schoolId } }">{{ schoolName }}</el-breadcrumb-item>
      <el-breadcrumb-item v-if="clubName">{{ clubName }}</el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const props = defineProps<{
  schoolId?: string
  schoolName?: string
  clubName?: string
}>()

const router = useRouter()

const showBack = computed(() => !!props.schoolName)

function goBack() {
  if (props.clubName && props.schoolId) {
    router.push({ name: 'clubs-list', params: { schoolId: props.schoolId } })
  } else {
    router.push({ name: 'schools-list' })
  }
}
</script>

<style scoped>
.entity-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
