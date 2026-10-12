package com.miaoyu.barc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.api.file.service.*;
import com.miaoyu.barc.api.mapper.*;
import com.miaoyu.barc.api.model.*;
import com.miaoyu.barc.api.service.*;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖签名 URL 回写、软删除主键冲突、部团迁移及学生图片创建契约。 */
@ExtendWith(MockitoExtension.class)
class FoundationDataBoundaryTest {
    @Mock CatalogMutationMapper catalogMutationMapper;
    @Mock SchoolMapper schools;
    @Mock ClubMapper clubs;
    @Mock StudentMapper students;
    @Mock ImageUrlResolver urls;
    @Mock CosGcService garbage;
    @Mock ObjectStorageService storage;
    @Mock CosConfig config;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks SchoolService schoolService;
    @InjectMocks ClubService clubService;
    @InjectMocks StudentService studentService;

    @BeforeEach void replacementPolicy() {
        lenient().when(storage.replace(anyString(), nullable(String.class), nullable(String.class)))
                .thenAnswer(inv -> ObjectKeyPolicy.replacement(inv.getArgument(1), inv.getArgument(2)));
        lenient().when(urls.isKey(nullable(String.class))).thenAnswer(inv -> ObjectKeyPolicy.isKey(inv.getArgument(0)));
    }

    @Test void readSeparatesKeyAndUrlAndTextOnlyEditDoesNotEnqueueActiveImage() {
        var stored = school(); when(schools.selectById("abydos")).thenReturn(stored);
        when(urls.resolve("schools/abydos/logo/a.png")).thenReturn("https://cos.example/a?signature=x");
        var response = (SchoolModel) schoolService.getSchoolById("abydos").getBody().getData();
        assertEquals("schools/abydos/logo/a.png", response.getLogo_key()); assertTrue(response.getLogo().startsWith("https://"));
        when(schools.selectByIdForUpdate("abydos")).thenReturn(school()); when(schools.update(any())).thenReturn(1);
        response.setCn_name("新中文名"); schoolService.updateSchool("manager", "abydos", response);
        verify(schools).update(argThat(model -> "schools/abydos/logo/a.png".equals(model.getLogo())));
        verifyNoInteractions(garbage);
    }

    @Test void signedUrlCannotReplaceStoredKey() {
        var update = school(); update.setLogo("https://evil.example/a?sign=x"); when(schools.selectByIdForUpdate("abydos")).thenReturn(school());
        assertThrows(ApiException.class, () -> schoolService.updateSchool("manager", "abydos", update));
        verify(schools, never()).update(any()); verifyNoInteractions(garbage);
    }

    @Test void deletedIdRemainsOccupiedAndEnglishNameCannotBeSilentlyChanged() {
        when(schools.countIncludingDeleted("abydos")).thenReturn(1); when(clubs.countIncludingDeleted("old")).thenReturn(1); when(students.countIncludingDeleted("old")).thenReturn(1);
        assertEquals(false, schoolService.checkIdAvailable("abydos").getBody().getData());
        assertEquals(false, clubService.checkIdAvailable("old").getBody().getData()); assertEquals(false, studentService.checkIdAvailable("old").getBody().getData());
        assertEquals(1, schoolService.createSchool("manager", school()).getBody().getCode()); verify(schools, never()).insert(any());
        when(schools.selectByIdForUpdate("abydos")).thenReturn(school()); var renamed = school(); renamed.setEn_name("Different");
        assertThrows(ApiException.class, () -> schoolService.updateSchool("manager", "abydos", renamed));
    }

    @Test void movingClubRecordsPreviousStudentsAndUpdatesTheirSchool() {
        var old = club(); old.setSchool("old"); when(clubs.selectByIdForUpdate("club")).thenReturn(old);
        when(schools.selectByIdForUpdate("new")).thenReturn(school()); when(clubs.update(any())).thenReturn(1);
        when(clubs.studentsBeforeMove("club")).thenReturn(List.of(Map.of("id", "student", "school", "old")));
        when(clubs.upsertSchoolClub("new", "club")).thenReturn(1);
        when(clubs.recordMove(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        var update = club(); assertEquals(0, clubService.updateClub("manager", "club", update, "new").getBody().getCode());
        verify(clubs).recordMove(anyString(), eq("manager"), eq("club"), eq("old"), eq("new"), contains("student"));
        verify(clubs).moveStudents("club", "new"); assertEquals("new", update.getSchool());
    }

    @Test void newStudentCarriesRequiredImageKeysAndDerivesParentSchool() {
        var parent = club(); parent.setSchool("abydos"); when(clubs.selectByIdForUpdate("club")).thenReturn(parent);
        when(schools.selectByIdForUpdate("abydos")).thenReturn(school());
        var student = new StudentModel(); student.setCn_name("学生"); student.setEn_name("Student");
        assertThrows(ApiException.class, () -> studentService.createStudent("manager", student, "club"));
        student.setAvatar_rectangle_key("students/student/avatar_rectangle/a.png"); student.setBody_image_key("students/student/body_image/b.png");
        when(students.insert(any())).thenReturn(1); studentService.createStudent("manager", student, "club");
        verify(students).insert(argThat(model -> "abydos".equals(model.getSchool()) && "students/student/body_image/b.png".equals(model.getBody_image())));
    }

    private SchoolModel school() { var model = new SchoolModel(); model.setId("abydos"); model.setEn_name("Abydos"); model.setCn_name("阿拜多斯"); model.setLogo("schools/abydos/logo/a.png"); return model; }
    private SchoolClubModel club() { var model = new SchoolClubModel(); model.setId("club"); model.setCn_name("部团"); model.setEn_name("Club"); return model; }
}
