package jreact.web;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;

/** The DOM Document. */
public interface JsDocument extends JSObject {

    @JSMethod JsNode createElement(String tag);

    @JSMethod JsNode createTextNode(String text);

    @JSMethod JsNode getElementById(String id);
}
