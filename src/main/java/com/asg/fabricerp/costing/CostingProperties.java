package com.asg.fabricerp.costing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials come from config or a secret store, never from a page.
 *
 * In asgdynamic the costing user and password were embedded in the client-side JavaScript
 * of the Booking and BPO screens, readable by anyone who could open them.
 */
@ConfigurationProperties(prefix = "asg.costing")
public record CostingProperties(String baseUrl, String user, String password) {}
