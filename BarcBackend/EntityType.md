# 实体数据结构文档
本文档将帮助前端开发人员，了解后端系统中，各对象实体的数据类型，以帮助前端开发人员在TypeScript网页开发时，合理设置interface接口类型。
## 介绍方式
本文档将使用MarkDown文档为载体。<br>
在数据类型后标有 `[null]` 的为可null类型；<br>
在数据类型后标有 `[important]` 的为不可缺少或为空的数据；<br>
在数据类型后标有 `[unique]` 的为唯一标识的数据；<br>
在数据类型后标有 `[default]` 的为后端自动生成，无需前端处理的数据；<br>
### 学园信息
```yaml
id: String [important] [unique] # 唯一ID
cn_name: String [important] [unique] # 中文名
jp_name: String [null] # 日文名
kr_name: String [null] # 韩文名
en_name: String [null] # 英文名
introduce: String [null] # 介绍
logo: String [null] # logo
beautify_logo: String [null] # 更好看的logo
bg: String [null] # 背景图
```
### 部团信息
```yaml
id: String [important] [unique] # 唯一ID
cn_name: String [important] [unique] # 中文名
jp_name: String [null] # 日文名
kr_name: String [null] # 韩文名
en_name: String [null] # 英文名
logo: String [null] # logo
bg: String [null] # 背景图
```
### 学生信息
```yaml
id: String [important] [unique] [default] # 唯一ID
cn_name: String [important] [unique] # 中文名
jp_name: String [null] # 日文名
kr_name: String [null] # 韩文名
en_name: String [important] [unique] # 英文名
introduce: String [null] # 介绍
avatar_square: String [null] # 方形头像
avatar_rectangle: String [important] # 竖长方形头像
body_image: String [important] # 立绘
school: String [important] # 所属学园
club: String [important] # 所属部团
```
### 分类信息
```yaml
id: String [important] [unique] # 唯一ID
parent_id: String [important] [null] # 上一代父级分类，最高级分类为null
name: String [important] [unique] # 分类名
level: String [important] # 分类层级
sort: String [default] # 排序顺序
is_enabled: String [important] # 是否启用
icon: String [null] # 图标
```
### 作品信息
```yaml
id: String [important] [unique] [default] # 唯一ID
title: String [important] # 标题
description: String [null] # 介绍
content: String [null] # 内容
banner_image: String [important] # Banner图
cover_image: String [important] # 封面图
view_count: int [null] [default] # 浏览量
like_count: int [null] [default] # 点赞量
author: String [null] # 上传者UUID
author_nickname: String [null] [半default] # 上传者昵称
source: String [important] # 来源
is_claim: boolean [null] [default] # 是否已被认领
status: int [default] # 状态 1正常 2封禁 3下架
student: String [important] # 作品归属学生
created_at: Date [default] # ISO8601国际时间标准
updated_at: Date [default] # ISO8601国际时间标准
```
### 验证码信息
```yaml
id: String [important] [unique] [default] # 唯一ID
code: String [important] [default] # 验证码
username: String [important] # 登录用的用户名
scenario: String [important] [default] # 用途
create_at: Date [default] # 创建时间 ISO8601国际时间标准
expiration_at: Data [important] [default] # 过期时间 ISO8601国际时间标准
used: boolean [important] [default] # 是否已经使用
```
### 用户基础信息
```yaml
uuid: String [important] [unique] [default] # 唯一UUID
username: String [important] [unique] # 登录用的用户名
password: String [important] # SHA256哈希加密的密码
password_version: int [default] # 密码加密版本
email: String [important] [unique] # 用户邮箱
email_verified: boolean [default] # 邮箱是否已经验证
telephone: String [null] # 手机号码
safe_level: int [default] # 安全等级
created_at: Date [default] # 创建时间 ISO8601国际时间标准
updated_at: Date [default] # 更新时间 ISO8601国际时间标准
```
### 用户档案信息
```yaml
uuid: String [important] [unique] [default] # 唯一UUID
nickname: String [important] # 用户昵称
avatar: String [null] # 用户头像
gender: int [null] # 性别
birthday: Date [null] # 生日 ISO8601国际时间标准
age: int [default] # 年龄
permission: int [default] # 权限等级
updated_at: Date [default] # 更新时间 ISO8601国际时间标准
```
