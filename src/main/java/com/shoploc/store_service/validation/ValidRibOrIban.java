package com.shoploc.store_service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = RibIbanValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRibOrIban {

    String message() default "Le format ou la clé algorithmique du RIB/IBAN est invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
