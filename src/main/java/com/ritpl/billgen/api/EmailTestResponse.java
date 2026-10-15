package com.ritpl.billgen.api;

/** The test either says it was accepted by the server or explains, in a sentence, why it was not. */
public record EmailTestResponse(boolean sent, String message) { }
