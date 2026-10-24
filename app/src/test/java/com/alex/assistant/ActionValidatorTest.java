package com.alex.assistant;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

public class ActionValidatorTest {
    private JSONObject j(String s) throws Exception { return new JSONObject(s); }

    @Test public void unknownActionRejected() throws Exception {
        assertNotNull(ActionValidator.validate(j("{\"type\":\"format_phone\"}")));
    }
    @Test public void missingParamRejected() throws Exception {
        assertNotNull(ActionValidator.validate(j("{\"type\":\"open_app\"}")));
    }
    @Test public void validActionAccepted() throws Exception {
        assertNull(ActionValidator.validate(j("{\"type\":\"open_app\",\"name\":\"YouTube\"}")));
    }
    @Test public void enumEnforced() throws Exception {
        assertNotNull(ActionValidator.validate(j("{\"type\":\"scroll\",\"dir\":\"sideways\"}")));
        assertNull(ActionValidator.validate(j("{\"type\":\"scroll\",\"dir\":\"down\"}")));
    }
    @Test public void numericRangeEnforced() throws Exception {
        assertNotNull(ActionValidator.validate(j("{\"type\":\"alarm\",\"hour\":30,\"minute\":0}")));
        assertNull(ActionValidator.validate(j("{\"type\":\"alarm\",\"hour\":7,\"minute\":30}")));
    }
    @Test public void onlyWebLinksAllowed() throws Exception {
        assertNotNull(ActionValidator.validate(j("{\"type\":\"open_url\",\"url\":\"javascript:alert(1)\"}")));
        assertNull(ActionValidator.validate(j("{\"type\":\"open_url\",\"url\":\"https://example.com\"}")));
    }
    @Test public void sensitiveActionsNeedConfirmation() throws Exception {
        assertTrue(ActionValidator.isSensitive(j("{\"type\":\"whatsapp_send\",\"to\":\"A\",\"text\":\"hi\"}")));
        assertTrue(ActionValidator.isSensitive(j("{\"type\":\"call\",\"who\":\"A\"}")));
        assertTrue(ActionValidator.isSensitive(j("{\"type\":\"click_text\",\"text\":\"Send\"}")));
        assertTrue(ActionValidator.isSensitive(j("{\"type\":\"uninstall_app\",\"name\":\"X\"}")));
        assertFalse(ActionValidator.isSensitive(j("{\"type\":\"click_text\",\"text\":\"Search\"}")));
    }
    @Test public void atLeast40Capabilities() {
        assertTrue(ActionRegistry.publicCount() >= 40);
    }
}
