package jreact.web;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/** A DOM Event. */
public interface JsEvent extends JSObject {

    @JSProperty("type") String getType();

    @JSProperty("target") JsNode getTarget();

    @JSMethod void preventDefault();
}
