package com.crm.service;

import com.crm.entity.Chara;
import com.crm.entity.CharaRef;
import com.crm.entity.Message;
import com.crm.entity.UserCharaLink;
import com.crm.repository.CharaRefRepository;
import com.crm.repository.CharaRepository;
import com.crm.repository.MessageRepository;
import com.crm.repository.UserCharaLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * キャラ (キャラ登録) as sender: which キャラ a broadcast / 差分ステップ / message is sent as
 * ({@link CharaRef}), and 紐づきキャラ ({@link UserCharaLink}) — a user is linked to a キャラ as soon
 * as they send one message to it (a reply to a message sent as that キャラ, or otherwise the キャラ
 * that last wrote to them). Removing the link on ユーザー詳細, or deleting the user's message from
 * 個別メッセージ管理, removes it.
 */
@Service
public class CharaLinkService {

    private static final Logger log = LoggerFactory.getLogger(CharaLinkService.class);

    private final CharaRefRepository refRepository;
    private final UserCharaLinkRepository linkRepository;
    private final CharaRepository charaRepository;
    private final MessageRepository messageRepository;
    private final TransactionTemplate ownTx;

    public CharaLinkService(CharaRefRepository refRepository, UserCharaLinkRepository linkRepository,
                            CharaRepository charaRepository, MessageRepository messageRepository,
                            PlatformTransactionManager txManager) {
        this.refRepository = refRepository;
        this.linkRepository = linkRepository;
        this.charaRepository = charaRepository;
        this.messageRepository = messageRepository;
        this.ownTx = new TransactionTemplate(txManager);
        this.ownTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Sets (or with a null / unknown キャラ, clears) the キャラ of one owner. */
    @Transactional
    public void assign(String ownerType, Long ownerId, Long charaId) {
        if (ownerId == null) return;
        Optional<CharaRef> existing = refRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
        if (charaId == null || !charaRepository.existsById(charaId)) {
            existing.ifPresent(refRepository::delete);
            return;
        }
        CharaRef r = existing.orElseGet(CharaRef::new);
        r.setOwnerType(ownerType);
        r.setOwnerId(ownerId);
        r.setCharaId(charaId);
        refRepository.save(r);
    }

    public Long charaIdOf(String ownerType, Long ownerId) {
        if (ownerId == null) return null;
        return refRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId).map(CharaRef::getCharaId).orElse(null);
    }

    /** ownerId → キャラ id for the given owners (owners without a キャラ are absent). */
    public Map<Long, Long> charaIdsOf(String ownerType, Collection<Long> ownerIds) {
        Map<Long, Long> out = new HashMap<>();
        if (ownerIds == null || ownerIds.isEmpty()) return out;
        for (CharaRef r : refRepository.findByOwnerTypeAndOwnerIdIn(ownerType, ownerIds)) out.put(r.getOwnerId(), r.getCharaId());
        return out;
    }

    /** message id → キャラ id for the given messages: the message's own キャラ, else its broadcast's. */
    public Map<Long, Long> charaIdsOfMessages(Collection<Message> messages) {
        Map<Long, Long> out = new HashMap<>();
        if (messages == null || messages.isEmpty()) return out;
        List<Long> ids = new ArrayList<>();
        Set<Long> broadcastIds = new LinkedHashSet<>();
        for (Message m : messages) {
            ids.add(m.getId());
            if (m.getBroadcastId() != null) broadcastIds.add(m.getBroadcastId());
        }
        Map<Long, Long> own = charaIdsOf(CharaRef.OWNER_MESSAGE, ids);
        Map<Long, Long> byBroadcast = charaIdsOf(CharaRef.OWNER_BROADCAST, broadcastIds);
        for (Message m : messages) {
            Long c = own.get(m.getId());
            if (c == null && m.getBroadcastId() != null) c = byBroadcast.get(m.getBroadcastId());
            if (c != null) out.put(m.getId(), c);
        }
        return out;
    }

    public Long charaIdOfMessage(Message m) {
        if (m == null) return null;
        return charaIdsOfMessages(Collections.singletonList(m)).get(m.getId());
    }

    /**
     * A user's message just arrived: it goes to the キャラ of the message it answers, else to the
     * キャラ that last wrote to the user. Records that and links the user to the キャラ. Runs in its
     * own transaction and never throws — a failure here must not lose the inbound message.
     */
    public void onInbound(Message in) {
        if (in == null || in.getId() == null || !Message.DIR_IN.equals(in.getDirection())) return;
        try {
            ownTx.execute(status -> {
                Long charaId = null;
                if (in.getReplyToMessageId() != null) {
                    charaId = messageRepository.findById(in.getReplyToMessageId()).map(this::charaIdOfMessage).orElse(null);
                }
                if (charaId == null) {
                    List<Number> latest = refRepository.latestOutboundCharaId(in.getUserId());
                    if (!latest.isEmpty() && latest.get(0) != null) charaId = latest.get(0).longValue();
                }
                if (charaId == null || !charaRepository.existsById(charaId)) return null;
                assign(CharaRef.OWNER_MESSAGE, in.getId(), charaId);
                link(in.getUserId(), charaId);
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("chara link on inbound failed: messageId={} {}", in.getId(), e.toString());
        }
    }

    @Transactional
    public void link(Long userId, Long charaId) {
        if (userId == null || charaId == null) return;
        if (linkRepository.findByUserIdAndCharaId(userId, charaId).isPresent()) return;
        UserCharaLink l = new UserCharaLink();
        l.setUserId(userId);
        l.setCharaId(charaId);
        linkRepository.save(l);
    }

    @Transactional
    public void unlink(Long userId, Long charaId) {
        if (userId == null || charaId == null) return;
        linkRepository.deleteLink(userId, charaId);
    }

    /** The user's 紐づきキャラ (newest link first); links to deleted キャラ are skipped. */
    public List<Chara> linkedCharas(Long userId) {
        List<Long> ids = new ArrayList<>();
        for (UserCharaLink l : linkRepository.findByUserIdOrderByCreatedAtDesc(userId)) ids.add(l.getCharaId());
        if (ids.isEmpty()) return Collections.emptyList();
        Map<Long, Chara> byId = new HashMap<>();
        for (Chara c : charaRepository.findAllById(ids)) byId.put(c.getId(), c);
        List<Chara> out = new ArrayList<>();
        for (Long id : ids) if (byId.containsKey(id)) out.add(byId.get(id));
        return out;
    }

    /**
     * Called before messages are deleted: a deleted message from the user unlinks the user from
     * the キャラ it was sent to; the deleted messages' キャラ records go too.
     */
    @Transactional
    public void onMessagesDeleted(Collection<Message> messages) {
        if (messages == null || messages.isEmpty()) return;
        List<Message> inbound = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (Message m : messages) {
            if (m == null || m.getId() == null) continue;
            ids.add(m.getId());
            if (Message.DIR_IN.equals(m.getDirection())) inbound.add(m);
        }
        if (ids.isEmpty()) return;
        Map<Long, Long> own = charaIdsOf(CharaRef.OWNER_MESSAGE, ids);
        for (Message m : inbound) {
            Long charaId = own.get(m.getId());
            if (charaId != null) unlink(m.getUserId(), charaId);
        }
        refRepository.deleteOwners(CharaRef.OWNER_MESSAGE, ids);
    }
}
