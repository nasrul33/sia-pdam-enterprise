package id.pdam.sia.customer.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCustomerRequest(
        @NotBlank
        @Size(max = 64)
        String customerNumber,

        @NotBlank
        @Size(max = 255)
        String fullName,

        @Size(max = 64)
        String identityNumber,

        @Size(max = 64)
        String phoneNumber,

        @NotBlank
        String addressLine,

        @NotBlank
        @Size(max = 64)
        String areaCode,

        @DecimalMin("-90.0000000")
        @DecimalMax("90.0000000")
        @Digits(integer = 3, fraction = 7)
        BigDecimal latitude,

        @DecimalMin("-180.0000000")
        @DecimalMax("180.0000000")
        @Digits(integer = 3, fraction = 7)
        BigDecimal longitude,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
