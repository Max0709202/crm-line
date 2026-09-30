package com.crm.service;

import com.crm.dto.BroadcastForm;
import com.crm.dto.UserSearchForm;
import com.crm.entity.CrmUser;
import com.crm.entity.DiffDefinition;
import com.crm.entity.DiffSchedule;
import com.crm.entity.DiffScheduleStep;
import com.crm.entity.DiffStep;
import com.crm.repository.BroadcastRepository;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.DiffDefinitionRepository;
import com.crm.repository.DiffScheduleRepository;
import com.crm.repository.DiffScheduleStepRepository;
import com.crm.repository.DiffStepRepository;
import com.crm.repository.LineUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registers, dispatches, cancels, and reports on diff schedules. Registering a schedule
 * materialises one {@link DiffScheduleStep} per {@link DiffStep} in the chosen
 * {@link DiffDefinition}'s timeline, each independently scheduled/executed/cancellable —
 * "apply diff X's whole timeline to target Y, set at time Z".
 */
@Service
public class DiffScheduleService {

    private static final Logger log = LoggerFactory.getLogger(DiffScheduleService.class);

    private final DiffScheduleRepository scheduleRepository;
    private final DiffScheduleStepRepository scheduleStepRepository;
    private final DiffDefinitionRepository definitionRepository;
    private final DiffStepRepository stepRepository;
    private final CrmUserService crmUserService;
    private final CrmUserRepository userRepository;
    private final BroadcastService broadcastService;
    private final BroadcastRepository broadcastRepository;
    private final AuditLogService auditLog;
    private final DomainSettingService domainSettingService;
    private final HtmlImageService htmlImageService;
    private final MessageService messageService;
    private final LineUserRepository lineUserRepository;
    private final LineAccountService lineAccountService;
    private final com.crm.repository.AdminUserRepository adminUserRepository;

    public DiffScheduleService(DiffScheduleRepository scheduleRepository,
                                DiffScheduleStepRepository scheduleStepRepository,
                                DiffDefinitionRepository definitionRepository,
                                DiffStepRepository stepRepository,
                                CrmUserService crmUserService,
                                CrmUserRepository userRepository,
                                BroadcastService broadcastService,
                                BroadcastRepository broadcastRepository,
                                AuditLogService auditLog,
                                DomainSettingService domainSettingService,
                                HtmlImageService htmlImageService,
                                MessageService messageService,
                                LineUserRepository lineUserRepository,
                                LineAccountService lineAccountService,
                                com.crm.repository.AdminUserRepository adminUserRepository) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleStepRepository = scheduleStepRepository;
        this.definitionRepository = definitionRepository;
        this.stepRepository = stepRepository;
        this.crmUserService = crmUserService;
        this.userRepository = userRepository;
        this.broadcastService = broadcastService;
        this.broadcastRepository = broadcastRepository;
        this.auditLog = auditLog;
        this.domainSettingService = domainSettingService;
        this.htmlImageService = htmlImageService;
        this.messageService = messageService;
        this.lineUserRepository = lineUserRepository;
        this.lineAccountService = lineAccountService;
        this.adminUserRepository = adminUserRepository;
    }

    /** Register a new schedule: resolves + freezes the target list, then materialises one
     *  DiffScheduleStep per DiffStep in the definition's timeline. */
    @Transactional
    public DiffSchedule register(Long diffDefinitionId, String targetType, String targetRaw,
                                  Long setByAdminId, String setByAdminName) {
        DiffDefinition def = definitionRepository.findById(diffDefinitionId)
                .orElseThrow(() -> new NotFoundException("diff definition not found: " + diffDefinitionId));
        List<DiffStep> steps = stepRepository.findByDiffDefinitionIdOrderByStepOrderAsc(diffDefinitionId);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("この差分にはステップが登録されていません。先に差分定義編集画面でステップを追加してください。");
        }
        // 登録後(分後) steps only ever fire for newly registered users (applyRegistrationSteps),
        // never when a diff is applied by hand to existing users.
        steps = steps.stream()
                .filter(st -> !DiffStep.OFFSET_AFTER_REGISTER.equals(st.getOffsetMode()))
                .collect(Collectors.toList());
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("この差分は「登録後(分後)」のステップのみのため、新規登録ユーザーに自動で発動します（手動でセットする必要はありません）。");
        }

        List<Long> ids = resolveTargetIds(targetType, targetRaw);

        LocalDateTime setAt = LocalDateTime.now();

        DiffSchedule s = new DiffSchedule();
        s.setDiffDefinitionId(def.getId());
        s.setDiffNameSnapshot(def.getName());
        s.setTargetType(targetType);
        s.setTargetValue(targetRaw);
        s.setTargetUserIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        s.setSetAt(setAt);
        s.setSetByAdminId(setByAdminId);
        s.setSetByAdminName(setByAdminName);
        DiffSchedule saved = scheduleRepository.save(s);

        for (DiffStep step : steps) {
            DiffScheduleStep ss = new DiffScheduleStep();
            ss.setDiffScheduleId(saved.getId());
            ss.setStepOrder(step.getStepOrder());
            ss.setOffsetMode(step.getOffsetMode());
            ss.setOffsetMinutes(step.getOffsetMinutes());
            ss.setOffsetDays(step.getOffsetDays());
            ss.setOffsetClockTime(step.getOffsetClockTime());
            ss.setScheduledFor(computeScheduledFor(setAt, step.getOffsetMode(),
                    step.getOffsetMinutes(), step.getOffsetDays(), step.getOffsetClockTime()));
            ss.setStepType(step.getStepType());
            ss.setChannel(step.getChannel());
            ss.setSubjectSnapshot(step.getSubject());
            ss.setBodySnapshot(step.getBody());
            ss.setMemoSlotSnapshot(step.getMemoSlot());
            ss.setLineAccountId(step.getLineAccountId());
            ss.setImageIdSnapshot(step.getImageId());
            ss.setStatus(DiffScheduleStep.STATUS_PENDING);
            scheduleStepRepository.save(ss);
        }

        auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_CREATE, "DiffSchedule", saved.getId(),
                "diff=" + def.getName() + " target=" + targetType + ":" + targetRaw
                        + " targets=" + ids.size() + " steps=" + steps.size());
        return saved;
    }

    private List<Long> resolveTargetIds(String targetType, String targetRaw) {
        if (DiffSchedule.TARGET_LINE_NAME.equals(targetType)) {
            return resolveLineNameTargetIds(targetRaw);
        }
        UserSearchForm form = new UserSearchForm();
        if (DiffSchedule.TARGET_PHONE.equals(targetType)) {
            form.setPhoneNumber(targetRaw);
        } else if (DiffSchedule.TARGET_EMAIL.equals(targetType)) {
            form.setEmail(targetRaw);
        } else if (DiffSchedule.TARGET_FOLDER.equals(targetType)) {
            form.setFolder(targetRaw);
        } else {
            throw new IllegalArgumentException("unknown target type: " + targetType);
        }
        return crmUserService.findIdsBySearch(form, null);
    }

    /** LINE display-name matching lives on LineUser, not CrmUser, so it can't go through
     *  UserSearchForm/buildSpecification like phone/email/folder — resolved separately here,
     *  with the same newline-separated-multi-value convention as the phone/email textareas. */
    private List<Long> resolveLineNameTargetIds(String targetRaw) {
        if (targetRaw == null || targetRaw.trim().isEmpty()) return java.util.Collections.emptyList();
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        for (String line : targetRaw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            ids.addAll(lineUserRepository.findLinkedCrmUserIdsByDisplayNameContaining(t));
        }
        return new java.util.ArrayList<>(ids);
    }

    /**
     * 当日(分後): setAt + N minutes. 翌日以降(日数+時刻): setAt's calendar date + N days, at
     * the operator-specified clock time (HH:mm). Both relative to SET_AT, never chained off
     * a previous step, so re-ordering steps never shifts other steps' fire times.
     */
    static LocalDateTime computeScheduledFor(LocalDateTime setAt, String mode,
                                              Integer minutes, Integer days, String clockTime) {
        // 登録後(分後): setAt is the user's registration time (see applyRegistrationSteps).
        if (DiffStep.OFFSET_MINUTES.equals(mode) || DiffStep.OFFSET_AFTER_REGISTER.equals(mode)) {
            if (minutes == null || minutes < 1) throw new IllegalArgumentException("分後の値は1以上で指定してください");
            return setAt.plusMinutes(minutes);
        }
        if (DiffStep.OFFSET_DAYS.equals(mode)) {
            if (days == null || days < 1) throw new IllegalArgumentException("日数は1以上で指定してください");
            LocalTime t = LocalTime.parse(clockTime);
            return setAt.toLocalDate().plusDays(days).atTime(t);
        }
        throw new IllegalArgumentException("unknown offset mode: " + mode);
    }

    /**
     * 登録後(分後): for a user who was just newly registered (manual add or LINE friend-add —
     * callers must NOT call this for CSV imports, which are excluded to prevent mass accidental
     * sends), creates one schedule per diff definition that has 登録後 steps, targeting only
     * this user, with each step due N minutes after the registration. The normal diff
     * dispatcher then fires them. Runs in its own transaction and callers guard it, so a
     * problem here never fails or rolls back the registration itself.
     *
     * @param adminId the registering admin; null (LINE friend-add) = the primary admin. A real
     *                id is required: every send goes out as a BROADCAST, whose ADMIN_USER_ID is
     *                NOT NULL.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int applyRegistrationSteps(com.crm.entity.CrmUser user, Long adminId) {
        if (user == null || user.getId() == null) return 0;
        int created = 0;
        try {
            if (adminId == null) {
                adminId = adminUserRepository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                        .findFirst().map(com.crm.entity.AdminUser::getId).orElse(null);
            }
            LocalDateTime registeredAt = user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now();
            java.util.Map<Long, List<DiffStep>> byDef = new java.util.LinkedHashMap<>();
            for (DiffStep st : stepRepository.findByOffsetModeOrderByDiffDefinitionIdAscStepOrderAsc(DiffStep.OFFSET_AFTER_REGISTER)) {
                byDef.computeIfAbsent(st.getDiffDefinitionId(), k -> new java.util.ArrayList<>()).add(st);
            }
            for (java.util.Map.Entry<Long, List<DiffStep>> e : byDef.entrySet()) {
                DiffDefinition def = definitionRepository.findById(e.getKey()).orElse(null);
                if (def == null) continue;
                DiffSchedule s = new DiffSchedule();
                s.setDiffDefinitionId(def.getId());
                s.setDiffNameSnapshot(def.getName());
                s.setTargetType(DiffSchedule.TARGET_REGISTER);
                s.setTargetValue(String.valueOf(user.getId()));
                s.setTargetUserIds(String.valueOf(user.getId()));
                s.setSetAt(registeredAt);
                s.setSetByAdminId(adminId);
                s.setSetByAdminName("新規登録(自動)");
                DiffSchedule saved = scheduleRepository.save(s);
                for (DiffStep step : e.getValue()) {
                    DiffScheduleStep ss = new DiffScheduleStep();
                    ss.setDiffScheduleId(saved.getId());
                    ss.setStepOrder(step.getStepOrder());
                    ss.setOffsetMode(step.getOffsetMode());
                    ss.setOffsetMinutes(step.getOffsetMinutes());
                    ss.setScheduledFor(computeScheduledFor(registeredAt, step.getOffsetMode(),
                            step.getOffsetMinutes(), null, null));
                    ss.setStepType(step.getStepType());
                    ss.setChannel(step.getChannel());
                    ss.setSubjectSnapshot(step.getSubject());
                    ss.setBodySnapshot(step.getBody());
                    ss.setMemoSlotSnapshot(step.getMemoSlot());
                    ss.setLineAccountId(step.getLineAccountId());
                    ss.setImageIdSnapshot(step.getImageId());
                    ss.setStatus(DiffScheduleStep.STATUS_PENDING);
                    scheduleStepRepository.save(ss);
                }
                created++;
                log.info("[DIFF] registration steps applied: user={} diff={} steps={}",
                        user.getId(), def.getId(), e.getValue().size());
            }
        } catch (Exception ex) {
            log.warn("[DIFF] registration steps failed for user={}: {}", user.getId(), ex.toString());
        }
        return created;
    }

    public List<DiffScheduleStep> listPendingSteps() {
        return scheduleStepRepository.findAllPending();
    }

    public java.util.Optional<DiffSchedule> findScheduleById(Long id) {
        return scheduleRepository.findById(id);
    }

    public Page<DiffScheduleStep> searchHistory(String status, String targetType, Long definitionId, Pageable pageable) {
        return scheduleStepRepository.search(status, targetType, definitionId, pageable);
    }

    /** Every DiffScheduleStep (pending + history) whose parent schedule targeted this user —
     *  powers the user-detail page's 差分スケジュール確認/削除 view. The repository LIKE is only
     *  a coarse candidate filter (a substring match can false-hit, e.g. id 5 against "51,52"),
     *  so each candidate's CSV is re-checked for exact membership before its steps are included. */
    /** Pending (not-yet-fired) MESSAGE-type steps for this user only — powers the 返信画面
     *  (thread.html) 差分予約 panel: a real send hasn't happened yet, so there's no Message row
     *  to show, but the operator still wants to see/cancel it as a "reservation". HTML_SWITCH
     *  steps are excluded — they don't send anything, so they're not a "reservation" here. */
    public List<DiffScheduleStep> listPendingMessageStepsForUser(Long userId) {
        List<DiffScheduleStep> out = new ArrayList<>();
        for (DiffScheduleStep s : listStepsForUser(userId)) {
            if (DiffScheduleStep.STATUS_PENDING.equals(s.getStatus())
                    && DiffStep.STEP_MESSAGE.equals(s.getStepType())) {
                out.add(s);
            }
        }
        return out;
    }

    public List<DiffScheduleStep> listStepsForUser(Long userId) {
        List<DiffSchedule> candidates = scheduleRepository.findByTargetUserIdsContaining(String.valueOf(userId));
        List<DiffScheduleStep> out = new ArrayList<>();
        for (DiffSchedule s : candidates) {
            if (!parseIds(s.getTargetUserIds()).contains(userId)) continue;
            out.addAll(scheduleStepRepository.findByDiffScheduleIdOrderByStepOrderAsc(s.getId()));
        }
        out.sort((a, b) -> b.getId().compareTo(a.getId()));
        return out;
    }

    /** Hard-deletes one step. Note: since a schedule's target list is shared across every
     *  user it was set for, this removes the record for ALL of that schedule's targets, not
     *  just the one being viewed — callers must make that clear to the operator before calling
     *  this. If the parent schedule ends up with no steps left, it is deleted too so it doesn't
     *  linger as an empty shell. */
    @Transactional
    public boolean deleteStep(Long stepId, String deletedByAdminName) {
        DiffScheduleStep step = scheduleStepRepository.findById(stepId).orElse(null);
        if (step == null) return false;
        Long scheduleId = step.getDiffScheduleId();
        scheduleStepRepository.deleteById(stepId);
        auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_DELETE, "DiffScheduleStep", stepId,
                "deleted by " + deletedByAdminName);
        if (scheduleStepRepository.findByDiffScheduleIdOrderByStepOrderAsc(scheduleId).isEmpty()) {
            scheduleRepository.deleteById(scheduleId);
        }
        return true;
    }

    @Transactional
    public int deleteSteps(List<Long> stepIds, String deletedByAdminName) {
        int n = 0;
        for (Long id : stepIds) {
            if (deleteStep(id, deletedByAdminName)) n++;
        }
        return n;
    }

    /** Delete every step (pending + history) associated with this user in one action —
     *  backs the user-detail page's 全件削除 button. */
    @Transactional
    public int deleteAllStepsForUser(Long userId, String deletedByAdminName) {
        List<Long> ids = new ArrayList<>();
        for (DiffScheduleStep s : listStepsForUser(userId)) ids.add(s.getId());
        return deleteSteps(ids, deletedByAdminName);
    }

    /** Cancel a single pending step. Re-fetches immediately before mutating so a step that
     *  fired between the pending-list render and this click is correctly left alone. */
    @Transactional
    public boolean cancelStep(Long stepId, String cancelledByAdminName) {
        DiffScheduleStep s = scheduleStepRepository.findById(stepId).orElse(null);
        if (s == null || !DiffScheduleStep.STATUS_PENDING.equals(s.getStatus())) return false;
        s.setStatus(DiffScheduleStep.STATUS_CANCELLED);
        s.setCancelledAt(LocalDateTime.now());
        s.setCancelledByAdminName(cancelledByAdminName);
        scheduleStepRepository.save(s);
        auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_CANCEL, "DiffScheduleStep", s.getId(),
                "cancelled by " + cancelledByAdminName);
        return true;
    }

    /** Cancel every still-pending step under one schedule (a whole registered "campaign run"). */
    @Transactional
    public int cancelSchedule(Long scheduleId, String cancelledByAdminName) {
        List<DiffScheduleStep> pending = scheduleStepRepository.findByStatusAndDiffScheduleId(
                DiffScheduleStep.STATUS_PENDING, scheduleId);
        int n = 0;
        for (DiffScheduleStep s : pending) {
            if (cancelStep(s.getId(), cancelledByAdminName)) n++;
        }
        return n;
    }

    /** Bulk-cancel every PENDING step whose parent schedule matches one dimension (target
     *  type+value, or diff definition). Loops calling {@link #cancelStep} (re-checking each
     *  row) rather than a blind bulk UPDATE, so a step that fires mid-operation is skipped. */
    @Transactional
    public int cancelByTarget(String targetType, String targetValue, Long diffDefinitionId, String cancelledByAdminName) {
        List<DiffScheduleStep> candidates = (diffDefinitionId != null)
                ? scheduleStepRepository.findPendingByDiffDefinition(diffDefinitionId)
                : scheduleStepRepository.findPendingByTarget(targetType, targetValue);
        int n = 0;
        for (DiffScheduleStep c : candidates) {
            if (cancelStep(c.getId(), cancelledByAdminName)) n++;
        }
        return n;
    }

    /** Bulk-cancel by phone/email search: resolves the query to matching CRM_USER ids (same
     *  partial-match search as registration), then cancels every PENDING step whose parent
     *  schedule's frozen target-user snapshot contains any of those ids — unlike
     *  {@link #cancelByTarget}, this matches on actual resolved users rather than requiring
     *  the operator to retype the exact original raw target string. */
    @Transactional
    public int cancelByUserSearch(String targetType, String rawQuery, String cancelledByAdminName) {
        List<Long> matchIds = resolveTargetIds(targetType, rawQuery);
        if (matchIds.isEmpty()) return 0;
        java.util.Set<Long> matchSet = new java.util.HashSet<>(matchIds);
        List<DiffScheduleStep> pending = scheduleStepRepository.findAllPending();
        int n = 0;
        for (DiffScheduleStep step : pending) {
            DiffSchedule sched = scheduleRepository.findById(step.getDiffScheduleId()).orElse(null);
            if (sched == null) continue;
            boolean matches = false;
            for (Long uid : parseIds(sched.getTargetUserIds())) {
                if (matchSet.contains(uid)) { matches = true; break; }
            }
            if (matches && cancelStep(step.getId(), cancelledByAdminName)) n++;
        }
        return n;
    }

    /** Execute one due step: MESSAGE steps queue a real send via the same pipeline as 一斉送信
     *  (placeholders/%reply_url% resolved per-recipient, delivered by the existing message
     *  dispatcher); HTML_SWITCH steps flip activeMemoSlot for every target user, chunked
     *  500-at-a-time like ReplyHtmlSlotService.bulkApply. */
    @Transactional
    public void execute(DiffScheduleStep step) {
        DiffSchedule schedule = scheduleRepository.findById(step.getDiffScheduleId()).orElse(null);
        if (schedule == null) {
            step.setStatus(DiffScheduleStep.STATUS_FAILED);
            step.setResultDetail("parent schedule not found");
            scheduleStepRepository.save(step);
            return;
        }
        List<Long> ids = parseIds(schedule.getTargetUserIds());

        if (DiffStep.STEP_HTML_SWITCH.equals(step.getStepType())) {
            executeHtmlSwitch(step, ids);
        } else {
            // MESSAGE and MESSAGE_IMAGE share the same send path — the only difference is
            // executeMessage() splicing an <img> into the body first when imageIdSnapshot is set.
            executeMessage(step, schedule, ids);
        }
    }

    /** Prepends {@code <img src=".../img/{id}">} to the body for a MESSAGE_IMAGE step. If the
     *  referenced image was deleted after this step was created, the send still goes out with
     *  the plain body rather than failing the whole step — a missing decorative image isn't
     *  worth blocking the message over. */
    private String bodyWithImageSpliced(DiffScheduleStep step) {
        String body = step.getBodySnapshot();
        if (!DiffStep.STEP_MESSAGE_IMAGE.equals(step.getStepType()) || step.getImageIdSnapshot() == null) {
            return body;
        }
        if (!htmlImageService.findById(step.getImageIdSnapshot()).isPresent()) {
            log.warn("Diff-schedule-step image missing at execute time: stepId={} imageId={}",
                    step.getId(), step.getImageIdSnapshot());
            return body;
        }
        String imgTag = "<img src=\"" + domainSettingService.getReplyBaseUrl() + "/img/" + step.getImageIdSnapshot() + "\"/><br/>";
        return imgTag + (body == null ? "" : body);
    }

    private void executeHtmlSwitch(DiffScheduleStep step, List<Long> ids) {
        int applied = 0;
        final int chunkSize = 500;
        for (int i = 0; i < ids.size(); i += chunkSize) {
            List<Long> chunk = ids.subList(i, Math.min(i + chunkSize, ids.size()));
            List<CrmUser> users = userRepository.findAllById(chunk);
            for (CrmUser u : users) {
                u.setActiveMemoSlot(step.getMemoSlotSnapshot());
                userRepository.save(u);
                applied++;
            }
        }
        int missing = ids.size() - applied;
        step.setStatus(DiffScheduleStep.STATUS_EXECUTED);
        step.setExecutedAt(LocalDateTime.now());
        step.setResultDetail("HTML切替 slot=" + step.getMemoSlotSnapshot()
                + " applied=" + applied + " missing=" + missing + " total=" + ids.size());
        scheduleStepRepository.save(step);
        log.info("Diff-schedule-step executed (HTML switch): id={} {}", step.getId(), step.getResultDetail());
        auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_EXECUTE, "DiffScheduleStep", step.getId(), step.getResultDetail());
    }

    private void executeMessage(DiffScheduleStep step, DiffSchedule schedule, List<Long> ids) {
        if (DiffStep.CHANNEL_LINE.equals(step.getChannel())
                && DiffStep.LINE_ACCOUNT_LINKED_DYNAMIC.equals(step.getLineAccountId())) {
            executeMessageLineDynamic(step, schedule, ids);
            return;
        }
        BroadcastForm form = new BroadcastForm();
        form.setTargetUserIds(ids);
        form.setChannel(step.getChannel());
        form.setTitle(schedule.getDiffNameSnapshot());
        form.setSubject(DiffStep.CHANNEL_EMAIL.equals(step.getChannel()) ? step.getSubjectSnapshot() : null);
        form.setBody(bodyWithImageSpliced(step));
        form.setLineAccountId(step.getLineAccountId());
        form.setRatePerMinute(DiffStep.CHANNEL_LINE.equals(step.getChannel())
                ? domainSettingService.getLineRatePerMinute() : 60);
        try {
            com.crm.entity.Broadcast b = DiffStep.CHANNEL_SMS.equals(step.getChannel())
                    ? broadcastService.createAndQueueSms(form, schedule.getSetByAdminId())
                    : broadcastService.createAndQueue(form, schedule.getSetByAdminId());
            b.setDiffOrigin(true);
            broadcastRepository.save(b);
            // 送信時点で既読 — this broadcast IS the response to whatever these users had
            // previously sent in, so their prior inbound is now considered handled rather
            // than waiting for an admin to separately open each thread (2026-09-23 request).
            messageService.markThreadsAsRead(ids);
            step.setStatus(DiffScheduleStep.STATUS_EXECUTED);
            step.setExecutedAt(LocalDateTime.now());
            step.setResultDetail("メッセージ送信 channel=" + step.getChannel()
                    + " broadcastId=" + b.getId() + " queued=" + b.getTotalCount());
        } catch (BroadcastService.NoTargetsException e) {
            step.setStatus(DiffScheduleStep.STATUS_FAILED);
            step.setResultDetail("送信先なし: " + e.getMessage());
        }
        scheduleStepRepository.save(step);
        log.info("Diff-schedule-step executed (message): id={} {}", step.getId(), step.getResultDetail());
        auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_EXECUTE, "DiffScheduleStep", step.getId(), step.getResultDetail());
    }

    /**
     * 紐づきアカ — this step's lineAccountId is the dynamic sentinel, so each recipient is
     * routed to whichever LINE account they're actually linked to right now, resolved here at
     * fire time rather than whatever was chosen when the step was configured. A recipient
     * linked to more than one child account resolves to exactly one account — whichever has
     * the lowest {@link com.crm.entity.LineAccount#getLinkagePriority()}, most-recently-active
     * as a tie-break — never more than one, so exactly one message is delivered even when a
     * recipient friended several accounts (see {@link LineAccountService#resolveDynamicLinkedAccountIds}
     * , 2026-09-27 client request). One Broadcast row is created per distinct resolved account
     * so BroadcastService's existing single-account targeting/filtering logic is reused
     * unchanged (2026-09-23 client request).
     */
    private void executeMessageLineDynamic(DiffScheduleStep step, DiffSchedule schedule, List<Long> ids) {
        java.util.Map<Long, Long> lineAccountIdByCrmUserId = lineAccountService.resolveDynamicLinkedAccountIds(ids);
        java.util.Map<Long, List<Long>> idsByAccount = new java.util.LinkedHashMap<>();
        int unlinked = 0;
        for (Long id : ids) {
            Long accountId = lineAccountIdByCrmUserId.get(id);
            if (accountId == null) { unlinked++; continue; }
            idsByAccount.computeIfAbsent(accountId, k -> new java.util.ArrayList<>()).add(id);
        }
        if (idsByAccount.isEmpty()) {
            step.setStatus(DiffScheduleStep.STATUS_FAILED);
            step.setResultDetail("送信先なし: 紐づいているLINEアカウントを持つ対象がいません");
            scheduleStepRepository.save(step);
            log.info("Diff-schedule-step executed (message): id={} {}", step.getId(), step.getResultDetail());
            auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_EXECUTE, "DiffScheduleStep", step.getId(), step.getResultDetail());
            return;
        }
        String body = bodyWithImageSpliced(step);
        java.util.List<String> broadcastSummaries = new java.util.ArrayList<>();
        java.util.List<Long> readTargets = new java.util.ArrayList<>();
        int totalQueued = 0;
        for (java.util.Map.Entry<Long, List<Long>> e : idsByAccount.entrySet()) {
            BroadcastForm form = new BroadcastForm();
            form.setTargetUserIds(e.getValue());
            form.setChannel(DiffStep.CHANNEL_LINE);
            form.setTitle(schedule.getDiffNameSnapshot());
            form.setBody(body);
            form.setLineAccountId(e.getKey());
            form.setRatePerMinute(domainSettingService.getLineRatePerMinute());
            try {
                com.crm.entity.Broadcast b = broadcastService.createAndQueue(form, schedule.getSetByAdminId());
                b.setDiffOrigin(true);
                broadcastRepository.save(b);
                broadcastSummaries.add("account=" + e.getKey() + " broadcastId=" + b.getId() + " queued=" + b.getTotalCount());
                totalQueued += b.getTotalCount() == null ? 0 : b.getTotalCount();
                readTargets.addAll(e.getValue());
            } catch (BroadcastService.NoTargetsException ex) {
                broadcastSummaries.add("account=" + e.getKey() + " 送信先なし");
            }
        }
        messageService.markThreadsAsRead(readTargets);
        step.setStatus(DiffScheduleStep.STATUS_EXECUTED);
        step.setExecutedAt(LocalDateTime.now());
        step.setResultDetail("メッセージ送信 channel=LINE(紐づきアカ) queued=" + totalQueued
                + (unlinked > 0 ? " unlinked=" + unlinked : "")
                + " [" + String.join(", ", broadcastSummaries) + "]");
        scheduleStepRepository.save(step);
        log.info("Diff-schedule-step executed (message): id={} {}", step.getId(), step.getResultDetail());
        auditLog.record(AuditLogService.ACTION_DIFF_SCHEDULE_EXECUTE, "DiffScheduleStep", step.getId(), step.getResultDetail());
    }

    private static List<Long> parseIds(String csv) {
        if (csv == null || csv.trim().isEmpty()) return Collections.emptyList();
        List<Long> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            try { out.add(Long.parseLong(p.trim())); } catch (NumberFormatException ignored) { /* skip malformed */ }
        }
        return out;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }
}
