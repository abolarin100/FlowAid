package com.flowaid.service;

import com.flowaid.dto.DonorDto;
import com.flowaid.exception.ResourceNotFoundException;
import com.flowaid.model.Donor;
import com.flowaid.repository.DonorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class DonorService {

    private final DonorRepository donorRepository;
    private final DashboardService dashboardService;

    public DonorService(DonorRepository donorRepository,
            @Lazy DashboardService dashboardService) {
        this.donorRepository = donorRepository;
        this.dashboardService = dashboardService;
    }

    @Transactional(readOnly = true)
    public Page<DonorDto.Response> listDonors(Pageable pageable) {
        return donorRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DonorDto.Response getById(UUID id) {
        return donorRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Donor", id));
    }

    @Transactional
    public DonorDto.Response create(DonorDto.CreateRequest request) {
        donorRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "A donor with email " + request.getEmail() + " already exists");
        });

        Donor donor = Donor.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .organizationName(request.getOrganizationName())
                .isRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false)
                .totalDonatedUsd(BigDecimal.ZERO)
                .stripeCustomerId(request.getStripeCustomerId())
                .build();

        Donor saved = donorRepository.save(donor);
        log.info("Donor registered: {} ({})", saved.getId(), saved.getEmail());
        dashboardService.evictCache();
        return toResponse(saved);
    }

    private DonorDto.Response toResponse(Donor d) {
        return DonorDto.Response.builder()
                .id(d.getId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .email(d.getEmail())
                .organizationName(d.getOrganizationName())
                .donorTier(d.getDonorTier())
                .totalDonatedUsd(d.getTotalDonatedUsd())
                .isRecurring(d.getIsRecurring())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}