package com.crm.service;

import com.crm.repository.CrmUserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Member login IDs: 10000, 10001, … in registration order — member registration, the admin's
 * 新規登録 and CSV import all draw the next number here. One MEMBER_LOGIN_SEQ row per issued ID.
 * The next value is MAX(ID)+1 rather than the table's AUTO_INCREMENT, because MySQL 5.7 resets an
 * empty table's AUTO_INCREMENT start on restart; two concurrent registrations racing for the same
 * number make one insert fail on the primary key, and it simply takes the next one.
 */
@Service
public class MemberLoginIdService {

    public static final long START = 10000L;
    private static final int MAX_ATTEMPTS = 20;

    private final JdbcTemplate jdbc;
    private final CrmUserRepository userRepository;

    public MemberLoginIdService(JdbcTemplate jdbc, CrmUserRepository userRepository) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
    }

    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long max = jdbc.queryForObject("SELECT COALESCE(MAX(ID), 0) FROM MEMBER_LOGIN_SEQ", Long.class);
            long candidate = Math.max((max == null ? 0 : max) + 1, START);
            try {
                jdbc.update("INSERT INTO MEMBER_LOGIN_SEQ (ID, CREATED_AT) VALUES (?, NOW())", candidate);
            } catch (DuplicateKeyException race) {
                continue;
            }
            String id = String.valueOf(candidate);
            // an older account could already hold this number as its login ID — then take the next
            if (!userRepository.existsByLoginId(id)) return id;
        }
        throw new IllegalStateException("could not issue a member login id after " + MAX_ATTEMPTS + " attempts");
    }
}
