package id.pdam.sia.connection.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connection_requests")
public class ConnectionRequest extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String requestNumber;

    private UUID customerId;

    @Column(nullable = false, length = 255)
    private String applicantName;

    @Column(length = 64)
    private String phoneNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String addressLine;

    @Column(nullable = false, length = 64)
    private String areaCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectionRequestStatus status;

    private UUID tariffGroupId;

    @Column(columnDefinition = "TEXT")
    private String surveyNotes;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant decidedAt;

    @Column(length = 128)
    private String decidedBy;

    private UUID activatedConnectionId;

    protected ConnectionRequest() {
    }

    public ConnectionRequest(
            String requestNumber,
            UUID customerId,
            String applicantName,
            String phoneNumber,
            String addressLine,
            String areaCode,
            UUID tariffGroupId
    ) {
        this.requestNumber = require(requestNumber, "CONNECTION_REQUEST_NUMBER_REQUIRED", "Connection request number is required.");
        this.customerId = customerId;
        this.applicantName = require(applicantName, "CONNECTION_REQUEST_APPLICANT_REQUIRED", "Connection request applicant is required.");
        this.phoneNumber = normalize(phoneNumber);
        this.addressLine = require(addressLine, "CONNECTION_REQUEST_ADDRESS_REQUIRED", "Connection request address is required.");
        this.areaCode = require(areaCode, "CONNECTION_REQUEST_AREA_REQUIRED", "Connection request area is required.");
        this.tariffGroupId = tariffGroupId;
        this.status = ConnectionRequestStatus.SUBMITTED;
        this.requestedAt = Instant.now();
    }

    public void survey(String notes) {
        if (status != ConnectionRequestStatus.SUBMITTED) {
            throw new BusinessException("CONNECTION_REQUEST_SURVEY_STATUS_INVALID", "Only submitted request can be surveyed.");
        }
        this.status = ConnectionRequestStatus.SURVEYED;
        this.surveyNotes = require(notes, "CONNECTION_REQUEST_SURVEY_NOTES_REQUIRED", "Survey notes are required.");
    }

    public void approve(String reason, String actor) {
        if (status != ConnectionRequestStatus.SUBMITTED && status != ConnectionRequestStatus.SURVEYED) {
            throw new BusinessException("CONNECTION_REQUEST_APPROVE_STATUS_INVALID", "Only submitted or surveyed request can be approved.");
        }
        this.status = ConnectionRequestStatus.APPROVED;
        this.decisionReason = require(reason, "CONNECTION_REQUEST_DECISION_REASON_REQUIRED", "Decision reason is required.");
        this.decidedAt = Instant.now();
        this.decidedBy = require(actor, "CONNECTION_REQUEST_DECIDED_BY_REQUIRED", "Decision actor is required.");
    }

    public void reject(String reason, String actor) {
        if (status == ConnectionRequestStatus.ACTIVATED) {
            throw new BusinessException("CONNECTION_REQUEST_REJECT_STATUS_INVALID", "Activated request cannot be rejected.");
        }
        this.status = ConnectionRequestStatus.REJECTED;
        this.decisionReason = require(reason, "CONNECTION_REQUEST_DECISION_REASON_REQUIRED", "Decision reason is required.");
        this.decidedAt = Instant.now();
        this.decidedBy = require(actor, "CONNECTION_REQUEST_DECIDED_BY_REQUIRED", "Decision actor is required.");
    }

    public void activate(UUID connectionId, String actor) {
        if (status != ConnectionRequestStatus.APPROVED) {
            throw new BusinessException("CONNECTION_REQUEST_ACTIVATE_STATUS_INVALID", "Only approved request can be activated.");
        }
        if (connectionId == null) {
            throw new BusinessException("CONNECTION_REQUEST_CONNECTION_REQUIRED", "Activated connection id is required.");
        }
        this.status = ConnectionRequestStatus.ACTIVATED;
        this.activatedConnectionId = connectionId;
        this.decidedAt = Instant.now();
        this.decidedBy = require(actor, "CONNECTION_REQUEST_DECIDED_BY_REQUIRED", "Decision actor is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getRequestNumber() {
        return requestNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public ConnectionRequestStatus getStatus() {
        return status;
    }

    public UUID getTariffGroupId() {
        return tariffGroupId;
    }

    public String getSurveyNotes() {
        return surveyNotes;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public UUID getActivatedConnectionId() {
        return activatedConnectionId;
    }
}
