package com.miaoyu.barc.integration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.miaoyu.barc.api.file.service.ObjectReservationService;
import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.mapper.CosGcMapper;
import com.miaoyu.barc.api.scheduler.CosGcScheduler;
import com.miaoyu.barc.api.service.CosGcGuard;
import com.miaoyu.barc.api.service.ClubService;
import com.miaoyu.barc.api.service.CategoryManageService;
import com.miaoyu.barc.api.service.SchoolService;
import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.StorageObjectMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkClaimModel;
import com.miaoyu.barc.api.work.service.WorkClaimService;
import com.miaoyu.barc.api.work.service.WorkManageService;
import com.miaoyu.barc.email.service.MailOutboxScheduler;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.friendlink.service.FriendLinkService;
import com.miaoyu.barc.user.mapper.VerificationCodeMapper;
import com.miaoyu.barc.user.model.dto.SignupRequest;
import com.miaoyu.barc.user.scheduler.UserBanScheduler;
import com.miaoyu.barc.user.service.SignService;
import com.miaoyu.barc.utils.VerifyCodeCleanupService;
import com.miaoyu.barc.utils.tencent.cos.CosClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用真实 Spring 事务、AOP 与 MyBatis；仅替换外部邮件、COS 和后台定时任务。 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "BARC_TEST_MYSQL_CONTAINER", matches = "barc-api-audit-[a-f0-9]{12}")
class MysqlTransactionIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var db = new IsolatedMysql();
        properties.add("spring.datasource.url", () -> db.url() + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", db::password);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.flyway.baseline-on-migrate", () -> false);
        properties.add("tencent.cos.image.bucket-name", () -> "barc-audit-images");
    }
    @MockitoBean private CosGcScheduler gcScheduler;
    @MockitoBean private MailOutboxScheduler mailScheduler;
    @MockitoBean private UserBanScheduler userBanScheduler;
    @MockitoBean private VerifyCodeCleanupService codeCleanup;
    @MockitoBean private CosClient cosClient;
    @MockitoBean private JavaMailSender mailSender;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private VerificationCodeMapper codes;
    @Autowired private SignService signup;
    @Autowired private WorkClaimService claims;
    @Autowired private WorkManageService manage;
    @Autowired private WorkMapper works;
    @Autowired private FriendLinkService links;
    @Autowired private ObjectReservationService reservations;
    @Autowired private ObjectStorageService storage;
    @Autowired private CosGcGuard garbage;
    @Autowired private StorageObjectMapper objects;
    @Autowired private ClubMapper clubMapper;
    @Autowired private ClubService clubService;
    @Autowired private SchoolService schoolService;
    @Autowired private CategoryManageService categories;
    @Autowired private com.miaoyu.barc.api.work.service.WorkCategoryService workCategories;
    @Autowired private com.miaoyu.barc.api.work.service.WorkImageService workImages;
    @Autowired private com.miaoyu.barc.background.service.BackgroundImageService backgrounds;
    private String admin;
    private String owner;

    @BeforeEach void accounts() { admin = account("MANAGER", 63); owner = account("USER", 1); }

    @Test void verificationCanBeConsumedOnlyOnceConcurrently() throws Exception {
        String id = code("consume@example.test");
        List<Integer> changed = concurrently(4, () -> new TransactionTemplate(manager).execute(status ->
                codes.consume(id, "consume@example.test", "Signup", "123456")));
        assertEquals(1, changed.stream().mapToInt(Integer::intValue).sum());
    }

    @Test void accountWriteFailureRollsBackVerificationConsumption() {
        String email = id() + "@example.test"; String codeId = code(email);
        jdbc.execute("CREATE TRIGGER barc_test_account_failure BEFORE INSERT ON user_archive FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected archive failure'");
        try {
            assertThrows(RuntimeException.class, () -> signup.register(new SignupRequest("new_" + id().substring(0, 8), "StrongTestPass123", email, null, codeId, "123456")));
            assertFalse(jdbc.queryForObject("SELECT used FROM verification_code WHERE unique_id=?", Boolean.class, codeId));
            assertEquals(0, count("SELECT COUNT(*) FROM user_basic WHERE email=?", email));
        } finally { jdbc.execute("DROP TRIGGER barc_test_account_failure"); }
    }

    @Test void simultaneousDuplicateClaimReturnsOneApplication() throws Exception {
        String work = work("APPROVED");
        List<String> ids = concurrently(4, () -> ((WorkClaimModel) claims.uploadWorkClaimService(owner, request(work)).getBody().getData()).getId());
        assertEquals(1, new HashSet<>(ids).size());
        assertEquals(1, count("SELECT COUNT(*) FROM work_claim WHERE work_id=?", work));
    }

    @Test void concurrentApprovalsAssignOneAuthorAndPreserveBothHistories() throws Exception {
        String work = work("APPROVED"), applicant = account("USER", 1);
        String first = claim(work, owner), second = claim(work, applicant);
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { start.await(); return manage.approveClaim(admin, first, true).getBody().getCode(); });
            var b = executor.submit(() -> { start.await(); return manage.approveClaim(admin, second, true).getBody().getCode(); });
            start.countDown();
            assertEquals(1, List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).stream().filter(code -> code == 0).count());
            assertEquals(1, count("SELECT COUNT(*) FROM work_claim WHERE work_id=? AND status='APPROVED'", work));
            assertEquals(1, count("SELECT COUNT(*) FROM work_claim WHERE work_id=? AND status='SUPERSEDED'", work));
            assertEquals(1, count("SELECT COUNT(*) FROM work_operation_log WHERE work_id=?", work));
            assertEquals(1, count("SELECT COUNT(*) FROM mail_outbox WHERE event_key IN (?,?)", "claim:" + first, "claim:" + second));
        } finally { executor.shutdownNow(); }
    }

    @Test void auditFailureRollsBackClaimAttributionAndNotification() {
        String work = work("APPROVED"), claim = claim(work, owner);
        jdbc.execute("CREATE TRIGGER barc_test_audit_failure BEFORE INSERT ON work_operation_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected audit failure'");
        try {
            assertThrows(RuntimeException.class, () -> manage.approveClaim(admin, claim, true));
            assertClaimUnchanged(work, claim);
        } finally { jdbc.execute("DROP TRIGGER barc_test_audit_failure"); }
    }

    @Test void outboxFailureRollsBackClaimAndAudit() {
        String work = work("APPROVED"), claim = claim(work, owner);
        jdbc.execute("CREATE TRIGGER barc_test_outbox_failure BEFORE INSERT ON mail_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected outbox failure'");
        try {
            assertThrows(RuntimeException.class, () -> manage.approveClaim(admin, claim, true));
            assertClaimUnchanged(work, claim);
        } finally { jdbc.execute("DROP TRIGGER barc_test_outbox_failure"); }
    }

    @Test void contentRevisionRejectsStaleReviewAndAcceptsCurrentReview() {
        String work = work("PENDING");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertNotNull(works.selectByIdForUpdate(work)); assertTrue(works.touchContent(work));
        });
        assertNotEquals(0, manage.reviewWork(admin, work, true, null, 0L).getBody().getCode());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM work_review WHERE work_id=?", String.class, work));
        assertEquals(0, manage.reviewWork(admin, work, true, null, 1L).getBody().getCode());
        assertEquals(1, count("SELECT COUNT(*) FROM work_operation_log WHERE work_id=?", work));
    }

    @Test void concurrentCatalogCreationGetsDistinctSortPositions() throws Exception {
        List<Integer> results = concurrently(4, () -> {
            var item = new FriendLinkService.FriendLinkItem(); item.setTitle("测试友链"); item.setUrl("https://example.test/");
            return links.createService(admin, item).getBody().getCode();
        });
        assertTrue(results.stream().allMatch(code -> code == 0));
        assertEquals(0, count("SELECT COUNT(*) FROM (SELECT sort_order FROM friend_link WHERE deleted_at IS NULL GROUP BY sort_order HAVING COUNT(*)>1) repeated"));
    }

    @Test void uploadReservationSurvivesBusinessRollback() {
        String work = work("APPROVED"), key = "uploads/" + owner + "/" + id() + ".png";
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            reservations.reserve(owner, key); storage.bind(owner, key);
            jdbc.update("INSERT INTO work_cover_image(id,work_id,object_key) VALUES(?,?,?)", id(), work, key);
            throw new IllegalStateException("业务写入失败");
        }));
        assertEquals(0, count("SELECT COUNT(*) FROM work_cover_image WHERE work_id=?", work));
        assertEquals(1, count("SELECT COUNT(*) FROM storage_object WHERE object_key=?", key));
        assertEquals(1, count("SELECT COUNT(*) FROM cos_garbage_key WHERE object_key=?", key));
    }

    @Test void committedReferencePreventsGarbageClaimAndDeletingObjectRejectsBinding() {
        String work = work("APPROVED"), key = "uploads/" + owner + "/" + id() + ".png";
        reservations.reserve(owner, key);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            storage.bind(owner, key);
            jdbc.update("INSERT INTO work_cover_image(id,work_id,object_key) VALUES(?,?,?)", id(), work, key);
        });
        assertFalse(garbage.claim(garbageItem(key)));
        String unused = "uploads/" + owner + "/" + id() + ".png"; reservations.reserve(owner, unused);
        assertTrue(garbage.claim(garbageItem(unused)));
        assertThrows(ApiException.class, () -> storage.bind(owner, unused));
    }

    @Test void legacySignedAndEncodedUrlsStillProtectReferencedObjects() {
        String rawKey = "uploads/历史 图片/" + id() + ".png";
        String encoded = java.net.URLEncoder.encode(rawKey, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
        jdbc.update("UPDATE user_archive SET avatar=? WHERE uuid=?", "https://legacy.invalid/" + encoded + "?sign=old", owner);
        assertEquals(1, objects.references(rawKey));
        jdbc.update("UPDATE user_archive SET avatar=? WHERE uuid=?", "https://legacy.invalid/" + rawKey + "?sign=old", owner);
        assertEquals(1, objects.references(rawKey));
    }

    @Test void garbageWaitsForInFlightBindingAndRechecksCommittedReference() throws Exception {
        String work = work("APPROVED"), key = "uploads/" + owner + "/" + id() + ".png";
        reservations.reserve(owner, key); var item = garbageItem(key);
        var locked = new CountDownLatch(1); var commit = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var bind = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                storage.bind(owner, key);
                jdbc.update("INSERT INTO work_cover_image(id,work_id,object_key) VALUES(?,?,?)", id(), work, key);
                locked.countDown();
                try { assertTrue(commit.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            var collect = executor.submit(() -> garbage.claim(item));
            assertThrows(TimeoutException.class, () -> collect.get(150, TimeUnit.MILLISECONDS));
            commit.countDown(); bind.get(5, TimeUnit.SECONDS);
            assertFalse(collect.get(5, TimeUnit.SECONDS));
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT state FROM storage_object WHERE object_key=?", String.class, key));
        } finally { commit.countDown(); executor.shutdownNow(); }
    }

    @Test void clubMoveUsesActualRelationAndMovesStudentsWithAudit() {
        var ids = foundation();
        assertEquals(ids[0], clubMapper.selectById(ids[2]).getSchool());
        assertEquals(0, clubService.updateClub(admin, ids[2], clubForm(ids[2]), ids[1]).getBody().getCode());
        assertEquals(ids[1], jdbc.queryForObject("SELECT school FROM student WHERE id=?", String.class, ids[3]));
        assertEquals(ids[1], jdbc.queryForObject("SELECT school_id FROM school_club WHERE club_id=?", String.class, ids[2]));
        assertEquals(ids[0], jdbc.queryForObject("SELECT old_school FROM entity_relation_change WHERE club_id=?", String.class, ids[2]));
        assertTrue(jdbc.queryForObject("SELECT previous_students FROM entity_relation_change WHERE club_id=?", String.class, ids[2]).contains(ids[3]));
    }

    @Test void failedRelationAuditRollsBackSchoolAndStudentMove() {
        var ids = foundation();
        jdbc.execute("CREATE TRIGGER barc_test_relation_failure BEFORE INSERT ON entity_relation_change FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected relation failure'");
        try {
            assertThrows(RuntimeException.class, () -> clubService.updateClub(admin, ids[2], clubForm(ids[2]), ids[1]));
            assertEquals(ids[0], jdbc.queryForObject("SELECT school_id FROM school_club WHERE club_id=?", String.class, ids[2]));
            assertEquals(ids[0], jdbc.queryForObject("SELECT school FROM student WHERE id=?", String.class, ids[3]));
        } finally { jdbc.execute("DROP TRIGGER barc_test_relation_failure"); }
    }

    @Test void failedSchoolDeletionRollsBackChildrenAndGarbageQueue() {
        var ids = foundation();
        String key = "schools/" + ids[0] + "/logo.png";
        jdbc.update("UPDATE school SET logo=? WHERE id=?", key, ids[0]);
        jdbc.execute("CREATE TRIGGER barc_test_school_failure BEFORE UPDATE ON school FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected school failure'");
        try {
            assertThrows(RuntimeException.class, () -> schoolService.deleteSchool(admin, ids[0]));
            assertNull(jdbc.queryForObject("SELECT deleted_at FROM student WHERE id=?", java.sql.Timestamp.class, ids[3]));
            assertNull(jdbc.queryForObject("SELECT deleted_at FROM club WHERE id=?", java.sql.Timestamp.class, ids[2]));
            assertEquals(0, count("SELECT COUNT(*) FROM cos_garbage_key WHERE object_key=?", key));
        } finally { jdbc.execute("DROP TRIGGER barc_test_school_failure"); }
    }

    @Test void partialCategoryReorderFailureRollsBackEverySibling() {
        String parent = "p_" + id().substring(0, 8), first = parent + "a", second = parent + "b";
        jdbc.update("INSERT INTO category(id,name,level) VALUES(?,'排序测试',1)", parent);
        jdbc.update("INSERT INTO category(id,parent_id,name,level,sort) VALUES(?,?,'甲',2,4),(?,?,'乙',2,4)", first, parent, second, parent);
        // 只对本测试的第二个节点注入失败，确保第一行已经写过而后整体回滚。
        jdbc.execute("CREATE TRIGGER barc_test_sort_failure BEFORE UPDATE ON category FOR EACH ROW BEGIN IF NEW.id='" + second + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected sort failure'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> categories.moveSortService(admin, second, "up"));
            assertEquals(List.of(4, 4), jdbc.queryForList("SELECT sort FROM category WHERE parent_id=? ORDER BY id", Integer.class, parent));
        } finally { jdbc.execute("DROP TRIGGER barc_test_sort_failure"); }
    }

    /** 第一个事务删除后，等待父锁的第二次删除必须读到最新状态，不再重复提审或入回收队列。 */
    @Test void imageDeleteRechecksAfterAnotherTransactionCommits() throws Exception {
        String work = work("APPROVED"), image = id(), key = "uploads/" + owner + "/" + image + ".png";
        jdbc.update("INSERT INTO work_image(id,work_id,object_key,sort) VALUES(?,?,?,0)", image, work, key);
        int result = afterConcurrentDeletion(work, () -> jdbc.update("DELETE FROM work_image WHERE id=?", image),
                () -> workImages.deleteWorkImageService(owner, owner, image).getBody().getCode());
        assertNotEquals(0, result); assertEquals(0, count("SELECT content_revision FROM work WHERE id=?", work));
        assertEquals(0, count("SELECT COUNT(*) FROM cos_garbage_key WHERE object_key=?", key));
    }

    @Test void categoryDeleteRechecksAfterAnotherTransactionCommits() throws Exception {
        String work = work("APPROVED"), category = "c_" + id().substring(0, 8), binding = id();
        jdbc.update("INSERT INTO category(id,name,level) VALUES(?,'并发分类',1)", category);
        jdbc.update("INSERT INTO work_category(id,work_id,category_id) VALUES(?,?,?)", binding, work, category);
        int result = afterConcurrentDeletion(work, () -> jdbc.update("DELETE FROM work_category WHERE id=?", binding),
                () -> workCategories.deleteWorkCategoryService(owner, binding).getBody().getCode());
        assertNotEquals(0, result); assertEquals(0, count("SELECT content_revision FROM work WHERE id=?", work));
    }

    /** 等待展示配置锁期间被删除的节日，不能因鉴权建立的旧快照继续绑定；停用节日仍允许保留绑定。 */
    @Test void backgroundBindingSeesFestivalDeletedWhileWaitingForCatalogLock() throws Exception {
        String festival = id().replace("-", ""), background = id();
        jdbc.update("INSERT INTO festival(id,name,start_month,start_day,end_month,end_day,start_index,end_index) VALUES(?,'并发节日',1,1,1,1,33,33)", festival);
        jdbc.update("INSERT INTO background_image(id,module,object_key) VALUES(?,'home','background/fixture.png')", background);
        jdbc.update("INSERT IGNORE INTO catalog_mutation_lock(scope) VALUES('presentation')");
        int result = afterConcurrentChange("SELECT scope FROM catalog_mutation_lock WHERE scope=? FOR UPDATE", "presentation",
                () -> jdbc.update("UPDATE festival SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", festival),
                () -> backgrounds.updateSceneService(admin, background, null, festival).getBody().getCode());
        assertNotEquals(0, result);
        assertNull(jdbc.queryForObject("SELECT festival FROM background_image WHERE id=?", String.class, background));
    }

    /** 用真实 InnoDB 锁等待确认第二次读取已进入竞争窗口，避免只测试顺序重复请求。 */
    private int afterConcurrentDeletion(String work, Runnable remove, Supplier<Integer> secondDelete) throws Exception {
        return afterConcurrentChange("SELECT id FROM work WHERE id=? FOR UPDATE", work, remove, secondDelete);
    }

    private int afterConcurrentChange(String lockSql, String lockId, Runnable change, Supplier<Integer> secondOperation) throws Exception {
        var locked = new CountDownLatch(1); var commit = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.queryForObject(lockSql, String.class, lockId); locked.countDown();
                try { assertTrue(commit.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                change.run();
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS)); var second = executor.submit(secondOperation::get);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean waiting = false;
            while (System.nanoTime() < deadline) {
                if (count("SELECT COUNT(*) FROM performance_schema.data_lock_waits") > 0) { waiting = true; break; }
                Thread.sleep(20);
            }
            assertTrue(waiting, "第二个事务必须实际等待 InnoDB 行锁"); commit.countDown(); first.get(5, TimeUnit.SECONDS);
            return second.get(5, TimeUnit.SECONDS);
        } finally { commit.countDown(); executor.shutdownNow(); }
    }

    private String[] foundation() {
        String prefix = "f_" + id().substring(0, 8), oldSchool = prefix + "old", newSchool = prefix + "new", club = prefix + "club", student = prefix + "student";
        jdbc.update("INSERT INTO school(id,cn_name,en_name) VALUES(?,?,?),(?,?,?)", oldSchool, oldSchool, oldSchool, newSchool, newSchool, newSchool);
        jdbc.update("INSERT INTO club(id,cn_name,en_name) VALUES(?,?,?)", club, club, club);
        jdbc.update("INSERT INTO school_club(school_id,club_id) VALUES(?,?)", oldSchool, club);
        jdbc.update("INSERT INTO student(id,cn_name,en_name,avatar_rectangle,body_image,school,club) VALUES(?,?,?,'','',?,?)", student, student, student, oldSchool, club);
        return new String[]{oldSchool, newSchool, club, student};
    }
    private SchoolClubModel clubForm(String club) {
        var form = new SchoolClubModel(); form.setCn_name(club); form.setEn_name(club); return form;
    }

    private void assertClaimUnchanged(String work, String claim) {
        assertFalse(jdbc.queryForObject("SELECT is_claim FROM work WHERE id=?", Boolean.class, work));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM work_claim WHERE id=?", String.class, claim));
        assertEquals(0, count("SELECT COUNT(*) FROM work_operation_log WHERE work_id=?", work));
        assertEquals(0, count("SELECT COUNT(*) FROM mail_outbox WHERE event_key=?", "claim:" + claim));
    }
    private CosGcMapper.CosGcItem garbageItem(String key) {
        var item = new CosGcMapper.CosGcItem(); item.setObject_key(key); item.setBucket("barc-audit-images");
        item.setId(jdbc.queryForObject("SELECT id FROM cos_garbage_key WHERE object_key=?", Long.class, key)); return item;
    }
    private String account(String identity, int permission) {
        String uuid = id().replace("-", "");
        jdbc.update("INSERT INTO user_basic(uuid,username,password,email) VALUES(?,?,?,?)", uuid, uuid, "fixture", uuid + "@example.test");
        jdbc.update("INSERT INTO user_archive(uuid,nickname,avatar,identity,permission) VALUES(?,'测试用户','',?,?)", uuid, identity, permission); return uuid;
    }
    private String work(String review) {
        String id = id();
        jdbc.update("INSERT INTO work(id,title,banner_image,cover_image,author,uploader,student,status) VALUES(?,'测试作品','','',?,?,'student','PUBLIC')", id, admin, owner);
        jdbc.update("INSERT INTO work_review(work_id,status) VALUES(?,?)", id, review); return id;
    }
    private String code(String email) {
        String id = id(); jdbc.update("INSERT INTO verification_code(unique_id,code,username,scenario,expiration_at) VALUES(?,'123456',?,'Signup',DATE_ADD(NOW(),INTERVAL 10 MINUTE))", id, email); return id;
    }
    private String claim(String work, String applicant) { return ((WorkClaimModel) claims.uploadWorkClaimService(applicant, request(work)).getBody().getData()).getId(); }
    private WorkClaimModel request(String work) { var request = new WorkClaimModel(); request.setWork_id(work); request.setStatement("申请证据"); return request; }
    private int count(String sql, Object... values) { return jdbc.queryForObject(sql, Integer.class, values); }
    private static String id() { return UUID.randomUUID().toString(); }

    /** 闸门同时释放多个独立线程/连接，避免把顺序重复调用当成并发测试。 */
    private static <T> List<T> concurrently(int total, Supplier<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(total); var ready = new CountDownLatch(total); var start = new CountDownLatch(1);
        try {
            List<Future<T>> pending = new ArrayList<>();
            for (int i = 0; i < total; i++) pending.add(executor.submit(() -> { ready.countDown(); start.await(); return action.get(); }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            List<T> results = new ArrayList<>(); for (var future : pending) results.add(future.get(15, TimeUnit.SECONDS)); return results;
        } finally { executor.shutdownNow(); }
    }
}
