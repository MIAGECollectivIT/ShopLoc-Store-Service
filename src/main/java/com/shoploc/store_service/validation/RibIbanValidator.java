package com.shoploc.store_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RibIbanValidator implements ConstraintValidator<ValidRibOrIban, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return RibIbanHelper.isValidRibOrIban(value);
    }
}
