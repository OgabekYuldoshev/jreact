package app;

import java.util.List;

/** A pricing plan. Prices are dollars per month; {@code yearly} is the per-month price when billed annually. */
public record Plan(String name, String tagline, int monthly, int yearly, boolean featured, List<String> features) {

    static final List<Plan> ALL = List.of(
        new Plan("Starter", "For side projects", 12, 10, false,
                 List.of("3 projects", "Basic automations", "Community support")),
        new Plan("Pro", "For growing teams", 39, 31, true,
                 List.of("Unlimited projects", "Advanced automations", "Priority support", "Audit log")),
        new Plan("Scale", "For big organizations", 99, 79, false,
                 List.of("Everything in Pro", "SSO and SAML", "99.9% uptime SLA", "Dedicated manager"))
    );
}
