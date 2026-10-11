package com.ritpl.billgen.service;

public class ResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ResourceNotFoundException(String message) { super(message); }
}
