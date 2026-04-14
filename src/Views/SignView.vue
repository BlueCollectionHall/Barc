<script setup lang="ts">

import SignBgComp from "@/Components/Sign/SignBgComp.vue";
import SignCardComp from "@/Components/Sign/SignCardComp.vue";
</script>

<template>
  <SignBgComp/>
  <div class="container">
    <SignCardComp/>
  </div>
</template>

<style scoped>
.container {
  position: relative;
  height: calc(100vh - 90px);
}
</style>