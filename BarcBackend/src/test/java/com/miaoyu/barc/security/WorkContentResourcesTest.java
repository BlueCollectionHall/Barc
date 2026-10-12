package com.miaoyu.barc.security;

import com.miaoyu.barc.api.mapper.CategoryMapper;
import com.miaoyu.barc.api.pojo.CategoryPojo;
import com.miaoyu.barc.api.service.CategoryService;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.api.work.service.*;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 图片与分类共用父作品可见性和内容提审契约，不触碰真实对象存储。 */
@ExtendWith(MockitoExtension.class)
class WorkContentResourcesTest {
    @Mock WorkMapper works;
    @Mock WorkImageMapper images;
    @Mock WorkCategoryMapper bindings;
    @Mock CategoryMapper categories;
    @Mock WorkService review;
    @Mock UserArchiveMapper users;
    @Mock ObjectStorageService storage;
    @Mock CosGcService garbage;
    @Mock CosService cos;
    @Spy CosConfig config = new CosConfig();
    @InjectMocks WorkImageService imageService;
    @InjectMocks WorkCategoryService categoryService;
    @InjectMocks CategoryService catalog;
    final MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});

    @BeforeEach void bucket() { var bucket = new CosConfig.CosBucketPojo(); bucket.setBucketName("test-image"); config.setImage(bucket); }
    @Test void publicImagesAreSortedAfterSigningAndHiddenParentsAreRejected() {
        WorkModel work = work(); when(works.selectById("w")).thenReturn(work);
        when(images.selectByWorkId("w")).thenReturn(List.of(image("b", 2), image("a", 1)));
        when(cos.generateBatchSignedUrl(eq(List.of("images/b.png", "images/a.png")), any(), eq(CosBucketConfigEnum.image))).thenReturn(List.of("url-b", "url-a"));
        assertEquals(List.of("url-a", "url-b"), imageService.getImagesByWorkService("w").getBody().getData());
        work.setStatus(WorkStatusEnum.PRIVATE); assertNotEquals(0, imageService.getImagesByWorkService("w").getBody().getCode());
        work.setStatus(WorkStatusEnum.PUBLIC); work.setReview_status(WorkReviewStatusEnum.PENDING); assertNotEquals(0, imageService.getImagesByWorkService("w").getBody().getCode());
        assertNotEquals(0, imageService.getImagesByWorkService("missing").getBody().getCode()); verify(cos, times(1)).generateBatchSignedUrl(any(), any(), any());
    }

    @Test void imageUploadAllowsUploaderButNeverOutsidersOrDeletedWork() {
        when(works.selectByIdForUpdate("w")).thenReturn(work());
        assertNotEquals(0, imageService.uploadWorkImageService("outsider", "w", file).getBody().getCode());
        assertNotEquals(0, imageService.uploadWorkImageService("author", "missing", file).getBody().getCode());
        var deleted = work(); deleted.setStatus(WorkStatusEnum.DELETED); when(works.selectByIdForUpdate("w")).thenReturn(deleted);
        assertNotEquals(0, imageService.uploadWorkImageService("author", "w", file).getBody().getCode()); verifyNoInteractions(storage);
        when(works.selectByIdForUpdate("w")).thenReturn(work()); when(images.selectByWorkId("w")).thenReturn(List.of(image("first", 4)));
        when(storage.upload(eq("uploader"), eq(file), anyString(), anyLong())).thenReturn("new/key.png"); when(images.insert(any())).thenReturn(true);
        assertEquals(0, imageService.uploadWorkImageService("uploader", "w", file).getBody().getCode());
        verify(images).insert(argThat(row -> row.getSort() == 5 && row.getWork_id().equals("w") && row.getObject_key().equals("new/key.png"))); verify(review).submitForReview("w");
    }

    @Test void imageLimitStopsUploadAndInsertFailureDoesNotSubmitReview() {
        when(works.selectByIdForUpdate("w")).thenReturn(work()); when(images.selectByWorkId("w")).thenReturn(Collections.nCopies(20, image("same", 1)));
        assertNotEquals(0, imageService.uploadWorkImageService("author", "w", file).getBody().getCode()); verifyNoInteractions(storage);
        when(images.selectByWorkId("w")).thenReturn(List.of()); when(storage.upload(eq("author"), eq(file), anyString(), anyLong())).thenReturn("new/key.png");
        assertThrows(ApiException.class, () -> imageService.uploadWorkImageService("author", "w", file)); verifyNoInteractions(review);
    }

    @Test void imageDeleteRechecksParentRoleAndReordersRemainingImages() {
        when(images.selectById("remove")).thenReturn(image("remove", 1)); when(works.selectByIdForUpdate("w")).thenReturn(work());
        when(images.selectByIdForUpdate("remove")).thenReturn(image("remove", 1));
        assertNotEquals(0, imageService.deleteWorkImageService("outsider", "outsider", "remove").getBody().getCode()); verify(images, never()).delete(anyString());
        when(users.selectByUuid("manager")).thenReturn(PermissionBoundaryTest.user(UserIdentityEnum.MANAGER, 4)); when(images.delete("remove")).thenReturn(true);
        when(images.selectByWorkId("w")).thenReturn(List.of(image("before", 0), image("after", 2))); when(images.update(any())).thenReturn(true);
        assertEquals(0, imageService.deleteWorkImageService("manager", "forged-author", "remove").getBody().getCode());
        verify(images).update(argThat(row -> row.getId().equals("after") && row.getSort() == 1)); verify(images, never()).update(argThat(row -> row.getId().equals("before")));
        verify(garbage).enqueue("test-image", "images/remove.png"); verify(review).submitForReview("w");
    }

    @Test void removedImageAndReorderFailuresDoNotPretendSuccess() {
        assertNotEquals(0, imageService.deleteWorkImageService("author", "author", "missing").getBody().getCode());
        when(images.selectById("removed")).thenReturn(image("removed", 1)); when(works.selectByIdForUpdate("w")).thenReturn(work());
        assertNotEquals(0, imageService.deleteWorkImageService("author", "author", "removed").getBody().getCode()); verifyNoInteractions(garbage);
        when(images.selectById("keep")).thenReturn(image("keep", 1)); when(images.selectByIdForUpdate("keep")).thenReturn(image("keep", 1)); assertNotEquals(0, imageService.deleteWorkImageService("author", "author", "keep").getBody().getCode());
        when(images.delete("keep")).thenReturn(true); when(images.selectByWorkId("w")).thenReturn(List.of(image("after", 2)));
        assertThrows(ApiException.class, () -> imageService.deleteWorkImageService("author", "author", "keep")); verifyNoInteractions(review);
    }

    @Test void categoryInsertIgnoresClientIdAndMakesRepeatedBindingIdempotent() {
        when(works.selectByIdForUpdate("w")).thenReturn(work()); when(categories.selectById("c")).thenReturn(category("c", null, 1, 0)); when(bindings.insert(any())).thenReturn(true);
        var request = binding(); request.setId("forged"); assertEquals(0, categoryService.recordWorkAndCategoryService("uploader", request).getBody().getCode());
        verify(bindings).insert(argThat(row -> !row.getId().equals("forged") && row.getId().length() == 36 && row.getCategory_id().equals("c"))); verify(review).submitForReview("w");
        when(bindings.selectByWorkId("w")).thenReturn(List.of(binding())); categoryService.recordWorkAndCategoryService("author", request); verify(review, times(1)).submitForReview("w");
    }

    @Test void categoryInsertRejectsUnknownParentTargetOutsiderAndDatabaseFailure() {
        assertThrows(ApiException.class, () -> categoryService.recordWorkAndCategoryService("author", binding())); when(works.selectByIdForUpdate("w")).thenReturn(work());
        assertThrows(ApiException.class, () -> categoryService.recordWorkAndCategoryService("outsider", binding())); assertThrows(ApiException.class, () -> categoryService.recordWorkAndCategoryService(null, binding()));
        assertThrows(ApiException.class, () -> categoryService.recordWorkAndCategoryService("author", binding())); when(categories.selectById("c")).thenReturn(category("c", null, 1, 0));
        assertThrows(ApiException.class, () -> categoryService.recordWorkAndCategoryService("author", binding())); verifyNoInteractions(review);
    }

    @Test void publicCategoryReadRequiresApprovedPublicParent() {
        when(works.selectById("w")).thenReturn(work()); when(bindings.selectByWorkId("w")).thenReturn(List.of(binding()));
        assertEquals(0, categoryService.getCategoriesByWorkIdService("w").getBody().getCode()); var pending = work(); pending.setReview_status(WorkReviewStatusEnum.PENDING); when(works.selectById("w")).thenReturn(pending);
        assertNotEquals(0, categoryService.getCategoriesByWorkIdService("w").getBody().getCode()); assertNotEquals(0, categoryService.getCategoriesByWorkIdService("missing").getBody().getCode());
    }

    @Test void categoryDeleteLocksRealWorkRechecksRecordAndTriggersReviewOnlyOnSuccess() {
        assertNotEquals(0, categoryService.deleteWorkCategoryService("author", "missing").getBody().getCode()); when(bindings.selectById("binding")).thenReturn(binding()); when(works.selectByIdForUpdate("w")).thenReturn(work());
        when(bindings.selectByIdForUpdate("binding")).thenReturn(binding());
        assertThrows(ApiException.class, () -> categoryService.deleteWorkCategoryService("author", "binding")); verifyNoInteractions(review);
        when(bindings.delete("binding")).thenReturn(true); assertEquals(0, categoryService.deleteWorkCategoryService("uploader", "binding").getBody().getCode()); verify(review).submitForReview("w");
        when(bindings.selectById("raced")).thenReturn(binding()); assertNotEquals(0, categoryService.deleteWorkCategoryService("author", "raced").getBody().getCode());
    }

    @Test void publicCategoryTreeSortsEveryLevelAndCanSelectSubtree() {
        var root = category("root", null, 1, 2); var first = category("first", null, 1, 1); var late = category("late", "root", 2, 3); var early = category("early", "root", 2, 1);
        when(categories.selectAll()).thenReturn(new ArrayList<>(List.of(root, late, first, early, category("orphan", "missing", 2, 1))));
        @SuppressWarnings("unchecked") var tree = (List<CategoryPojo>) catalog.getCategoriesService(true, null).getBody().getData(); assertEquals(List.of("first", "root"), tree.stream().map(CategoryPojo::getId).toList()); assertEquals(List.of("early", "late"), root.getChildren().stream().map(CategoryPojo::getId).toList());
        root.setChildren(null); when(categories.selectAll()).thenReturn(new ArrayList<>(List.of(root, late, early))); assertEquals(List.of(root), catalog.getCategoriesService(false, "root").getBody().getData());
        when(categories.selectAllTop()).thenReturn(new ArrayList<>(List.of(root, first))); assertEquals(List.of(first, root), catalog.getAllTopCategoriesService().getBody().getData());
        assertEquals(0, catalog.getCategoriesAllNotGradedService().getBody().getCode());
    }

    private static WorkModel work() { var work = new WorkModel(); work.setId("w"); work.setAuthor("author"); work.setUploader("uploader"); work.setStatus(WorkStatusEnum.PUBLIC); work.setReview_status(WorkReviewStatusEnum.APPROVED); return work; }
    private static WorkImageModel image(String id, int sort) { var image = new WorkImageModel(); image.setId(id); image.setWork_id("w"); image.setObject_key("images/" + id + ".png"); image.setSort(sort); return image; }
    private static WorkCategoryModel binding() { var binding = new WorkCategoryModel(); binding.setId("binding"); binding.setWork_id("w"); binding.setCategory_id("c"); return binding; }
    private static CategoryPojo category(String id, String parent, int level, int sort) { var category = new CategoryPojo(); category.setId(id); category.setParent_id(parent); category.setLevel(level); category.setSort(sort); return category; }
}
