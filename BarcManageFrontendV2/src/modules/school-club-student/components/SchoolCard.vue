<template>
  <div class="entity-card school-card" @click="handleCardClick">
    <img v-if="props.logo" :src="props.logo" class="card-bg" />
    <div v-else class="card-bg placeholder">学园</div>
    <div class="card-name">{{ props.name }}</div>
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
  logo?: string
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
.school-card {
  position: relative;
  width: 150px;
  height: 150px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease;
}
.school-card:hover {
  transform: scale(1.02);
}
.card-bg {
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
  font-size: 14px;
}
.card-name {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px;
  background: linear-gradient(transparent, rgba(0,0,0,0.6));
  color: #fff;
  font-size: 14px;
  text-align: center;
}
.card-menu {
  position: absolute;
  top: 8px;
  right: 8px;
  color: #fff;
  background: rgba(0,0,0,0.3);
  border-radius: 4px;
  padding: 4px;
  cursor: pointer;
}
</style>
