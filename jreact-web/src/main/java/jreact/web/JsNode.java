package jreact.web;

import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/** A DOM Node or Element, only the members jreact needs. */
public interface JsNode extends JSObject {

    @JSMethod JsNode appendChild(JsNode child);

    @JSMethod JsNode insertBefore(JsNode child, JsNode before);

    @JSMethod JsNode removeChild(JsNode child);

    @JSMethod void setAttribute(String name, String value);

    @JSMethod void removeAttribute(String name);

    @JSMethod void addEventListener(String type, JsListener listener, boolean capture);

    @JSProperty("parentNode") JsNode getParentNode();

    @JSProperty("textContent") void setTextContent(String text);

    @JSProperty("textContent") String getTextContent();
}
