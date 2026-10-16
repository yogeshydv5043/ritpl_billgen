package com.ritpl.billgen.api;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ritpl.billgen.entity.AppInfo;
import com.ritpl.billgen.entity.ContentStatus;
import com.ritpl.billgen.entity.Faq;
import com.ritpl.billgen.entity.FaqType;
import com.ritpl.billgen.entity.NotificationSetting;
import com.ritpl.billgen.entity.PolicyPage;
import com.ritpl.billgen.entity.PolicyType;
import com.ritpl.billgen.entity.Review;
import com.ritpl.billgen.security.AppUserPrincipal;
import com.ritpl.billgen.security.TenantScope;
import com.ritpl.billgen.service.AppInfoCommand;
import com.ritpl.billgen.service.AppInfoService;
import com.ritpl.billgen.service.EmailSettingsCommand;
import com.ritpl.billgen.service.EmailSettingsService;
import com.ritpl.billgen.service.FaqCommand;
import com.ritpl.billgen.service.FaqService;
import com.ritpl.billgen.service.FaqTypeCommand;
import com.ritpl.billgen.service.FaqTypeService;
import com.ritpl.billgen.service.NotificationSettingCommand;
import com.ritpl.billgen.service.NotificationSettingService;
import com.ritpl.billgen.service.PolicyPageCommand;
import com.ritpl.billgen.service.PolicyPageService;
import com.ritpl.billgen.service.ReviewCommand;
import com.ritpl.billgen.service.ReviewService;
import jakarta.validation.Valid;

/**
 * The configuration surface of the product about itself: branding and support details, the legal pages,
 * customer reviews, FAQs, the mail transport and which notices the platform sends.
 *
 * <p>Every route is administrator-only, and the check is in the services rather than only here: these are
 * rows every tenant sees, so a credential that is not an administrator's is refused with a 403 for the
 * same reason a customer cannot reach an administrative screen. Nothing in this controller invents a
 * second storage path - it maps a request body onto the command the service already defines.</p>
 */
@RestController
@RequestMapping("/api/admin/app-information")
public class AdminAppInformationController {

    private final AppInfoService appInfo;
    private final PolicyPageService policies;
    private final ReviewService reviews;
    private final FaqTypeService faqTypes;
    private final FaqService faqs;
    private final EmailSettingsService emailSettings;
    private final NotificationSettingService notificationSettings;

    public AdminAppInformationController(AppInfoService appInfo, PolicyPageService policies, ReviewService reviews,
            FaqTypeService faqTypes, FaqService faqs, EmailSettingsService emailSettings,
            NotificationSettingService notificationSettings) {
        this.appInfo = appInfo; this.policies = policies; this.reviews = reviews; this.faqTypes = faqTypes;
        this.faqs = faqs; this.emailSettings = emailSettings; this.notificationSettings = notificationSettings;
    }

    // ------------------------------------------------------------------ application information

    @GetMapping("/app-info")
    public AppInfoResponse appInfo(@AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        return response(appInfo.current());
    }

    @PutMapping("/app-info")
    public AppInfoResponse updateAppInfo(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody AppInfoRequest request) {
        AppInfo saved = appInfo.save(scope(principal), new AppInfoCommand(request.siteName(), request.siteTagline(),
                request.siteLogoUrl(), request.siteFaviconUrl(), request.supportEmail(), request.supportPhone(),
                request.supportWhatsapp(), request.primaryColor(), request.secondaryColor(), request.instagramUrl(),
                request.facebookUrl(), request.youtubeUrl(), request.xUrl(), request.footerDescription(),
                request.copyrightText(), request.disclaimerText()));
        return response(saved);
    }

    // ------------------------------------------------------------------ policy pages

    @GetMapping("/policies")
    public List<PolicyPageResponse> policyList(@AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        return policies.overview().stream()
                .map(row -> new PolicyPageResponse(row.id(), row.policyType(), row.policyType().getSlug(),
                        row.heading(), row.metaTitle(), row.metaDescription(), row.content(), row.status(),
                        row.publiclyVisible(), row.updatedAt()))
                .toList();
    }

    @GetMapping("/policies/{type}")
    public PolicyPageResponse policy(@PathVariable String type, @AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        PolicyType policyType = parsePolicyType(type);
        PolicyPage page = policies.find(policyType)
                .orElseThrow(() -> new com.ritpl.billgen.service.ResourceNotFoundException(
                        "No " + policyType.getDisplayName() + " page has been written yet."));
        return response(page);
    }

    @PutMapping("/policies/{type}")
    public PolicyPageResponse savePolicy(@PathVariable String type, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody PolicyPageRequest request) {
        PolicyType policyType = parsePolicyType(type);
        if (request.policyType() != null && request.policyType() != policyType) {
            throw new IllegalArgumentException("The body names " + request.policyType() + " but the address names "
                    + policyType + ".");
        }
        return response(policies.save(scope(principal), new PolicyPageCommand(policyType, request.heading(),
                request.metaTitle(), request.metaDescription(), request.pageContent(), request.status())));
    }

    @PutMapping("/policies/{type}/status")
    public PolicyPageResponse setPolicyStatus(@PathVariable String type, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ContentStatusRequest request) {
        return response(policies.setStatus(scope(principal), parsePolicyType(type), request.status()));
    }

    // ------------------------------------------------------------------ reviews

    @GetMapping("/reviews")
    public PageResponse<ReviewResponse> reviewList(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String search, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        scope(principal);
        Page<Review> found = reviews.search(blankToNull(search), parseStatus(status, "Review status"), minRating,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PageResponse.of(found, AdminAppInformationController::response);
    }

    @PostMapping("/reviews")
    public ResponseEntity<ReviewResponse> createReview(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ReviewRequest request) {
        Review saved = reviews.save(scope(principal), null, command(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response(saved));
    }

    @PutMapping("/reviews/{id}")
    public ReviewResponse updateReview(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ReviewRequest request) {
        return response(reviews.save(scope(principal), id, command(request)));
    }

    @PutMapping("/reviews/{id}/status")
    public ReviewResponse setReviewStatus(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ContentStatusRequest request) {
        return response(reviews.setStatus(scope(principal), id, request.status()));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        reviews.delete(scope(principal), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reviews/{id}/restore")
    public ReviewResponse restoreReview(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return response(reviews.restore(scope(principal), id));
    }

    @GetMapping("/reviews/counts")
    public Map<String, Long> reviewCounts(@AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        return reviews.counts();
    }

    // ------------------------------------------------------------------ FAQs

    @GetMapping("/faq-types")
    public List<FaqTypeResponse> faqTypes(@AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        Map<Long, Long> counts = faqTypes.questionCounts();
        return faqTypes.list().stream()
                .map(type -> response(type, counts.getOrDefault(type.getId(), 0L))).toList();
    }

    @PostMapping("/faq-types")
    public ResponseEntity<FaqTypeResponse> createFaqType(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody FaqTypeRequest request) {
        FaqType saved = faqTypes.save(scope(principal), new FaqTypeCommand(request.typeName(),
                request.typeCode(), request.description(), request.displayOrder(), request.status()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response(saved, 0L));
    }

    @PutMapping("/faq-types/{id}")
    public FaqTypeResponse updateFaqType(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody FaqTypeRequest request) {
        FaqType saved = faqTypes.update(scope(principal), id, request.typeName(), request.description(),
                request.displayOrder(), request.status());
        return response(saved, faqTypes.questionCount(id));
    }

    @DeleteMapping("/faq-types/{id}")
    public ResponseEntity<Void> deleteFaqType(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        faqTypes.delete(scope(principal), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/faqs")
    public PageResponse<FaqResponse> faqs(@AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String search, @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        scope(principal);
        Page<Faq> found = this.faqs.search(blankToNull(search), typeId, parseStatus(status, "FAQ status"),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PageResponse.of(found, AdminAppInformationController::response);
    }

    @PostMapping("/faqs")
    public ResponseEntity<FaqResponse> createFaq(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody FaqRequest request) {
        Faq saved = faqs.save(scope(principal), null, command(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response(saved));
    }

    @PutMapping("/faqs/{id}")
    public FaqResponse updateFaq(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody FaqRequest request) {
        return response(faqs.save(scope(principal), id, command(request)));
    }

    @PutMapping("/faqs/{id}/status")
    public FaqResponse setFaqStatus(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ContentStatusRequest request) {
        return response(faqs.setStatus(scope(principal), id, request.status()));
    }

    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        faqs.delete(scope(principal), id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ email settings

    @GetMapping("/email-settings")
    public EmailSettingsResponse emailSettings(@AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        return EmailSettingsResponse.of(emailSettings.view());
    }

    @PutMapping("/email-settings")
    public EmailSettingsResponse updateEmailSettings(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody EmailSettingsRequest request) {
        emailSettings.save(scope(principal), new EmailSettingsCommand(request.smtpHost(), request.smtpPort(),
                request.smtpUsername(), request.password(), request.authEnabled(), request.securityProtocol(),
                request.timeoutMs(), request.senderName(), request.senderEmail(), request.replyToEmail(),
                request.status()));
        return EmailSettingsResponse.of(emailSettings.view());
    }

    /** Sends one real message. A refusal is reported as a sentence, never as a stack trace or a secret. */
    @PostMapping("/email-settings/test")
    public EmailTestResponse testEmail(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody EmailTestRequest request) {
        EmailSettingsService.TestResult result = emailSettings.test(scope(principal), request.recipient());
        return new EmailTestResponse(result.sent(), result.message());
    }

    // ------------------------------------------------------------------ notification settings

    @GetMapping("/notification-settings")
    public List<NotificationSettingResponse> notificationSettings(@AuthenticationPrincipal AppUserPrincipal principal) {
        scope(principal);
        return notificationSettings.list().stream().map(AdminAppInformationController::response).toList();
    }

    @PutMapping("/notification-settings/{code}")
    public NotificationSettingResponse updateNotificationSetting(@PathVariable String code,
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody NotificationSettingRequest request) {
        return response(notificationSettings.update(scope(principal), code, new NotificationSettingCommand(
                request.notificationName(), request.description(), request.triggerDaysBefore(),
                request.repeatIntervalDays(), request.emailEnabled(), request.inAppEnabled(), request.status())));
    }

    @PutMapping("/notification-settings/{code}/status")
    public NotificationSettingResponse setNotificationStatus(@PathVariable String code,
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody ContentStatusRequest request) {
        return response(notificationSettings.setStatus(scope(principal), code, request.status()));
    }

    // ------------------------------------------------------------------ mapping and guards

    private static TenantScope scope(AppUserPrincipal principal) {
        if (principal == null) throw new AccessDeniedException("Authentication is required.");
        return TenantScope.from(principal);
    }

    private static PolicyType parsePolicyType(String value) {
        try {
            return PolicyType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException notAType) {
            throw new IllegalArgumentException("Unknown policy type: " + value
                    + ". Use " + String.join(", ", java.util.Arrays.stream(PolicyType.values())
                            .map(Enum::name).toList()) + ".");
        }
    }

    private static ContentStatus parseStatus(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return ContentStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException notAStatus) {
            throw new IllegalArgumentException(label + " must be ACTIVE or INACTIVE.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ReviewCommand command(ReviewRequest request) {
        return new ReviewCommand(request.reviewerName(), request.reviewContent(), request.rating(),
                request.location(), request.designation(), request.profileImageUrl(), request.displayOrder(),
                request.status());
    }

    private static FaqCommand command(FaqRequest request) {
        return new FaqCommand(request.faqTypeId(), request.question(), request.answer(), request.displayOrder(),
                request.status());
    }

    /** {@code configured} is false while the page still shows the branding the build shipped with. */
    private static AppInfoResponse response(AppInfo row) {
        return new AppInfoResponse(row.getUpdatedAt() != null, row.getSiteName(), row.getSiteTagline(), row.getSiteLogoUrl(),
                row.getSiteFaviconUrl(), row.getSupportEmail(), row.getSupportPhone(), row.getSupportWhatsapp(),
                row.getPrimaryColor(), row.getSecondaryColor(), row.getInstagramUrl(), row.getFacebookUrl(),
                row.getYoutubeUrl(), row.getXUrl(), row.getFooterDescription(), row.getCopyrightText(),
                row.getDisclaimerText(), row.getUpdatedAt(), row.getUpdatedBy());
    }

    private static PolicyPageResponse response(PolicyPage page) {
        return new PolicyPageResponse(page.getId(), page.getPolicyType(), page.getPolicyType().getSlug(),
                page.getHeading(), page.getMetaTitle(), page.getMetaDescription(), page.getPageContent(),
                page.getStatus(), page.isPublished(), page.getUpdatedAt());
    }

    private static ReviewResponse response(Review row) {
        return new ReviewResponse(row.getId(), row.getReviewerName(), row.getReviewContent(), row.getRating(),
                row.getLocation(), row.getDesignation(), row.getProfileImageUrl(), row.getDisplayOrder(),
                row.getStatus(), row.isDeleted(), row.getUpdatedAt(), row.getUpdatedBy());
    }

    private static FaqTypeResponse response(FaqType type, long questionCount) {
        return new FaqTypeResponse(type.getId(), type.getTypeName(), type.getTypeCode(), type.getDescription(),
                type.getDisplayOrder(), type.getStatus(), questionCount);
    }

    private static FaqResponse response(Faq row) {
        return new FaqResponse(row.getId(), row.getFaqTypeId(), row.getFaqType().getTypeCode(),
                row.getFaqType().getTypeName(), row.getQuestion(), row.getAnswer(), row.getDisplayOrder(),
                row.getStatus(), row.getUpdatedAt());
    }

    private static NotificationSettingResponse response(NotificationSetting row) {
        return new NotificationSettingResponse(row.getId(), row.getNotificationCode(), row.getNotificationName(),
                row.getDescription(), row.isEmailEnabled(), row.isInAppEnabled(), row.getTriggerDaysBefore(),
                row.getRepeatIntervalDays(), row.getStatus(), row.deliversAnything(), row.getUpdatedAt());
    }
}
