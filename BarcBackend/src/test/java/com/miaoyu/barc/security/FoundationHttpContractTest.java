package com.miaoyu.barc.security;

import com.miaoyu.barc.api.controller.*;
import com.miaoyu.barc.api.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import static com.miaoyu.barc.security.HttpContractSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 基础资料 API 的路径、关系参数与图片 key 合同；防止表单迁移后静默丢失父关系。 */
class FoundationHttpContractTest {
    SchoolService schools = apiMock(SchoolService.class);
    ClubService clubs = apiMock(ClubService.class);
    StudentService students = apiMock(StudentService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var school = new SchoolController(); var club = new ClubController(); var student = new StudentController();
        ReflectionTestUtils.setField(school, "schoolService", schools); ReflectionTestUtils.setField(club, "clubService", clubs); ReflectionTestUtils.setField(student, "studentService", students); mvc = mvc(school, club, student);
    }
    @Test void dictionaryListsAndAvailabilityRoutesUseNamedParameters() throws Exception {
        ok(get("/api/school/list")); verify(schools).getSchoolsByPage("", 1, 20);
        ok(get("/api/club/list").param("school_id", "s").param("keyword", "词").param("page", "2").param("size", "30")); verify(clubs).getClubsByPage("s", "词", 2, 30);
        ok(get("/api/student/list").param("club_id", "c")); verify(students).getStudentsByPage("c", "", 1, 20);
        ok(get("/api/school/check_id_available").param("id", "s")); verify(schools).checkIdAvailable("s");
        ok(get("/api/club/check_id_available").param("id", "c")); verify(clubs).checkIdAvailable("c");
        ok(get("/api/student/check_id_available").param("id", "t")); verify(students).checkIdAvailable("t");
        ok(get("/api/school/s")); verify(schools).getSchoolById("s"); ok(get("/api/club/c")); verify(clubs).getClubByIdService("c"); ok(get("/api/student/t")); verify(students).getStudentByIdService("t");
    }
    @Test void schoolWritesUsePathIdAndAuthenticatedActor() throws Exception {
        ok(post("/api/school").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"s\",\"cn_name\":\"学园\",\"en_name\":\"school\"}")); verify(schools).createSchool(eq("actor"), argThat(model -> model.getEn_name().equals("school")));
        ok(put("/api/school/s").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"forged\",\"cn_name\":\"新学园\"}")); verify(schools).updateSchool(eq("actor"), eq("s"), argThat(model -> model.getCn_name().equals("新学园")));
        ok(delete("/api/school/s").requestAttr("uuid", "actor")); verify(schools).deleteSchool("actor", "s");
    }
    @Test void clubMoveExtractsParentFromBodyAsIndependentMutationParameter() throws Exception {
        ok(post("/api/club").requestAttr("uuid", "actor").param("school_id", "s").contentType(MediaType.APPLICATION_JSON).content("{\"en_name\":\"club\"}")); verify(clubs).createClub(eq("actor"), any(), eq("s"));
        ok(put("/api/club/c").requestAttr("uuid", "actor").contentType(MediaType.APPLICATION_JSON).content("{\"cn_name\":\"部团\",\"school\":\"new-school\"}")); verify(clubs).updateClub(eq("actor"), eq("c"), argThat(model -> model.getSchool() == null), eq("new-school"));
        ok(get("/api/club/c/move-preview").requestAttr("uuid", "actor").param("school_id", "new-school")); verify(clubs).movePreview("actor", "c", "new-school");
        ok(delete("/api/club/c").requestAttr("uuid", "actor")); verify(clubs).deleteClub("actor", "c");
    }
    @Test void studentMoveAndImageFieldsKeepIndependentCommands() throws Exception {
        ok(post("/api/student").requestAttr("uuid", "actor").param("club_id", "c").contentType(MediaType.APPLICATION_JSON).content("{\"en_name\":\"student\",\"avatar_rectangle\":\"uploads/a.png\",\"body_image\":\"uploads/b.png\"}")); verify(students).createStudent(eq("actor"), argThat(model -> model.getBody_image().equals("uploads/b.png")), eq("c"));
        ok(put("/api/student/t").requestAttr("uuid", "actor").param("club_id", "new-club").contentType(MediaType.APPLICATION_JSON).content("{\"cn_name\":\"学生\"}")); verify(students).updateStudent(eq("actor"), eq("t"), any(), eq("new-club"));
        ok(put("/api/student/t/avatar_square").requestAttr("uuid", "actor").param("value", "uploads/square.png")); verify(students).updateAvatarSquare("actor", "t", "uploads/square.png");
        ok(put("/api/student/t/avatar_rectangle").requestAttr("uuid", "actor").param("value", "uploads/rectangle.png")); verify(students).updateAvatarRectangle("actor", "t", "uploads/rectangle.png");
        ok(put("/api/student/t/body_image").requestAttr("uuid", "actor").param("value", "uploads/body.png")); verify(students).updateBodyImage("actor", "t", "uploads/body.png");
        ok(delete("/api/student/t").requestAttr("uuid", "actor")); verify(students).deleteStudent("actor", "t");
    }
    private void ok(RequestBuilder request) throws Exception { mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)); }
}
