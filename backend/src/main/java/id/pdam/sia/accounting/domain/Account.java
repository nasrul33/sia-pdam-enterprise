package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountType type;

    protected Account() {
    }

    public Account(String code, String name, AccountType type) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("ACCOUNT_CODE_REQUIRED", "Account code is required.");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("ACCOUNT_NAME_REQUIRED", "Account name is required.");
        }
        if (type == null) {
            throw new BusinessException("ACCOUNT_TYPE_REQUIRED", "Account type is required.");
        }
        this.code = code.trim();
        this.name = name.trim();
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public NormalBalance getNormalBalance() {
        return type.normalBalance();
    }
}
