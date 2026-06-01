<template>
  <el-tag :type="tagType" size="small">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()

const labelMap: Record<string, string> = {
  PUBLIC: '公开', PRIVATE: '私有', OFF: '下架', BAN: '封禁', DELETED: '已删除',
}

const typeMap: Record<string, string> = {
  PUBLIC: 'success', PRIVATE: 'info', OFF: 'warning', BAN: 'danger', DELETED: 'danger',
}

const label = computed(() => labelMap[props.status] || props.status)
const tagType = computed(() => typeMap[props.status] || 'info')
</script>
