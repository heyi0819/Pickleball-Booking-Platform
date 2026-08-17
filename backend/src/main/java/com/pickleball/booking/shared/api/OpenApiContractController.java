package com.pickleball.booking.shared.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the checked-in OpenAPI contract verbatim so clients and the published endpoint share one source. */
@RestController
public class OpenApiContractController {
    @GetMapping(value = "/v3/api-docs.yaml", produces = "application/yaml")
    ResponseEntity<ClassPathResource> contract() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/yaml"))
                .body(new ClassPathResource("openapi/openapi.yaml"));
    }
}
