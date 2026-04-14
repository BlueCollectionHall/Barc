# API接口文档
## 通信接口
baseUrl: `/api`
### 学园信息
#### 全部获取
`GET:` `/schools_all`
#### 获取唯一
`GET:` `/school_only` `param:school_id`
#### 上传修改
`POST:` `/up_school`<br>
请求头：
```json
{
  "Content-type": "application/json",
  "Authorization": "Token令牌"
}
```
请求体：`学园实体中全部参数`<br>
请求参：param：`{"up_type": "参数"}` `upload` `update`
### 部团信息
#### 全部获取
`GET:` `/clubs_all`
#### 通过学园id获取
`GET:` `/clubs_by_school` `param:school_id`
#### 获取唯一
`GET:` `/club_only` `param:club_id`
#### 上传修改
`POST:` `/up_club`<br>
请求头：
```json
{
  "Content-type": "application/json",
  "Authorization": "Token令牌"
}
```
请求体：`部团实体中全部参数`<br>
请求参：param：`{"up_type": "参数"}` `upload` `update`
### 学生信息
#### 全部获取
`GET:` `/students_all`
#### 通过学园id获取
`GET:` `/students_by_school` `param:school_id`
#### 通过部团id获取
`GET:` `/students_by_club` `param:club_id`
#### 获取唯一
`GET:` `/student_only` `param:student_id`
#### 上传修改
`POST:` `/up_student`<br>
请求头：
```json
{
  "Content-type": "application/json",
  "Authorization": "Token令牌"
}
```
请求体：`学生实体中全部参数`<br>
请求参：param：`{"up_type": "参数"}` `upload` `update`
### 作品信息
#### 全部获取
`GET:` `/works_all`
#### 通过层级分类id获取
`GET:` `/works_by_category` `param:category_id`
#### 通过学园id获取
`GET:` `/works_by_school` `param:school_id`
#### 通过部团id获取
`GET:` `/works_by_club` `param:club_id`
#### 通过学生id获取
`GET:` `/works_by_student` `param:student_id`
#### 获取唯一
`GET:` `/work_only` `param:work_id`
### 分类信息
#### 全部获取
`GET:` `/categories_all`
#### 通过任意一级分类id获取本级及其所有后代所有层级分类信息
`GET:` `/categories_by_category` `param:category_id`