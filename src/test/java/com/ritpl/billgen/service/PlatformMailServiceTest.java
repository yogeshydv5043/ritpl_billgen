package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.Customer;
import com.ritpl.billgen.entity.DeliveryStatus;
import com.ritpl.billgen.entity.NotificationDelivery;
import com.ritpl.billgen.entity.NotificationSetting;
import com.ritpl.billgen.repository.NotificationDeliveryRepository;

/**
 * The promises a notice by email has to keep: a muted notice writes nothing, a retried event sends nothing
 * twice, and a transport that did not accept the message is never recorded as though it had. These are the
 * behaviours an administrator reads off the delivery history, so they are tested against the transport
 * rather than assumed from it.
 */
class PlatformMailServiceTest {

    private static final String CODE = "SUBSCRIPTION_EXPIRY_WARNING";
    private static final String KEY = CODE + ":11:7";

    private final NotificationSettingService settings = mock(NotificationSettingService.class);
    private final NotificationDeliveryRepository deliveries = mock(NotificationDeliveryRepository.class);
    private final EmailSink sink = mock(EmailSink.class);
    private final PlatformMailService mail = new PlatformMailService(settings, deliveries, sink);
    private final Customer customer = new Customer("Acme Traders", "Owner", "accounts@acme.test");

    @Test
    void aMutedOrInAppOnlyNoticeSendsNothingAndWritesNothing() throws Exception {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.INACTIVE, true)));
        assertFalse(mail.email(CODE, customer, "Subject", "Body", KEY).attempted());

        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, false)));
        assertFalse(mail.email(CODE, customer, "Subject", "Body", KEY).attempted());

        when(settings.find(CODE)).thenReturn(Optional.empty());
        assertFalse(mail.email(CODE, customer, "Subject", "Body", KEY).attempted());

        verify(deliveries, never()).save(any());
        verify(sink, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void anAccountWithNoEmailAddressIsNotAnErrorJustANoticeThatCannotBeEmailed() {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, true)));
        Customer nobody = new Customer("Acme Traders", "Owner", null);
        PlatformMailService.Outcome outcome = mail.email(CODE, nobody, "Subject", "Body", KEY);
        assertFalse(outcome.attempted());
        assertTrue(outcome.detail().contains("no email address"), outcome.detail());
        verify(deliveries, never()).save(any());
    }

    @Test
    void aSecondDeliveryForTheSameEventSendsNothingEvenWhenTheTransportIsHappy() throws Exception {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, true)));
        when(deliveries.findByIdempotencyKey(KEY)).thenReturn(Optional.of(mock(NotificationDelivery.class)));
        when(sink.isLive()).thenReturn(true);

        PlatformMailService.Outcome outcome = mail.email(CODE, customer, "Subject", "Body", KEY);
        assertFalse(outcome.attempted(), "a retry is not a second send");
        assertTrue(outcome.detail().contains("already sent"), outcome.detail());
        verify(sink, never()).send(anyString(), anyString(), anyString());
        verify(deliveries, never()).save(any());
    }

    @Test
    void aTransportThatOnlyRecordsIsStoredAsSimulatedRatherThanSent() {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, true)));
        when(sink.isLive()).thenReturn(false);
        when(sink.describe()).thenReturn("Recording only (no mail transport configured)");

        PlatformMailService.Outcome outcome = mail.email(CODE, customer, "Expires soon", "Body", KEY);
        assertTrue(outcome.attempted());
        assertEquals(DeliveryStatus.SIMULATED, outcome.status(), "the history must not claim a delivery nobody made");
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries).save(saved.capture());
        NotificationDelivery delivery = saved.getValue();
        assertEquals("accounts@acme.test", delivery.getRecipient());
        assertEquals(CODE, delivery.getTriggerEvent(), "the history says which notice this was");
        assertEquals("Subscription expiry warning", delivery.getNotificationName());
        assertEquals(KEY, delivery.getIdempotencyKey());
        assertNull(delivery.getFailureReason(), "a simulated delivery has no failure to explain");
    }

    @Test
    void aRefusedMessageIsRecordedAsFailedWithTheReason() throws Exception {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, true)));
        when(sink.isLive()).thenReturn(true);
        when(sink.describe()).thenReturn("SMTP (smtp.example.test)");
        doThrow(new IllegalStateException("535 5.7.8 Username and Password not accepted"))
                .when(sink).send(eq("accounts@acme.test"), anyString(), anyString());

        PlatformMailService.Outcome outcome = mail.email(CODE, customer, "Subject", "Body", KEY);
        assertTrue(outcome.attempted());
        assertEquals(DeliveryStatus.FAILED, outcome.status());
        ArgumentCaptor<NotificationDelivery> saved = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveries).save(saved.capture());
        assertEquals(DeliveryStatus.FAILED, saved.getValue().getStatus());
        assertTrue(saved.getValue().getFailureReason().contains("5.7.8"),
                "the administrator sees why, not just that it failed");
    }

    @Test
    void anAcceptedMessageIsRecordedAsSent() throws Exception {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, true)));
        when(sink.isLive()).thenReturn(true);
        when(sink.describe()).thenReturn("SMTP (smtp.example.test)");

        PlatformMailService.Outcome outcome = mail.email(CODE, customer, "Subject", "Body", KEY);
        assertTrue(outcome.delivered());
        verify(sink).send("accounts@acme.test", "Subject", "Body");
    }

    @Test
    void theChannelFlagsOnTheSettingDecideWhetherAnInboxEntryIsWritten() {
        when(settings.find(CODE)).thenReturn(Optional.of(setting(ContentStatus.ACTIVE, true)));
        assertTrue(mail.deliversInApp(CODE));
        NotificationSetting emailOnly = setting(ContentStatus.ACTIVE, true);
        emailOnly.setInAppEnabled(false);
        when(settings.find(CODE)).thenReturn(Optional.of(emailOnly));
        assertFalse(mail.deliversInApp(CODE), "in-app only is a real setting, not a display choice");
        when(settings.find("UNCONFIGURED_NOTICE")).thenReturn(Optional.empty());
        assertTrue(mail.deliversInApp("UNCONFIGURED_NOTICE"), "a missing row keeps the notice's own behaviour");
        assertFalse(mail.muted("UNCONFIGURED_NOTICE"), "an absent row is not somebody having muted the notice");
    }

    private static NotificationSetting setting(ContentStatus status, boolean emailEnabled) {
        NotificationSetting row = new NotificationSetting();
        row.setNotificationCode(CODE);
        row.setNotificationName("Subscription expiry warning");
        row.setStatus(status);
        row.setEmailEnabled(emailEnabled);
        row.setInAppEnabled(true);
        return row;
    }
}
