package jreact.dom;

import jreact.Css;

import java.util.Map;
import java.util.Set;

/** Pure rules that turn JSX props into DOM attributes, properties and styles. */
final class DomAttributes {
    private DomAttributes() {}

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("className", "class"), Map.entry("htmlFor", "for"),
            Map.entry("readOnly", "readonly"), Map.entry("tabIndex", "tabindex"),
            Map.entry("maxLength", "maxlength"), Map.entry("minLength", "minlength"),
            Map.entry("autoFocus", "autofocus"), Map.entry("autoComplete", "autocomplete"),
            Map.entry("autoPlay", "autoplay"), Map.entry("playsInline", "playsinline"),
            Map.entry("colSpan", "colspan"), Map.entry("rowSpan", "rowspan"),
            Map.entry("contentEditable", "contenteditable"), Map.entry("spellCheck", "spellcheck"),
            Map.entry("crossOrigin", "crossorigin"), Map.entry("srcSet", "srcset"),
            Map.entry("acceptCharset", "accept-charset"), Map.entry("httpEquiv", "http-equiv"),
            Map.entry("encType", "enctype"), Map.entry("noValidate", "novalidate"),
            Map.entry("formNoValidate", "formnovalidate"), Map.entry("allowFullScreen", "allowfullscreen"));

    /** Live state of a form control: must be set as a property, the attribute only holds the initial value. */
    private static final Set<String> PROPERTIES = Set.of("value", "checked", "selected", "indeterminate");

    private static final Set<String> BOOLEAN_ATTRIBUTES = Set.of(
            "allowfullscreen", "async", "autofocus", "autoplay", "controls", "default", "defer", "disabled",
            "formnovalidate", "hidden", "inert", "loop", "multiple", "muted", "nomodule", "novalidate", "open",
            "playsinline", "readonly", "required", "reversed", "itemscope");

    static boolean isProperty(String name) { return PROPERTIES.contains(name); }

    static String attributeName(String prop) { return ALIASES.getOrDefault(prop, prop); }

    static boolean isBooleanAttribute(String attribute) { return BOOLEAN_ATTRIBUTES.contains(attribute); }

    /** {@code checked}/{@code selected}/{@code indeterminate} are booleans, {@code value} is text ("" when null). */
    static Object propertyValue(String name, Object value) {
        if (name.equals("value")) return value == null ? "" : String.valueOf(value);
        return value instanceof Boolean b ? b : value != null;
    }

    /** Style object (a Map of camelCase name to value) to CSS names and values. Null entries are dropped. */
    static Map<String, String> styleMap(Object style) { return Css.styleMap(style); }

    static String cssName(String name) { return Css.cssName(name); }

    static String cssValue(String cssName, Object value) { return Css.cssValue(cssName, value); }
}
