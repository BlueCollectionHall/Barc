package com.miaoyu.barc.integration;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 在真实 MySQL 上校验空库、升级与独立快照，绝不向业务数据库执行迁移。 */
@EnabledIfEnvironmentVariable(named = "BARC_TEST_MYSQL_CONTAINER", matches = "barc-api-audit-[a-f0-9]{12}")
class MysqlMigrationIntegrationTest {
    @Test void deploymentPreflightRunsReadOnlyOnVersion13() {
        var db = new IsolatedMysql(); db.flyway("13").migrate();
        var script = new ResourceDatabasePopulator(new org.springframework.core.io.FileSystemResource("db/validation/api-audit-preflight.sql"));
        script.setSqlScriptEncoding("UTF-8"); script.execute(db.source());
        assertEquals(0, db.jdbc().queryForObject("SELECT COUNT(*) FROM work", Integer.class));
        assertEquals("13", db.jdbc().queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class));
    }

    @Test void emptyDatabaseMigratesAndValidates() {
        var db = new IsolatedMysql();
        db.flyway(null).migrate();
        db.flyway(null).validate();
        assertEquals("22", db.jdbc().queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(1, db.jdbc().queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='13' AND type='SQL_BASELINE'", Integer.class));
        assertEquals(0, db.jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('students','schools','clubs','school_clubs')", Integer.class));
    }

    @Test void version13DataSurvivesUpgradeAndPendingUniquenessIsPreserved() {
        var db = new IsolatedMysql(); db.flyway("13").migrate(); var jdbc = db.jdbc();
        jdbc.update("INSERT INTO user_basic(uuid,username,password,email) VALUES('u','u','fixture','u@example.test')");
        jdbc.update("INSERT INTO work(id,title,banner_image,cover_image,author,student,status) VALUES('w','旧作品','','','u','s','PUBLIC')");
        jdbc.update("INSERT INTO work_claim(id,work_id,applicant_uuid) VALUES('old','w','u')");
        jdbc.update("INSERT INTO category(id,name,level) VALUES('c','分类',1)");
        jdbc.update("INSERT INTO work_category(work_id,category_id) VALUES('w','c')");
        jdbc.update("INSERT INTO trend(id,title,content,author) VALUES('t','旧动态','正文','u')");
        jdbc.update("INSERT INTO trend_image(id,trend_id,image_url,image_name) VALUES('ti','t','https://legacy.invalid/a.png','a.png')");
        db.flyway(null).migrate(); db.flyway(null).validate();
        assertEquals("1", jdbc.queryForObject("SELECT id FROM work_category", String.class));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM work_claim WHERE id='old'", String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO work_claim(id,work_id,applicant_uuid) VALUES('duplicate','w','u')"));
        jdbc.update("UPDATE work_claim SET status='REJECTED' WHERE id='old'");
        jdbc.update("INSERT INTO work_claim(id,work_id,applicant_uuid) VALUES('new','w','u')");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM work_claim", Integer.class));
        assertEquals("https://legacy.invalid/a.png", jdbc.queryForObject("SELECT image_url FROM trend_image WHERE id='ti'", String.class));
        jdbc.update("INSERT INTO trend_image(id,trend_id,image_name,object_key) VALUES('new-image','t','b.png','trend/b.png')");
        jdbc.update("INSERT INTO feedback(id,type,content) VALUES('f','BUG','无目标反馈')");
    }

    @Test void standaloneSnapshotCanBeImportedTwice() {
        var db = new IsolatedMysql();
        var script = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        script.setSqlScriptEncoding("UTF-8"); script.setCommentPrefixes("--", "#");
        script.execute(db.source()); script.execute(db.source());
        assertEquals(1, db.jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_archive' AND column_name='identity'", Integer.class));
        assertEquals("longtext", db.jdbc().queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='notice' AND column_name='content'", String.class));
    }

    @Test void ambiguousLegacyStatusStopsBeforeContractChanges() {
        var db = new IsolatedMysql(); db.flyway("21").migrate(); var jdbc = db.jdbc();
        jdbc.update("INSERT INTO work(id,title,banner_image,cover_image,author,student,status) VALUES('ambiguous','旧状态','','','u','s','0')");
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> db.flyway(null).migrate());
        assertEquals("0", jdbc.queryForObject("SELECT status FROM work WHERE id='ambiguous'", String.class));
        assertEquals("bigint", jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='work_category' AND column_name='id'", String.class));
    }

    @Test void conflictingLegacyClubRelationsAreReportedWithoutDeletion() {
        var db = new IsolatedMysql(); db.flyway("21").migrate(); var jdbc = db.jdbc();
        jdbc.update("INSERT INTO school(id,cn_name,en_name) VALUES('s1','学园一','One'),('s2','学园二','Two')");
        jdbc.update("INSERT INTO club(id,cn_name,en_name) VALUES('c','部团','Club')");
        jdbc.update("INSERT INTO school_club(school_id,club_id) VALUES('s1','c'),('s2','c')");
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> db.flyway(null).migrate());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM school_club WHERE club_id='c'", Integer.class));
    }
}
