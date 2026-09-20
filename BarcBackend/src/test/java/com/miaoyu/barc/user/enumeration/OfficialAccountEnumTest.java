package com.miaoyu.barc.user.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OfficialAccountEnumTest {

    @Test
    @DisplayName("业务键解析：合法值（含前后空格）必须解析成功")
    void fromName_ShouldResolveKnownBusinessKey() {
        assertEquals(OfficialAccountEnum.COLLECTION_ASSISTANT,
                OfficialAccountEnum.fromName("COLLECTION_ASSISTANT"));
        assertEquals(OfficialAccountEnum.COLLECTION_ASSISTANT,
                OfficialAccountEnum.fromName("  COLLECTION_ASSISTANT  "));
    }

    @Test
    @DisplayName("业务键解析：空值与非法值必须返回 null 由调用方拒绝")
    void fromName_ShouldReturnNullForBlankOrUnknown() {
        assertNull(OfficialAccountEnum.fromName(null));
        assertNull(OfficialAccountEnum.fromName(""));
        assertNull(OfficialAccountEnum.fromName("   "));
        assertNull(OfficialAccountEnum.fromName("NOT_A_BUSINESS"));
        // 大小写敏感：落库键必须与枚举 name() 完全一致
        assertNull(OfficialAccountEnum.fromName("collection_assistant"));
    }

    @Test
    @DisplayName("业务名与说明必须非空，且业务名与绑定账号昵称解耦")
    void displayMetadata_ShouldBePresent() {
        OfficialAccountEnum item = OfficialAccountEnum.COLLECTION_ASSISTANT;
        assertEquals("未认领收录作品临时归属", item.getDisplayName());
        assertEquals(true, item.getDescription() != null && !item.getDescription().isBlank());
        // 业务名不应写成某个具体账号的昵称
        assertEquals(false, item.getDisplayName().contains("蔚蓝收录认领助手"));
    }

    @Test
    @DisplayName("业务说明必须足够短，避免管理端「说明」列换行")
    void description_ShouldStayShortForSingleLineRendering() {
        // 管理端「说明」列宽约 400px，中文按 14px 估算单行约容纳 26 字；
        // 这里以 30 字为硬上限：一旦超限就会在表格里换行，破坏列表可读性。
        for (OfficialAccountEnum item : OfficialAccountEnum.values()) {
            assertEquals(true, item.getDescription().length() <= 30,
                    item.name() + " 的业务说明过长，会在管理端表格内换行：" + item.getDescription());
        }
    }
}
