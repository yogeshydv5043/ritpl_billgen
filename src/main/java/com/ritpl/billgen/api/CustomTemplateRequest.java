package com.ritpl.billgen.api;

import com.ritpl.billgen.service.CustomTemplateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomTemplateRequest(@NotBlank @Size(max=100) String name,
        @NotBlank @Pattern(regexp="CLASSIC|MODERN|MINIMAL") String styleKey,
        @Pattern(regexp="^(#[0-9a-fA-F]{6})?$", message="Use a six-digit hex color") String accentColor,
        boolean active) {
    public CustomTemplateCommand toCommand() { return new CustomTemplateCommand(name, styleKey, accentColor, active); }
}
