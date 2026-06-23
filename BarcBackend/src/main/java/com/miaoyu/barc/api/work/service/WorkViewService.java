package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkViewLogMapper;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.WorkViewLogModel;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.utils.web.ClientIpInfo;
import com.miaoyu.barc.utils.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Slf4j
@Service
public class WorkViewService {
    private static final ZoneId VIEW_ZONE_ID = ZoneId.of("Asia/Shanghai");

    @Autowired
    private WorkViewLogMapper workViewLogMapper;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private Clock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public void recordViewIfNeeded(HttpServletRequest request, WorkModel work) {
        if (Objects.isNull(work) || isBlank(work.getId())) {
            return;
        }

        try {
            ViewIdentity identity = resolveIdentity(request, work);
            if (Objects.isNull(identity)) {
                return;
            }

            LocalDate viewDate = currentViewDate();
            WorkViewLogModel model = buildLogModel(work.getId().trim(), viewDate, identity);

            transactionTemplate.executeWithoutResult(status -> {
                workViewLogMapper.insert(model);
                int updated = workMapper.incrementViewCount(work.getId().trim());
                if (updated <= 0) {
                    throw new IllegalStateException("Failed to increment work view count");
                }
            });
        } catch (DuplicateKeyException ignored) {
            // same work + same day + same dedupe identity; skip silently
        } catch (Exception e) {
            log.error("Failed to record work view for work_id={}", work.getId(), e);
        }
    }

    private ViewIdentity resolveIdentity(HttpServletRequest request, WorkModel work) {
        String userUuid = resolveUserUuid(request);
        if (!isBlank(userUuid)) {
            String normalizedUserUuid = userUuid.trim();
            if (normalizedUserUuid.equals(work.getAuthor())) {
                return null;
            }
            return ViewIdentity.user(normalizedUserUuid);
        }

        ClientIpInfo ipInfo = clientIpResolver.resolve(request);
        if (Objects.isNull(ipInfo)
                || isBlank(ipInfo.viewerType())
                || isBlank(ipInfo.normalizedIp())
                || isBlank(ipInfo.ipHash())) {
            return null;
        }
        return ViewIdentity.ip(ipInfo.viewerType(), ipInfo.normalizedIp(), ipInfo.ipHash());
    }

    private String resolveUserUuid(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader("Authorization");
        if (isBlank(token)) {
            return null;
        }
        try {
            J jwtResult = jwtService.jwtParser(token);
            if (Objects.isNull(jwtResult) || jwtResult.getCode() != 0 || Objects.isNull(jwtResult.getData())) {
                return null;
            }
            return jwtResult.getData().toString();
        } catch (Exception e) {
            log.debug("Optional work view token parsing failed; treating request as anonymous.", e);
            return null;
        }
    }

    private LocalDate currentViewDate() {
        Instant now = clock.instant();
        return now.atZone(VIEW_ZONE_ID).toLocalDate();
    }

    private WorkViewLogModel buildLogModel(String workId, LocalDate viewDate, ViewIdentity identity) {
        WorkViewLogModel model = new WorkViewLogModel();
        model.setId(new GenerateUUID().getUuid36l());
        model.setWork_id(workId);
        model.setView_date(viewDate);
        model.setViewer_type(identity.viewerType());
        model.setViewer_user_uuid(identity.userUuid());
        model.setViewer_ipv4(identity.ipv4());
        model.setViewer_ipv6(identity.ipv6());
        model.setViewer_ip_hash(identity.ipHash());
        model.setDedupe_key(identity.dedupeKey());
        return model;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ViewIdentity(
            String viewerType,
            String userUuid,
            String ipv4,
            String ipv6,
            String ipHash,
            String dedupeKey
    ) {
        private static ViewIdentity user(String userUuid) {
            return new ViewIdentity("USER", userUuid, null, null, null, "USER:" + userUuid);
        }

        private static ViewIdentity ip(String viewerType, String normalizedIp, String ipHash) {
            String ipv4 = "IPV4".equals(viewerType) ? normalizedIp : null;
            String ipv6 = "IPV6".equals(viewerType) ? normalizedIp : null;
            return new ViewIdentity(viewerType, null, ipv4, ipv6, ipHash, viewerType + ":" + ipHash);
        }
    }
}
