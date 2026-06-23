package io.livelattice.notifications.template;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.stereotype.Service;

@Service
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    private final TemplateCatalog catalog;
    private final TemplateEngine templateEngine;

    public TemplateRenderer(TemplateCatalog catalog, TemplateEngine templateEngine) {
        this.catalog = catalog;
        this.templateEngine = templateEngine;
    }

    @PostConstruct
    void warmEmailTemplates() {
        Context context = new Context();
        context.setVariable("title", "");
        context.setVariable("body", "");
        context.setVariable("actionUrl", "");
        context.setVariable("notifications", java.util.List.of());
        catalog.types().stream()
            .map(type -> catalog.template(type).emailTemplate())
            .distinct()
            .forEach(template -> templateEngine.process(template, context));
        templateEngine.process("email/digest", context);
    }

    public RenderedNotification render(NotificationTemplate template, String explicitTitle, String explicitBody, String explicitActionUrl, Map<String, Object> data) {
        Map<String, Object> safeData = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        String title = notBlank(explicitTitle) ? explicitTitle : interpolate(template.title(), safeData);
        String body = notBlank(explicitBody) ? explicitBody : interpolate(template.body(), safeData);
        String actionUrl = notBlank(explicitActionUrl) ? explicitActionUrl : interpolate(template.actionUrl(), safeData);
        return new RenderedNotification(title, body, blankToNull(actionUrl), safeData);
    }

    public String renderEmail(NotificationTemplate template, RenderedNotification rendered) {
        Context context = new Context();
        context.setVariable("title", rendered.title());
        context.setVariable("body", rendered.body());
        context.setVariable("actionUrl", rendered.actionUrl());
        context.setVariables(rendered.data());
        return templateEngine.process(template.emailTemplate(), context);
    }

    public String renderDigest(Iterable<RenderedNotification> notifications) {
        Context context = new Context();
        context.setVariable("notifications", notifications);
        return templateEngine.process("email/digest", context);
    }

    private String interpolate(String value, Map<String, Object> data) {
        if (value == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            Object replacement = data.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement == null ? "" : replacement.toString()));
        }
        matcher.appendTail(output);
        return output.toString().trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
