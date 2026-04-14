<script setup lang="ts">
import HomeMessageComp from "@/components/Home/HomeMessageComp.vue";
import HomeUpdatedComp from "@/components/Home/HomeUpdatedComp.vue";
import HomeJoinComp from "@/components/Home/HomeJoinComp.vue";
import HomeNoticeComp from "@/components/Home/HomeNoticeComp.vue";
import HomeArticleComp from "@/components/Home/HomeArticleComp.vue";
import HomeFriendshipComp
  from "@/components/Home/HomeFriendshipComp.vue";
import HomeSearchComp from "@/components/Home/HomeSearchComp.vue";
</script>

<template>
  <div class="container">
    <div class="c1">
      <div class="c1_left">
<!--        <HomeTimeComp/>-->
<!--        <HomeSelectItemComp/>-->
        <HomeSearchComp/>
        <HomeMessageComp/>
      </div>
      <div class="c1_right">
        <HomeUpdatedComp/>
<!--        <HomeJoinComp/>-->
      </div>
    </div>
    <div class="c2 c">
      <HomeNoticeComp/>
      <HomeArticleComp/>
    </div>
    <div class="c3 c">
      <HomeFriendshipComp/>
    </div>
  </div>
</template>

<style scoped>
.container {
  margin: 6rem auto 0 auto;
  max-width: calc(100vw - 10rem);
  min-height: calc(100vh - 70px - 6rem);
  display: flex;
  flex-direction: column;
}
.c1 {
  margin-top: 3rem;
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 2rem;
  .c1_right {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
}
.c {
  background-color: #ffffff80;
  padding: 1rem;
  margin-top: 1rem;
}
.c2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
}
</style>
