package com.ritpl.billgen.service;

import java.math.BigDecimal;

public record DashboardStats(long invoiceCount, long customerCount, long overdueCount,
                             BigDecimal paidRevenue, BigDecimal outstanding) implements java.io.Serializable { }
