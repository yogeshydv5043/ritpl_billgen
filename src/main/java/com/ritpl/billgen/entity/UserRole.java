package com.ritpl.billgen.entity;

/**
 * The application has exactly two roles.
 *
 * <ul>
 *   <li>{@code ADMIN} - manages accounts, plans and templates and can read every customer record.</li>
 *   <li>{@code CUSTOMER} - a tenant account that owns its own invoices, products and categories and
 *       can only use the templates included in the plan it purchased.</li>
 * </ul>
 */
public enum UserRole {
    ADMIN, CUSTOMER
}