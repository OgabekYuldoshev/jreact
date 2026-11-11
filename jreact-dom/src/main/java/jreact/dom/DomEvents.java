package jreact.dom;

import jreact.scheduler.Priority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mapping between JSX handler names ({@code onClick}) and native event types ({@code click}). */
final class DomEvents {
    private DomEvents() {}

    private static final String[][] TABLE = {
            {"click", "onClick"}, {"dblclick", "onDoubleClick"}, {"contextmenu", "onContextMenu"},
            {"mousedown", "onMouseDown"}, {"mouseup", "onMouseUp"}, {"mousemove", "onMouseMove"},
            {"mouseover", "onMouseOver"}, {"mouseout", "onMouseOut"},
            {"pointerdown", "onPointerDown"}, {"pointerup", "onPointerUp"},
            {"pointermove", "onPointerMove"}, {"pointercancel", "onPointerCancel"},
            {"touchstart", "onTouchStart"}, {"touchend", "onTouchEnd"}, {"touchmove", "onTouchMove"},
            {"keydown", "onKeyDown"}, {"keyup", "onKeyUp"}, {"keypress", "onKeyPress"},
            {"input", "onInput"}, {"submit", "onSubmit"}, {"reset", "onReset"},
            {"focusin", "onFocus"}, {"focusout", "onBlur"},
            {"scroll", "onScroll"}, {"wheel", "onWheel"},
            {"copy", "onCopy"}, {"cut", "onCut"}, {"paste", "onPaste"},
            {"dragstart", "onDragStart"}, {"drag", "onDrag"}, {"dragenter", "onDragEnter"},
            {"dragleave", "onDragLeave"}, {"dragover", "onDragOver"}, {"dragend", "onDragEnd"}, {"drop", "onDrop"},
    };

    private static final Map<String, String> HANDLER_BY_TYPE = new HashMap<>();
    private static final Map<String, String> TYPE_BY_HANDLER = new HashMap<>();
    static {
        for (String[] row : TABLE) { HANDLER_BY_TYPE.put(row[0], row[1]); TYPE_BY_HANDLER.put(row[1], row[0]); }
    }

    /** Direct input: handled as USER_BLOCKING so the UI answers without waiting behind background work. */
    private static final Set<String> DISCRETE = Set.of(
            "click", "dblclick", "contextmenu", "mousedown", "mouseup", "pointerdown", "pointerup",
            "touchstart", "touchend", "keydown", "keyup", "keypress", "input", "change", "submit", "reset",
            "focusin", "focusout", "copy", "cut", "paste", "drop", "dragstart", "dragend");

    /** Native event types to listen to so that handler {@code name} can fire. */
    static List<String> nativeTypes(String handlerName) {
        if (handlerName.equals("onChange")) return List.of("input", "change");
        String type = TYPE_BY_HANDLER.get(handlerName);
        if (type != null) return List.of(type);
        return List.of(handlerName.substring(2).toLowerCase());     // onFoo -> foo
    }

    /**
     * Handler names to call for a native event. {@code onChange} follows React: text-like inputs fire it while
     * typing ("input"), checkboxes, radios, selects and files when committed ("change").
     */
    static List<String> handlerNames(String nativeType, String tag, String inputType) {
        boolean textLike = isTextLike(tag, inputType);
        switch (nativeType) {
            case "input": return textLike ? List.of("onInput", "onChange") : List.of("onInput");
            case "change": return textLike ? List.of() : List.of("onChange");
            default: {
                String name = HANDLER_BY_TYPE.get(nativeType);
                if (name == null) name = "on" + Character.toUpperCase(nativeType.charAt(0)) + nativeType.substring(1);
                return List.of(name);
            }
        }
    }

    private static boolean isTextLike(String tag, String inputType) {
        if ("textarea".equals(tag)) return true;
        if (!"input".equals(tag)) return false;
        return inputType == null || !Set.of("checkbox", "radio", "file", "range", "color").contains(inputType);
    }

    static Priority priority(String nativeType) {
        return DISCRETE.contains(nativeType) ? Priority.USER_BLOCKING : Priority.NORMAL;
    }
}
