<script setup lang="ts">

</script>

<template>
  <div class="container">
    <div class="title">
      <h1>期待您加入B.A.R.C.!</h1>
    </div>
    <div class="space_box">
      <div class="space">
        <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E5%9B%BD%E6%9C%8D%E7%B4%A0%E6%9D%90/%E4%BC%98%E9%A6%99_8.png" alt="icon"/>
        <div class="info">
          <div class="info_title">
            <h2>站内协助</h2>
            <p>在B.A.R.C.中担任各身份权限，成为收录者或管理者。</p>
          </div>
          <div class="introduce">
            <ul>
              <li>可以根据更换身份的方式成为创作者、收录者、风纪委员以及一、二、三级管理员，权限可以叠加共存，仅是解锁不同的站内权限，并非有上下级区分。</li>
              <li>通常无需手动更换为创作者、收录者，您上传和收录文章时，系统会自动为您解锁权限。</li>
              <li>对应方向
                <ul>
                  <li>风纪委员：作品认领、上传、收录审核</li>
                  <li>一级管理员：处理评论、留言投诉</li>
                  <li>二级管理员：处理作品投诉</li>
                  <li>三级管理员：处理用户投诉</li>
                </ul>
              </li>
            </ul>
          </div>
        </div>
        <a-button style="margin-left: auto" type="primary">填写表格</a-button>
      </div>
      <div class="space">
        <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E5%9B%BD%E6%9C%8D%E7%B4%A0%E6%9D%90/%E7%88%B1%E4%B8%BD%E4%B8%9D.png" alt="icon"/>
        <div class="info">
          <div class="info_title">
            <h2>加入开发</h2>
            <p>熟悉前后端与编程技能，成为B.A.R.C.的开发者。</p>
          </div>
          <div class="introduce">
            <ul>
              <li>在B.A.R.C中加入您的创意，或想亲自解决B.A.R.C中存在的技术问题</li>
              <li>目前还处于基础功能构建阶段，待基础功能完善后将成立开发组等待您的加入</li>
              <li>基础技术方向
                <ul>
                  <li>Vue3 + SpringBoot3 + TypeScript</li>
                  <li>MySQL、Redis、MinIO</li>
                </ul>
              </li>
              <li>还有更多技术等您带来B.A.R.C.</li>
            </ul>
          </div>
        </div>
        <a-button style="margin-left: auto" type="primary">填写表格</a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.container {
  padding: 2rem;
  width: 80%;
  background-color: #ffffff80;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin: 10rem auto 0;
  gap: 2rem;
}
.space_box {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
  justify-content: center;
}
.space:hover {
  border: 2px solid #fda5bc;
}
.space {
  transition: .8s ease;
  backdrop-filter: blur(2px);
  width: 100%;
  border: white solid 2px;
  display: flex;
  padding: 1rem;
  background-color: #ffffff80;
  align-items: center;
}
.icon {
  width: 10rem;
}
.info {
  display: flex;
  flex-direction: column;
  .info_title {
    font-size: 1.2rem;
  }
}
</style>
