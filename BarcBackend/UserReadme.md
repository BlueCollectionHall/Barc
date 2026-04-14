# User接口文档
## 通信接口
baseUrl: `/user`
### 注册相关接口
#### 注册请求
`POST:` `/sign_up`<br>
请求参：param： `email`、`code`、`unique_id`<br>
请求体：
```json
{
  "username": "",
  "password": "",
  "email": "",
  "telephone": ""
}
```
### 登录相关接口
#### 登录
`GET:` `/sign_in`<br>
请求参：param：`account`、`password`、`type`<br>
`type`：两种`"email"`和`"username"`，登录方式<br>
`account`：当`type`是`email`时account应该是邮箱地址、`username`时应该是用户名
#### 通过奶果Naigos服务登录（不可以无密码登录）
##### 使用奶果服务登录时，若没有蔚蓝收录馆账号时将自动创建账号，同时自动创建后蔚蓝收录馆账号初始密码为123456，若奶果档案没有设置邮箱，则为用户qq号@qq.com。
`GET:` `/sign_in_by_naigos`<br>
请求参：param：`account`、`password`、`type`<br>
`type`：两种`"email"`和`"uid"`，登录方式<br>
`account`：当`type`是`email`时account应该是邮箱地址、`uid`时应该是奶果档案ID
