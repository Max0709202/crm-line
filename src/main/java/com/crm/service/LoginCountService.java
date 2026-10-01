package com.crm.service;

import com.crm.entity.DailyLoginCount;
import com.crm.repository.CrmUserRepository;
import com.crm.repository.DailyLoginCountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入金レポート ログイン数 (client request 2026-10-01): per day, the number of users whose 最終ログイン
 * falls on that day — a user who logs in several times that day counts once. CRM_USER.LAST_LOGIN_AT
 * is overwritten on every login, so each day's count is saved at 23:59 into DAILY_LOGIN_COUNT;
 * today's count is always live (0:00 → now).
 */
@Service
public class LoginCountService {

    private static final Logger log = LoggerFactory.getLogger(LoginCountService.class);

    private final CrmUserRepository userRepository;
    private final DailyLoginCountRepository dailyRepository;

    public LoginCountService(CrmUserRepository userRepository, DailyLoginCountRepository dailyRepository) {
        this.userRepository = userRepository;
        this.dailyRepository = dailyRepository;
    }

    /** Users whose 最終ログイン is on {@code day} (for today: 0:00 → now). */
    public long countFor(LocalDate day) {
        return userRepository.countByLastLoginBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /** {yyyy-MM-dd: n} — the saved days plus today's live count. */
    public Map<String, Long> dailyCounts() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (DailyLoginCount d : dailyRepository.findAll()) {
            out.put(d.getLoginDate().toString(), (long) d.getLoginCount());
        }
        LocalDate today = LocalDate.now();
        out.put(today.toString(), countFor(today));
        return out;
    }

    /** Daily at 23:59:59 — save the day's ログイン数 before tomorrow's logins overwrite 最終ログイン. */
    @Scheduled(cron = "59 59 23 * * *")
    public void saveTodayCount() {
        LocalDateTime now = LocalDateTime.now();
        // The scheduler thread is shared, so this can start late; past midnight it is still yesterday's run.
        LocalDate day = now.getHour() < 12 ? now.toLocalDate().minusDays(1) : now.toLocalDate();
        try {
            DailyLoginCount row = dailyRepository.findById(day).orElseGet(DailyLoginCount::new);
            row.setLoginDate(day);
            row.setLoginCount((int) countFor(day));
            dailyRepository.save(row);
            log.info("Daily login count saved: {} = {}", day, row.getLoginCount());
        } catch (Exception e) {
            log.warn("Daily login count for {} failed: {}", day, e.toString());
        }
    }
}
