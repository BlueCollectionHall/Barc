package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.file.service.ObjectKeyPolicy;
import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.mapper.*;
import com.miaoyu.barc.api.model.*;
import com.miaoyu.barc.api.service.*;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 基础实体完整生命周期：查询签名、父关系派生、字段独立更新和延迟回收边界。事务回滚另由真实 MySQL 测试验证。 */
@ExtendWith(MockitoExtension.class)
class FoundationLifecycleTest {
    @Mock CatalogMutationMapper locks;
    @Mock SchoolMapper schools;
    @Mock ClubMapper clubs;
    @Mock StudentMapper students;
    @Mock ImageUrlResolver urls;
    @Mock CosGcService garbage;
    @Mock ObjectStorageService storage;
    @Spy CosConfig config = new CosConfig();
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks SchoolService schoolService;
    @InjectMocks ClubService clubService;
    @InjectMocks StudentService studentService;

    @BeforeEach void storagePolicy() {
        var image = new CosConfig.CosBucketPojo(); image.setBucketName("test-image"); config.setImage(image);
        lenient().when(storage.replace(anyString(), nullable(String.class), nullable(String.class)))
                .thenAnswer(call -> ObjectKeyPolicy.replacement(call.getArgument(1), call.getArgument(2)));
        lenient().when(urls.resolve(nullable(String.class))).thenAnswer(call -> call.getArgument(0) == null ? null : "https://signed.test/" + call.getArgument(0));
        lenient().when(urls.isKey(nullable(String.class))).thenAnswer(call -> ObjectKeyPolicy.isKey(call.getArgument(0)));
    }

    @Test void studentReadsExposeKeyAndSignedPreviewAcrossAllSelectors() {
        when(students.selectAll()).thenReturn(List.of(student())); when(students.selectBySchool("school")).thenReturn(List.of(student()));
        when(students.selectByClub("club")).thenReturn(List.of(student())); when(students.selectById("student")).thenReturn(student());
        for (var response : List.of(studentService.getAllStudentsService(), studentService.getStudentsBySchoolService("school"), studentService.getStudentsByClubIdService("club"))) {
            var row = (StudentModel) ((List<?>) response.getBody().getData()).get(0);
            assertEquals("students/student/rectangle.png", row.getAvatar_rectangle_key()); assertEquals("https://signed.test/students/student/rectangle.png", row.getAvatar_rectangle());
            assertEquals(row.getBody_image_url(), row.getBody_image());
        }
        assertEquals(0, studentService.getStudentByIdService("student").getBody().getCode());
        assertNotEquals(0, studentService.getStudentByIdService("absent").getBody().getCode());
    }

    @Test void allFoundationPagesClampSizeTrimKeywordAndKeepTotal() {
        when(students.selectAllByPage("白子", 100, 100)).thenReturn(List.of(student())); when(students.countAllByKeyword("白子")).thenReturn(120L);
        Map<?, ?> page = (Map<?, ?>) studentService.getStudentsByPage(null, " 白子 ", 2, 999).getBody().getData(); assertEquals(120L, page.get("total"));
        when(students.selectByPage("club", null, 0, 10)).thenReturn(List.of(student())); studentService.getStudentsByPage("club", " ", -1, 0); verify(students).countByKeyword("club", null);
        when(clubs.selectAllByPage("部团", 0, 100)).thenReturn(List.of(club())); clubService.getClubsByPage("", " 部团 ", 1, 1000); verify(clubs).countAllByKeyword("部团");
        when(clubs.selectByPage("school", null, 10, 10)).thenReturn(List.of(club())); clubService.getClubsByPage("school", null, 2, 10); verify(clubs).countByKeyword("school", null);
        when(schools.selectByPage(null, 0, 10)).thenReturn(List.of(school())); schoolService.getSchoolsByPage(null, 1, 10);
        assertThrows(ApiException.class, () -> studentService.getStudentsByPage(null, null, Integer.MAX_VALUE, 100));
        assertThrows(ApiException.class, () -> clubService.getClubsByPage(null, "x".repeat(101), 1, 10));
    }

    @Test void parentReadsSeparateKeysAndUrlsAndMissingEntityFails() {
        when(schools.selectAll()).thenReturn(List.of(school())); assertEquals(0, schoolService.getAllSchoolsService().getBody().getCode());
        when(clubs.selectAll()).thenReturn(List.of(club())); when(clubs.selectBySchool("school")).thenReturn(List.of(club())); when(clubs.selectById("club")).thenReturn(club());
        for (var response : List.of(clubService.getAllClubsService(), clubService.getClubsBySchoolService("school"))) {
            var row = (SchoolClubModel) ((List<?>) response.getBody().getData()).get(0); assertEquals("clubs/club/logo.png", row.getLogo_key()); assertEquals(row.getLogo_url(), row.getLogo());
        }
        assertEquals(0, clubService.getClubByIdService("club").getBody().getCode()); assertNotEquals(0, clubService.getClubByIdService("absent").getBody().getCode());
        assertNotEquals(0, schoolService.getSchoolById("absent").getBody().getCode());
        assertEquals(true, studentService.checkIdAvailable("new").getBody().getData()); assertEquals(true, clubService.checkIdAvailable("new").getBody().getData()); assertEquals(true, schoolService.checkIdAvailable("new").getBody().getData());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"不合法", "_bad", "bad/parent"})
    void creationRejectsInvalidEnglishIdentifiersBeforeWriting(String english) {
        var s = student(); s.setEn_name(english); assertEquals(1, studentService.createStudent("manager", s, "club").getBody().getCode());
        var c = club(); c.setEn_name(english); assertEquals(1, clubService.createClub("manager", c, "school").getBody().getCode());
        var parent = school(); parent.setEn_name(english); assertEquals(1, schoolService.createSchool("manager", parent).getBody().getCode()); verifyNoInteractions(storage);
    }

    @Test void createStudentRejectsMissingParentAndOccupiedId() {
        assertEquals(1, studentService.createStudent("manager", student(), null).getBody().getCode());
        assertEquals(1, studentService.createStudent("manager", student(), "missing").getBody().getCode());
        when(students.countIncludingDeleted("student")).thenReturn(1); assertEquals(1, studentService.createStudent("manager", student(), "club").getBody().getCode());
        when(students.countIncludingDeleted("student")).thenReturn(0); when(clubs.selectByIdForUpdate("club")).thenReturn(club());
        assertEquals(1, studentService.createStudent("manager", student(), "club").getBody().getCode()); verifyNoInteractions(storage);
    }

    @Test void studentInsertDerivesBothParentsAndAnyWriteFailureIsExceptional() {
        when(clubs.selectByIdForUpdate("club")).thenReturn(club()); when(schools.selectByIdForUpdate("school")).thenReturn(school());
        var row = student(); row.setEn_name("New Student"); row.setSchool("forged"); when(students.insert(any())).thenReturn(1);
        assertEquals(0, studentService.createStudent("manager", row, "club").getBody().getCode()); assertEquals("new_student", row.getId()); assertEquals("school", row.getSchool());
        verify(students).insert(argThat(value -> "students/student/rectangle.png".equals(value.getAvatar_rectangle())));
        when(students.insert(any())).thenReturn(0); assertThrows(ApiException.class, () -> studentService.createStudent("manager", student(), "club"));
    }

    @Test void studentTextUpdateCannotReplaceImagesOrForgeRelations() {
        when(students.selectByIdForUpdate("student")).thenReturn(student()); when(students.update(any())).thenReturn(1);
        var update = student(); update.setId("forged"); update.setSchool("fake-school"); update.setClub("fake-club"); update.setBody_image("evil"); update.setEn_name(null);
        assertEquals(0, studentService.updateStudent("manager", "student", update, null).getBody().getCode());
        assertEquals("student", update.getId()); assertEquals("club", update.getClub()); assertEquals("school", update.getSchool()); assertEquals("students/student/body.png", update.getBody_image()); assertEquals("Student", update.getEn_name());
        assertEquals(1, studentService.updateStudent("manager", "missing", student(), null).getBody().getCode());
        when(students.update(any())).thenReturn(0); assertThrows(ApiException.class, () -> studentService.updateStudent("manager", "student", student(), null));
    }

    @Test void studentMoveUsesLockedParentAndRejectsMissingDestination() {
        when(students.selectByIdForUpdate("student")).thenReturn(student()); assertEquals(1, studentService.updateStudent("manager", "student", student(), "missing").getBody().getCode());
        var parent = club(); parent.setSchool("new-school"); when(clubs.selectByIdForUpdate("new-club")).thenReturn(parent);
        assertEquals(1, studentService.updateStudent("manager", "student", student(), "new-club").getBody().getCode());
        when(schools.selectByIdForUpdate("new-school")).thenReturn(school()); when(students.update(any())).thenReturn(1);
        var update = student(); studentService.updateStudent("manager", "student", update, "new-club"); assertEquals("new-club", update.getClub()); assertEquals("new-school", update.getSchool());
    }

    @Test void imageChangesEnqueueOnlyPreviousKeysAndMandatoryImagesCannotBeCleared() {
        when(students.selectByIdForUpdate("student")).thenReturn(student()); when(students.updateAvatarSquare(anyString(), nullable(String.class))).thenReturn(1);
        when(students.updateAvatarRectangle(anyString(), anyString())).thenReturn(1); when(students.updateBodyImage(anyString(), anyString())).thenReturn(1);
        studentService.updateAvatarSquare("manager", "student", "new/square.png"); studentService.updateAvatarRectangle("manager", "student", "new/rectangle.png"); studentService.updateBodyImage("manager", "student", "new/body.png");
        verify(garbage).enqueue("test-image", "students/student/square.png"); verify(garbage).enqueue("test-image", "students/student/rectangle.png"); verify(garbage).enqueue("test-image", "students/student/body.png");
        clearInvocations(garbage); studentService.updateAvatarSquare("manager", "student", "students/student/square.png"); verifyNoInteractions(garbage);
        assertThrows(ApiException.class, () -> studentService.updateAvatarRectangle("manager", "student", "")); assertThrows(ApiException.class, () -> studentService.updateBodyImage("manager", "student", " ")); verifyNoInteractions(garbage);
        when(students.updateBodyImage(anyString(), anyString())).thenReturn(0); assertThrows(ApiException.class, () -> studentService.updateBodyImage("manager", "student", "new/body.png"));
    }

    @Test void missingImagesAndStudentsNeverEnqueueOrWrite() {
        assertEquals(1, studentService.updateAvatarSquare("manager", "absent", "image").getBody().getCode());
        assertEquals(1, studentService.updateAvatarRectangle("manager", "absent", "image").getBody().getCode());
        assertEquals(1, studentService.updateBodyImage("manager", "absent", "image").getBody().getCode());
        assertEquals(1, studentService.deleteStudent("manager", "absent").getBody().getCode()); verifyNoInteractions(garbage, storage);
    }

    @Test void studentSoftDeleteQueuesImagesAndFailedDeleteThrows() {
        when(students.selectByIdForUpdate("student")).thenReturn(student()); when(students.softDelete("student")).thenReturn(1);
        assertEquals(0, studentService.deleteStudent("manager", "student").getBody().getCode()); verify(garbage, times(3)).enqueue(eq("test-image"), anyString());
        when(students.softDelete("student")).thenReturn(0); assertThrows(ApiException.class, () -> studentService.deleteStudent("manager", "student"));
    }

    @Test void clubCreationValidatesParentAndRequiresRelationshipWrite() {
        assertEquals(1, clubService.createClub("manager", club(), null).getBody().getCode()); assertEquals(1, clubService.createClub("manager", club(), "school").getBody().getCode());
        when(clubs.countIncludingDeleted("club")).thenReturn(1); assertEquals(1, clubService.createClub("manager", club(), "school").getBody().getCode());
        when(clubs.countIncludingDeleted("club")).thenReturn(0); when(schools.selectByIdForUpdate("school")).thenReturn(school()); when(clubs.insert(any())).thenReturn(1); when(clubs.upsertSchoolClub("school", "club")).thenReturn(1);
        assertEquals(0, clubService.createClub("manager", club(), "school").getBody().getCode());
        when(clubs.upsertSchoolClub("school", "club")).thenReturn(0); assertThrows(ApiException.class, () -> clubService.createClub("manager", club(), "school"));
        when(clubs.insert(any())).thenReturn(0); assertThrows(ApiException.class, () -> clubService.createClub("manager", club(), "school"));
    }

    @Test void clubTextCannotForgeSchoolAndMissingMoveDoesNotPersist() {
        when(clubs.selectByIdForUpdate("club")).thenReturn(club()); when(clubs.update(any())).thenReturn(1);
        var update = club(); update.setSchool("forged"); update.setLogo("new/logo.png"); update.setBg("new/bg.png");
        clubService.updateClub("manager", "club", update, null); assertEquals("school", update.getSchool()); verify(clubs, never()).upsertSchoolClub(anyString(), anyString()); verify(garbage, times(2)).enqueue(eq("test-image"), anyString());
        assertEquals(1, clubService.updateClub("manager", "club", club(), "missing").getBody().getCode()); assertEquals(1, clubService.updateClub("manager", "missing", club(), null).getBody().getCode());
        when(clubs.update(any())).thenReturn(0); assertThrows(ApiException.class, () -> clubService.updateClub("manager", "club", club(), null));
    }

    @Test void clubMovePreviewAndDeleteIncludeDependentStudents() {
        assertNotEquals(0, clubService.movePreview("manager", "club", "school").getBody().getCode()); when(clubs.selectById("club")).thenReturn(club()); when(schools.selectById("school")).thenReturn(school());
        when(clubs.studentsBeforeMove("club")).thenReturn(List.of(Map.of("id", "student", "school", "school"))); assertEquals(Map.of("affected_students", 1), clubService.movePreview("manager", "club", "school").getBody().getData());
        assertEquals(1, clubService.deleteClub("manager", "missing").getBody().getCode()); when(clubs.selectByIdForUpdate("club")).thenReturn(club()); when(clubs.studentImageKeys("club")).thenReturn(List.of("students/body.png", "https://legacy/image")); when(clubs.softDelete("club")).thenReturn(1);
        clubService.deleteClub("manager", "club"); verify(clubs).softDeleteStudentsByClub("club"); verify(garbage).enqueue("test-image", "students/body.png"); verify(garbage, never()).enqueue(anyString(), startsWith("https:"));
        when(clubs.softDelete("club")).thenReturn(0); assertThrows(ApiException.class, () -> clubService.deleteClub("manager", "club"));
    }

    @Test void schoolCreateUpdateAndDeletePreserveKeysAndRejectWriteFailure() {
        when(schools.insert(any())).thenReturn(1); assertEquals(0, schoolService.createSchool("manager", school()).getBody().getCode());
        when(schools.insert(any())).thenReturn(0); assertThrows(ApiException.class, () -> schoolService.createSchool("manager", school()));
        assertEquals(1, schoolService.updateSchool("manager", "missing", school()).getBody().getCode()); assertEquals(1, schoolService.deleteSchool("manager", "missing").getBody().getCode());
        when(schools.selectByIdForUpdate("school")).thenReturn(school()); when(schools.update(any())).thenReturn(1); var update = school(); update.setLogo_key("new/logo.png"); update.setBeautify_logo_key("new/pretty.png"); update.setBg_key("new/bg.png");
        schoolService.updateSchool("manager", "school", update); verify(garbage, times(3)).enqueue(eq("test-image"), startsWith("schools/"));
        when(schools.update(any())).thenReturn(0); assertThrows(ApiException.class, () -> schoolService.updateSchool("manager", "school", school()));
        when(schools.childImageKeys("school")).thenReturn(List.of("child/key.png")); when(schools.softDelete("school")).thenReturn(1); schoolService.deleteSchool("manager", "school");
        verify(schools).softDeleteStudentsBySchool("school"); verify(schools).softDeleteClubsBySchool("school"); verify(garbage).enqueue("test-image", "child/key.png");
        when(schools.softDelete("school")).thenReturn(0); assertThrows(ApiException.class, () -> schoolService.deleteSchool("manager", "school"));
    }

    private static StudentModel student() { var row = new StudentModel(); row.setId("student"); row.setCn_name("学生"); row.setEn_name("Student"); row.setSchool("school"); row.setClub("club"); row.setAvatar_square("students/student/square.png"); row.setAvatar_rectangle("students/student/rectangle.png"); row.setBody_image("students/student/body.png"); return row; }
    private static SchoolClubModel club() { var row = new SchoolClubModel(); row.setId("club"); row.setCn_name("部团"); row.setEn_name("Club"); row.setSchool("school"); row.setLogo("clubs/club/logo.png"); row.setBg("clubs/club/bg.png"); return row; }
    private static SchoolModel school() { var row = new SchoolModel(); row.setId("school"); row.setCn_name("学园"); row.setEn_name("School"); row.setLogo("schools/school/logo.png"); row.setBeautify_logo("schools/school/pretty.png"); row.setBg("schools/school/bg.png"); return row; }
}
