<script setup lang="ts">

import HeaderComp from "@/components/HeaderFooter/HeaderComp.vue";
import FooterComp from "@/components/HeaderFooter/FooterComp.vue";
import {ref} from "vue";

const htmlValue = ref<string>("<body><span>123321</span></body>");
</script>

<template>
  <HeaderComp/>
  <div class="container">
    <div v-html="htmlValue"/>
  </div>
  <FooterComp/>
</template>

<style scoped>

</style>
