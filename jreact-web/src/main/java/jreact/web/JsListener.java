package jreact.web;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** A JS function taking a DOM event. */
@JSFunctor
public interface JsListener extends JSObject {
    void handle(JsEvent event);
}
