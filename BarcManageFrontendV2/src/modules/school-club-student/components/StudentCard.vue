<template>
  <div class="student-card" @click="handleCardClick">
    <img v-if="props.avatar" :src="props.avatar" class="avatar" />
    <div v-else class="avatar placeholder">头像</div>
    <div class="name">{{ props.name }}</div>
    <el-dropdown class="card-menu" trigger="click" @command="handleCommand" @click.stop>
      <span class="el-dropdown-link">
        <el-icon><More-Filled /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="edit">修改</el-dropdown-item>
          <el-dropdown-item command="delete" style="color: #f56c6c;">删除</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { MoreFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  id: string
  name: string
  avatar?: string
}>()

const emit = defineEmits<{
  (e: 'click', id: string): void
  (e: 'edit', id: string): void
  (e: 'delete', id: string): void
}>()

function handleCardClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (target.closest('.card-menu')) return
  emit('click', props.id)
}

function handleCommand(cmd: string) {
  if (cmd === 'edit') emit('edit', props.id)
  if (cmd === 'delete') emit('delete', props.id)
}
</script>

<style scoped>
.student-card {
  position: relative;
  width: 96px;
  height: 128px;
  border: 0.2rem solid white;
  border-radius: 0.5rem;
  overflow: hidden;
  cursor: pointer;
  transition: 0.3s ease;
}
.student-card:hover {
  background: white;
}
.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
}
.name {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 4px;
  background: linear-gradient(transparent, rgba(0,0,0,0.6));
  color: #fff;
  font-size: 12px;
  text-align: center;
}
.card-menu {
  position: absolute;
  top: 4px;
  right: 4px;
  color: #fff;
  background: rgba(0,0,0,0.3);
  border-radius: 4px;
  padding: 2px;
  cursor: pointer;
}
</style>
