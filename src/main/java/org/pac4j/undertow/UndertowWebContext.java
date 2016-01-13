/*
  Copyright 2014 - 2016 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.undertow;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;
import io.undertow.util.HttpString;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.JavaSerializationHelper;
import org.pac4j.undertow.session.UndertowSessionStore;

/**
 * The webcontext implementation for Undertow.
 *
 * @author Jerome Leleu
 * @author Michael Remond
 * @since 1.0.0
 */
public class UndertowWebContext implements WebContext {

    private final static JavaSerializationHelper JAVA_SERIALIZATION_HELPER = new JavaSerializationHelper();

    private final HttpServerExchange exchange;
    private final UndertowSessionStore sessionStore;

    public UndertowWebContext(final HttpServerExchange exchange) {
        this.exchange = exchange;
        this.sessionStore = new UndertowSessionStore(exchange.getAttachment(SessionManager.ATTACHMENT_KEY), exchange.getAttachment(SessionConfig.ATTACHMENT_KEY));
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public String getRequestParameter(final String name) {
        Deque<String> param = exchange.getQueryParameters().get(name);
        if (param != null) {
            return param.peek();
        } else {
            FormData data = exchange.getAttachment(FormDataParser.FORM_DATA);
            if (data != null && data.get(name) != null) {
                return data.get(name).peek().getValue();
            }
        }
        return null;
    }

    @Override
    public Map<String, String[]> getRequestParameters() {
        Map<String, Deque<String>> params = exchange.getQueryParameters();
        Map<String, String[]> map = new HashMap<String, String[]>();
        for (Entry<String, Deque<String>> entry : params.entrySet()) {
            map.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
        }
        FormData data = exchange.getAttachment(FormDataParser.FORM_DATA);
        if (data != null) {
            for (String key : data) {
                map.put(key, data.get(key).toArray(new String[data.get(key).size()]));
            }
        }
        return map;
    }

    @Override
    public String getRequestHeader(final String name) {
        return exchange.getRequestHeaders().get(name, 0);
    }

    @Override
    public void setSessionAttribute(final String name, final Object value) {
        sessionStore.set(this, name, value);
    }

    @Override
    public Object getSessionAttribute(final String name) {
        return sessionStore.get(this, name);
    }

    @Override
    public String getRequestMethod() {
        return exchange.getRequestMethod().toString();
    }

    @Override
    public void writeResponseContent(final String content) {
        exchange.getResponseSender().send(content);
    }

    @Override
    public void setResponseStatus(final int code) {
        exchange.setResponseCode(code);
    }

    @Override
    public void setResponseHeader(final String name, final String value) {
        exchange.getResponseHeaders().put(HttpString.tryFromString(name), value);
    }

    @Override
    public String getServerName() {
        return exchange.getHostName();
    }

    @Override
    public int getServerPort() {
        return exchange.getHostPort();
    }

    @Override
    public String getScheme() {
        return exchange.getProtocol().toString();
    }

    @Override
    public String getFullRequestURL() {
        String full = exchange.getRequestURL();
        if (CommonHelper.isNotBlank(exchange.getQueryString())) {
            full = full + "?" + exchange.getQueryString();
        }
        return full;
    }

    @Override
    public String getRemoteAddr() {
        return exchange.getSourceAddress().getAddress().getHostAddress();
    }

    @Override
    public void addResponseCookie(final Cookie cookie) {
        final CookieImpl newCookie = new CookieImpl(cookie.getName(), cookie.getValue());
        newCookie.setComment(cookie.getComment());
        newCookie.setDomain(cookie.getDomain());
        newCookie.setPath(cookie.getPath());
        newCookie.setMaxAge(cookie.getMaxAge());
        newCookie.setSecure(cookie.isSecure());
        newCookie.setHttpOnly(cookie.isHttpOnly());
        exchange.setResponseCookie(newCookie);
    }

    @Override
    public void setRequestAttribute(final String name, final Object value) {
        String result = null;
        if (value != null) {
            result = JAVA_SERIALIZATION_HELPER.serializeToBase64((Serializable) value);
        }
        // TODO: not sure if it can be used as request attribute
        exchange.addPathParam(name, result);
    }

    @Override
    public String getPath() {
        return exchange.getRequestPath();
    }

    @Override
    public void setResponseContentType(final String content) {
        exchange.getResponseHeaders().add(HttpString.tryFromString(HttpConstants.CONTENT_TYPE_HEADER), content);
    }

    @Override
    public Collection<Cookie> getRequestCookies() {
        Map<String, io.undertow.server.handlers.Cookie> cookiesMap = exchange.getRequestCookies();
        final List<Cookie> cookies = new ArrayList<>();
        for (final String key : cookiesMap.keySet()) {
            final io.undertow.server.handlers.Cookie uCookie = cookiesMap.get(key);
            final Cookie cookie = new Cookie(uCookie.getName(), uCookie.getValue());
            cookie.setComment(uCookie.getComment());
            cookie.setDomain(uCookie.getDomain());
            cookie.setPath(uCookie.getPath());
            cookie.setMaxAge(uCookie.getMaxAge());
            cookie.setSecure(uCookie.isSecure());
            cookie.setHttpOnly(uCookie.isHttpOnly());
            cookies.add(cookie);
        }
        return cookies;
    }

    @Override
    public Object getSessionIdentifier() {
        return sessionStore.getOrCreateSessionId(this);
    }

    @Override
    public Object getRequestAttribute(final String name) {
        // TODO: not sure if it can be used as request attribute
        Deque<String> value = exchange.getPathParameters().get(name);
        if (value != null) {
            final String serializedValue = value.getFirst();
            if (serializedValue != null) {
                return JAVA_SERIALIZATION_HELPER.unserializeFromBase64(serializedValue);
            }
        }
        return null;
    }

    @Override
    public boolean isSecure() {
        return "HTTPS".equalsIgnoreCase(exchange.getRequestScheme().toString());
    }
}
