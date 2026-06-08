# 内容管理与作品编辑功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复个人中心内容管理页的旧封面链路，补齐前台作品编辑能力，并把搜索区升级为首页同语义但更适合管理场景的当前状态内筛选。

**Architecture:** 保持现有“文字更新”和“图片更新”分治思路：文字走 text-only 更新链路；封面与图集走独立图片链路；前台编辑页只复用上传页的视觉与交互，不直接耦合管理端 V2 UI。后端仅做最小增量修改，不改表结构，不破坏管理端既有契约。

**Tech Stack:** Vue 3 + Vite + Element Plus + Quill + CropperJS；Spring Boot + MyBatis + 腾讯 COS。

---

### Task 1: 修复内容管理列表封面链路

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkController.java`
- Test: `BarcBackend/src/test/java/**/work/**`

- [ ] 为 `works_by_uuid` / `works_by_me` 封面签名补写失败测试
- [ ] 运行后端测试，确认新测试先失败
- [ ] 在 `WorkService` 中让 owner 列表链路复用 `loopSignatureWorkCover`
- [ ] 再次运行测试，确认通过

### Task 2: 补 owner-safe 编辑详情读取与文字更新接口

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkController.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- Modify/Create: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/model/*.java`
- Test: `BarcBackend/src/test/java/**/work/**`

- [ ] 为 owner-safe 编辑详情 DTO/接口与 text-only 更新补写失败测试
- [ ] 运行后端测试，确认失败点正确
- [ ] 复用/抽取编辑详情组装逻辑，新增 owner-safe 接口
- [ ] 新增 owner-safe text-only 更新接口，避免误写 legacy `cover_image`
- [ ] 再次运行测试，确认通过

### Task 3: 补前台图片编辑能力所需最小接口

**Files:**
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkImageController.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/controller/WorkController.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkImageService.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/mapper/WorkCoverImageMapper.java`
- Test: `BarcBackend/src/test/java/**/work/**`

- [ ] 为封面替换、图集上传、图集删除 owner-safe 行为补写失败测试
- [ ] 运行后端测试，确认失败
- [ ] 实现封面替换接口与服务逻辑
- [ ] 补完 `POST /api/work/image/upload`，支持图集新增
- [ ] 确认图集删除链路具备 owner-safe 校验
- [ ] 再次运行测试，确认通过

### Task 4: 新增前台作品编辑页与路由

**Files:**
- Modify: `BarcFrontend/src/router/ManageRouter.ts`
- Modify: `BarcFrontend/src/components/Manage/ManageWorkComp.vue`
- Create: `BarcFrontend/src/components/Manage/ManageWorkEditComp.vue`
- Create/Modify: `BarcFrontend/src/interfaces/*.ts`
- Test: `BarcFrontend/tests/**`

- [ ] 为编辑按钮跳转、编辑页回填、分段保存逻辑补写失败测试
- [ ] 运行前台测试，确认失败
- [ ] 接通编辑路由与“编辑”按钮
- [ ] 实现上传页同风格编辑页：文本/封面/图集回填与脏区保存
- [ ] 再次运行测试，确认通过

### Task 5: 升级内容管理搜索区

**Files:**
- Modify: `BarcFrontend/src/components/Manage/ManageWorkComp.vue`
- Create: `BarcFrontend/src/components/Manage/ManageWorkSearchComp.vue`
- Modify: `BarcBackend/src/main/resources/mappers/work/WorkMapper.xml`
- Modify: `BarcBackend/src/main/java/com/miaoyu/barc/api/work/service/WorkService.java`
- Test: `BarcFrontend/tests/**`, `BarcBackend/src/test/java/**/work/**`

- [ ] 为“当前状态内筛选”补写失败测试
- [ ] 运行相关测试，确认失败
- [ ] 在 owner 列表接口上增加 keyword/school/club/student 可选筛选
- [ ] 实现前台轻量搜索区 UI，与首页语义对齐
- [ ] 再次运行测试，确认通过

### Task 6: 联调与回归验证

**Files:**
- Verify: `BarcBackend/`
- Verify: `BarcFrontend/`
- Verify: `BarcManageFrontendV2/`

- [ ] 运行 `mvn test`
- [ ] 运行 `npm run build`（BarcFrontend）
- [ ] 运行 `npm run build`（BarcManageFrontendV2）
- [ ] 验证内容管理列表、编辑页、公共详情页、管理端 V2 详情页的图片链路都正常
