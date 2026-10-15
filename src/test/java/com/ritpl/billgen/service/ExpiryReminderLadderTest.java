package com.ritpl.billgen.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.NotificationSetting;

/**
 * The reminder ladder is the part of the expiry notice an administrator can change, so what it must not do
 * is drift into silence: an interval longer than the lead time still has to warn, and the day before
 * expiry is never skipped because the arithmetic ran out.
 */
class ExpiryReminderLadderTest {

    @Test
    void theSeededConfigurationReproducesTheLadderTheJobAlwaysUsed() {
        assertArrayEquals(new int[] {7, 3, 1},
                SubscriptionExpiryNotificationService.reminderOffsets(setting(7, 4)));
        assertArrayEquals(new int[] {7, 3, 1}, SubscriptionExpiryNotificationService.reminderOffsets(null),
                "an unseeded database keeps behaving the way it did before the setting existed");
    }

    @Test
    void everyIntervalEndsWithTheDayBeforeExpiry() {
        assertArrayEquals(new int[] {1}, SubscriptionExpiryNotificationService.reminderOffsets(setting(1, 0)));
        assertArrayEquals(new int[] {2, 1}, SubscriptionExpiryNotificationService.reminderOffsets(setting(2, 0)));
        assertArrayEquals(new int[] {14, 7, 1}, SubscriptionExpiryNotificationService.reminderOffsets(setting(14, 7)));
        assertArrayEquals(new int[] {10, 7, 4, 1}, SubscriptionExpiryNotificationService.reminderOffsets(setting(10, 3)));
        assertArrayEquals(new int[] {5, 1}, SubscriptionExpiryNotificationService.reminderOffsets(setting(5, 90)),
                "an interval longer than the lead time warns once, then on the last day");
    }

    @Test
    void aLeadTimeOfZeroMeansTheExpiryNoticeAndNothingBeforeIt() {
        assertEquals(0, SubscriptionExpiryNotificationService.reminderOffsets(setting(0, 0)).length,
                "zero means "when it happens", which is the expiry notice's own job");
    }

    @Test
    void aWideLeadTimeIsCappedRatherThanLoopingForever() {
        int[] offsets = SubscriptionExpiryNotificationService.reminderOffsets(setting(365, 1));
        assertTrue(offsets.length <= 32, "a daily reminder for a year is a lot of notices, so it is bounded");
        assertEquals(1, offsets[offsets.length - 1], "and it still ends the day before expiry");
        assertEquals(365, offsets[0]);
    }

    private static NotificationSetting setting(int daysBefore, int repeatInterval) {
        NotificationSetting row = new NotificationSetting();
        row.setNotificationCode("SUBSCRIPTION_EXPIRY_WARNING");
        row.setNotificationName("Subscription expiry warning");
        row.setStatus(ContentStatus.ACTIVE);
        row.setInAppEnabled(true);
        row.setTriggerDaysBefore(daysBefore);
        row.setRepeatIntervalDays(repeatInterval);
        return row;
    }
}
