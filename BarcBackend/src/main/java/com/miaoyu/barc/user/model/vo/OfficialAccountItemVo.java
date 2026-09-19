package com.miaoyu.barc.user.model.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端「官方账号」列表项：一条业务键 + 其绑定状态。
 *
 * 约定：列表返回**全部**业务项（含未绑定），未绑定项由 bound=false 标记，
 * 页面据此强提示「未配置」——因为未绑定会让对应业务的写链路被阻断。
 */
@Getter
@Setter
public class OfficialAccountItemVo {
    /** 业务键，取 OfficialAccountEnum.name()，前端提交绑定请求时原样回传 */
    private String business_key;
    /** 业务名（与绑定账号昵称无关） */
    private String display_name;
    /** 业务说明 */
    private String description;
    /** 是否已绑定：绑定行存在且绑定账号仍然存在 */
    private Boolean bound;
    /** 绑定行存在但账号已被删除（悬空绑定），页面需引导重新绑定 */
    private Boolean dangling;
    /** 绑定账号 UUID；未绑定时为 null */
    private String user_uuid;
    /** 绑定账号昵称 */
    private String nickname;
    /** 绑定账号用户名 */
    private String username;
    /** 绑定账号头像 */
    private String avatar;
    /** 绑定备注 */
    private String remark;
    /** 最后操作管理员昵称 */
    private String updated_by_nickname;
    /** 最后操作时间（yyyy-MM-dd HH:mm:ss）；未绑定时为空串 */
    private String updated_at;
}
