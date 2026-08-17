package com.packid.api.controller.tenant;

import com.packid.api.controller.tenant.dto.PublicTenantResponse;
import com.packid.api.domain.repository.TenantRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/tenants")
public class PublicTenantController {

    private final TenantRepository tenantRepository;

    public PublicTenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public List<PublicTenantResponse> listActiveTenants() {
        return tenantRepository.findAllByActiveTrueAndDeletedFalseOrderByNameAsc()
                .stream()
                .map(tenant -> new PublicTenantResponse(tenant.getName(), tenant.getSlug()))
                .toList();
    }
}
