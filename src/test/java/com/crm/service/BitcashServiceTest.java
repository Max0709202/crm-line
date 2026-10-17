package com.crm.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitcashServiceTest {

    /** 導入マニュアル 5.2.2 「ハッシュによる改竄検知の確認例」 (values as the servlet sees them, decoded). */
    @Test
    void hashMatchesManualExample() {
        assertTrue(BitcashService.hashMatches("STSC-00001", "ORDER001", "2014/01/01 00:00:00", "1000",
                "abcdefg", "906df9b78690b9bdf9feaf945fdbc8245b914e7f"));
        // upper-case hex is accepted too
        assertTrue(BitcashService.hashMatches("STSC-00001", "ORDER001", "2014/01/01 00:00:00", "1000",
                "abcdefg", "906DF9B78690B9BDF9FEAF945FDBC8245B914E7F"));
    }

    @Test
    void hashRejectsTamperedValuesOrWrongKey() {
        String h = "906df9b78690b9bdf9feaf945fdbc8245b914e7f";
        assertFalse(BitcashService.hashMatches("STSC-00001", "ORDER001", "2014/01/01 00:00:00", "9000", "abcdefg", h));
        assertFalse(BitcashService.hashMatches("STSC-00001", "ORDER001", "2014/01/01 00:00:00", "1000", "other", h));
        assertFalse(BitcashService.hashMatches("STSC-00001", "ORDER001", "2014/01/01 00:00:00", "1000", "abcdefg", ""));
    }

    @Test
    void parsesPrepareSettleResponse() {
        Map<String, String> r = BitcashService.parseResponse("status_code=0&status_string=SUCCESS&tran_id=STSC-16814&settle_url=https%"
                + "3A%2F%2Fsettle.bitcash.co.jp%2Fsettle%2Ftri%3Fact%3DselectSettle%26encTranId%3DHKyp%252FayjPjDXLDGeXp4duV1%252Fuxpqprmrsvxeie4k4V0%253D\n");
        assertEquals("0", r.get("status_code"));
        assertEquals("STSC-16814", r.get("tran_id"));
        assertTrue(r.get("settle_url").startsWith("https%3A%2F%2Fsettle.bitcash.co.jp"));
    }

    @Test
    void parsesConfirmResponseWithSpaceInDate() {
        Map<String, String> r = BitcashService.parseResponse("status_code=0&status_string=SUCCESS&settle_id=68980921&price=1"
                + "&settle_date=2014/01/16 16:07:44&refunded=false&cno=8000000001000067");
        assertEquals("1", r.get("price"));
        assertEquals("false", r.get("refunded"));
        assertEquals("8000000001000067", r.get("cno"));
        assertEquals("2014/01/16 16:07:44", r.get("settle_date"));
    }

    @Test
    void orderCodeRoundTrip() {
        assertEquals("BC42", BitcashService.orderCode(42L));
        assertEquals(Long.valueOf(42L), BitcashService.parseOrderCode("BC42"));
        assertNull(BitcashService.parseOrderCode("NA"));
        assertNull(BitcashService.parseOrderCode("BCx"));
    }
}
