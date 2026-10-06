package com.crm.service;

import com.crm.entity.HtmlImage;
import com.crm.entity.Message;
import com.crm.entity.MessageImage;
import com.crm.repository.MessageImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Images of an outbound message (返信) or a broadcast (一斉送信 / 差分), uploaded into HTML画像管理:
 * <ul>
 *   <li>メール / SMS — 画像添付: not written into the mail as a URL (the 文字数設定 used to cut it off);
 *       the 返信画面 shows them with a 📎 mark, behind ポイント設定's 写真閲覧 points;</li>
 *   <li>LINE — 画像挿入: sent to LINE as image messages after the text (LINE takes JPEG / PNG).</li>
 * </ul>
 * A broadcast's messages use the broadcast's images.
 */
@Service
public class MessageImageService {

    /** Images per message (LINE: one push holds 5 messages — the text and up to 4 images). */
    public static final int MAX_IMAGES = 4;

    private final MessageImageRepository repository;
    private final HtmlImageService htmlImageService;
    private final DomainSettingService domainSettingService;

    public MessageImageService(MessageImageRepository repository, HtmlImageService htmlImageService,
                               DomainSettingService domainSettingService) {
        this.repository = repository;
        this.htmlImageService = htmlImageService;
        this.domainSettingService = domainSettingService;
    }

    public static class ImageException extends RuntimeException {
        public ImageException(String msg) { super(msg); }
    }

    /**
     * The given image ids that exist, in order, without duplicates. Throws when there are more than
     * {@link #MAX_IMAGES}, or (for LINE) an image isn't JPEG / PNG.
     */
    public List<Long> validIds(Collection<Long> ids, boolean forLine) {
        List<Long> out = new ArrayList<>();
        if (ids == null) return out;
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null || !seen.add(id)) continue;
            Optional<HtmlImage> img = htmlImageService.findById(id);
            if (!img.isPresent()) continue;
            if (forLine) {
                String type = img.get().getContentType() == null ? "" : img.get().getContentType().toLowerCase(Locale.ROOT);
                if (!"image/jpeg".equals(type) && !"image/png".equals(type)) {
                    throw new ImageException("LINEに挿入できる画像はJPEG・PNGのみです（" + img.get().getFileName() + "）");
                }
            }
            out.add(id);
        }
        if (out.size() > MAX_IMAGES) throw new ImageException("画像は1通につき" + MAX_IMAGES + "枚までです");
        return out;
    }

    @Transactional
    public void attach(String ownerType, Long ownerId, List<Long> imageIds) {
        if (ownerId == null || imageIds == null) return;
        int i = 0;
        for (Long id : imageIds) {
            MessageImage m = new MessageImage();
            m.setOwnerType(ownerType);
            m.setOwnerId(ownerId);
            m.setImageId(id);
            m.setSortNo(i++);
            repository.save(m);
        }
    }

    /** Image ids of one message: its own, else its broadcast's. */
    public List<Long> imageIdsOf(Message m) {
        if (m == null || m.getId() == null) return Collections.emptyList();
        List<Long> ids = imageIdsOfMessages(Collections.singletonList(m)).get(m.getId());
        return ids == null ? Collections.<Long>emptyList() : ids;
    }

    /** Image ids of one message that still exist in HTML画像管理 (one deleted since is left out). */
    public List<Long> existingImageIdsOf(Message m) {
        List<Long> out = new ArrayList<>();
        for (Long id : imageIdsOf(m)) if (htmlImageService.findById(id).isPresent()) out.add(id);
        return out;
    }

    /** message id → image ids (the message's own, else its broadcast's); messages without images are absent. */
    public Map<Long, List<Long>> imageIdsOfMessages(Collection<Message> messages) {
        Map<Long, List<Long>> out = new HashMap<>();
        if (messages == null || messages.isEmpty()) return out;
        List<Long> ids = new ArrayList<>();
        Set<Long> broadcastIds = new LinkedHashSet<>();
        for (Message m : messages) {
            if (m.getId() != null) ids.add(m.getId());
            if (m.getBroadcastId() != null) broadcastIds.add(m.getBroadcastId());
        }
        Map<Long, List<Long>> own = group(MessageImage.OWNER_MESSAGE, ids);
        Map<Long, List<Long>> byBroadcast = group(MessageImage.OWNER_BROADCAST, broadcastIds);
        for (Message m : messages) {
            List<Long> l = own.get(m.getId());
            if (l == null && m.getBroadcastId() != null) l = byBroadcast.get(m.getBroadcastId());
            if (l != null && !l.isEmpty()) out.put(m.getId(), l);
        }
        return out;
    }

    /** Was the image sent to this user (so the user's 返信画面 / 会員ページ may show it)? */
    public boolean isImageOfUser(Long userId, Long imageId) {
        return userId != null && imageId != null && repository.countSentToUser(userId, imageId) > 0;
    }

    /** Public URL LINE fetches the image from. */
    public String publicUrl(Long imageId) {
        String base = domainSettingService.getReplyBaseUrl();
        return (base == null ? "" : base.trim().replaceAll("/+$", "")) + "/img/" + imageId;
    }

    private Map<Long, List<Long>> group(String ownerType, Collection<Long> ownerIds) {
        Map<Long, List<Long>> out = new HashMap<>();
        if (ownerIds.isEmpty()) return out;
        for (MessageImage mi : repository.findByOwnerTypeAndOwnerIdInOrderBySortNoAscIdAsc(ownerType, ownerIds)) {
            out.computeIfAbsent(mi.getOwnerId(), k -> new ArrayList<>()).add(mi.getImageId());
        }
        return out;
    }
}
