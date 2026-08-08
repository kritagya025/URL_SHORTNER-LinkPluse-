package com.urlshortener.controller;

import com.urlshortener.service.UrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;

    @GetMapping("/{shortCode:[a-zA-Z0-9]{1,10}}")
    public ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String shortCode) {
        String originalUrl = urlService.getOriginalUrlAndLogClick(shortCode);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));

        // Return 302 Found (or 307 Temporary Redirect) to instruct browser to perform redirect
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
