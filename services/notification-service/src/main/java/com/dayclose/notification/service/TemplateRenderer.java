package com.dayclose.notification.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple template renderer using Handlebars-style {{variable}} syntax.
 */
@Component
public class TemplateRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Render a template with given data.
     * Example: "Hello {{name}}!" with {name: "World"} -> "Hello World!"
     */
    public String render(String template, Map<String, Object> data) {
        if (template == null || data == null) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = data.get(varName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
