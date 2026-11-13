package dev.jreact.spring;

import dev.jreact.api.RpcException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bean Validation failures of a {@code @Validated} {@code @ServerApi} bean as a 422 with a message per field.
 * Kept apart from {@link RpcController} so that the starter works without jakarta.validation on the classpath.
 */
final class ConstraintViolations {
    private ConstraintViolations() {}

    static boolean isViolation(Throwable e) { return e instanceof ConstraintViolationException; }

    /**
     * Field names are the property path without the method and parameter: {@code add.input.title} is {@code title},
     * {@code add.input.address.city} is {@code address.city}; a rule on the parameter itself ({@code add.title}) is {@code title}.
     */
    static RpcException toRpcException(Throwable e) {
        Map<String, String> fields = new LinkedHashMap<>();
        StringBuilder message = new StringBuilder();
        for (ConstraintViolation<?> v : ((ConstraintViolationException) e).getConstraintViolations()) {
            String field = fieldOf(v.getPropertyPath());
            if (fields.putIfAbsent(field, v.getMessage()) != null) continue;      // the first message of a field is enough
            if (message.length() > 0) message.append("; ");
            // a message written as a sentence ("The title must be...") stands alone; the default ones ("must not be blank") need the field
            boolean sentence = !v.getMessage().isEmpty() && Character.isUpperCase(v.getMessage().charAt(0));
            message.append(field.isEmpty() || sentence ? "" : field + " ").append(v.getMessage());
        }
        return new RpcException(422, message.length() == 0 ? "The input is not valid." : message.toString(), fields);
    }

    private static String fieldOf(Path path) {
        StringBuilder name = new StringBuilder();
        boolean sawParameter = false;
        for (Path.Node node : path) {
            switch (node.getKind()) {
                case METHOD, CONSTRUCTOR, RETURN_VALUE, CROSS_PARAMETER -> {}
                case PARAMETER -> {
                    sawParameter = true;
                    name.append(node.getName());
                }
                default -> {
                    if (node.getName() == null) break;
                    if (name.length() > 0 && sawParameter) name.append('.');
                    name.append(node.getName());
                }
            }
        }
        // add.input.title -> input.title -> title: a record parameter is the form, its components are the fields
        String full = name.toString();
        int dot = full.indexOf('.');
        return dot < 0 ? full : full.substring(dot + 1);
    }
}
