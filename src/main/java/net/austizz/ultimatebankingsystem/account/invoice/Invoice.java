package net.austizz.ultimatebankingsystem.account.invoice;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class Invoice {
    private final UUID CustomerId;
    private final UUID BusinessId;
    private final LocalDateTime DateOfInvoice;
    private final LocalDateTime DueDate;
    private final int InvoiceNumber;
    private final ConcurrentHashMap<String, BigDecimal> ItemizedList;
    private final int taxPercentage;

    public Invoice(UUID customerId , UUID businessId, int DueInDays, int InvoiceNumber, ConcurrentHashMap<String, BigDecimal> ItemizedList, int taxPercentage) {
        this.CustomerId = customerId;
        this.BusinessId = businessId;
        this.DateOfInvoice = LocalDateTime.now();
        this.DueDate = this.DateOfInvoice.plusDays(DueInDays);
        this.InvoiceNumber = InvoiceNumber;
        this.ItemizedList = ItemizedList;
        this.taxPercentage = taxPercentage;
        



    }
}
