# 邮箱接口文档
## 通信接口
baseUrl: `/email`
### 获取注册验证码
`GET:` `/send/signup`<br>
请求参：param： `email`<br>
响应：
```json
{
  "code": 0,
  "msg": "邮件发送成功",
  "data": "<unique_id>"
}
```
### 获取重置密码验证码
`GET:` `/send/reset_password`<br>
请求参：param： `email`<br>
响应：
```json
{
  "code": 0,
  "msg": "邮件发送成功",
  "data": "<unique_id>"
}
```