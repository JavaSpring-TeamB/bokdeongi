package com.bokdeongi.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsentRequest(

        @NotBlank
        @Size(max = 15)
        String consentType,

        @NotBlank
        @Size(max = 20)
        String docVersion,

        @NotNull
        Boolean agreed

) {
}