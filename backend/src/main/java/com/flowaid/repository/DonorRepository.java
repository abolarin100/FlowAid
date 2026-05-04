package com.flowaid.repository;

import com.flowaid.model.Donor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorRepository extends JpaRepository<Donor, UUID> {
    Optional<Donor> findByEmail(String email);
    Optional<Donor> findByStripeCustomerId(String stripeCustomerId);
    boolean existsByEmail(String email);
}
