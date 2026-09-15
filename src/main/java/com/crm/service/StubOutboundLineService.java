package com.crm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * No-op LINE adapter. Logs the attempted send and always reports success.
 * Active when {@code app.line.adapter=stub} (the default until a real LINE Channel exists).
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.line.adapter", havingValue = "stub", matchIfMissing = true)
public class StubOutboundLineService implements OutboundLineService {

    private static final Logger log = LoggerFactory.getLogger(StubOutboundLineService.class);

    @Override
    public SendResult send(LineSendRequest req) {
        log.info("[STUB LINE] to={} sender={} body={}", req.toLineUserId, req.senderName, req.body);
        return SendResult.ok();
    }
}
