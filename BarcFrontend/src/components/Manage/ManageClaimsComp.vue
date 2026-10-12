<script setup lang="ts">
/** 本人认领记录独立于作品是否仍公开，申请结案后仍能查看处理结果。 */
import {onMounted, ref} from "vue";
import {fetchMyClaims, claimStatusLabel, type ClaimProgress} from "@/utils/workClaimApi";
import {errorMessage} from "@/utils/MessageAlert";

const rows = ref<ClaimProgress[]>([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchMyClaims(page.value);
    rows.value = result.list;
    total.value = result.total;
  } catch (error) { errorMessage(error instanceof Error ? error.message : "加载认领记录失败"); }
  finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <section class="claim-progress">
    <h2>我的认领申请</h2>
    <el-table :data="rows" v-loading="loading" empty-text="暂无认领申请">
      <el-table-column prop="work_title" label="作品" min-width="180" />
      <el-table-column label="进度" min-width="180"><template #default="{row}">{{claimStatusLabel[row.status as ClaimProgress['status']]}}</template></el-table-column>
      <el-table-column prop="result_reason" label="处理结果" min-width="180" />
      <el-table-column prop="created_at" label="申请时间" width="180" />
    </el-table>
    <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev, pager, next, total" @current-change="load" />
  </section>
</template>

<style scoped>
.claim-progress { max-width: 1100px; margin: 24px auto; padding: 20px; }
</style>
