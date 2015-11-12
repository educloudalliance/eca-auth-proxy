/*
 * The MIT License
 * Copyright (c) 2015 CSC - IT Center for Science, http://www.csc.fi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package fi.okm.mpass.idp.authn.impl;

import java.io.IOException;

import javax.security.auth.Subject;
import javax.servlet.annotation.WebServlet;
import javax.annotation.Nonnull;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.shibboleth.idp.authn.ExternalAuthentication;
import net.shibboleth.idp.authn.ExternalAuthenticationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.okm.mpass.idp.authn.SocialUserAuthenticationException;
import fi.okm.mpass.idp.authn.SocialRedirectAuthenticator;
import fi.okm.mpass.idp.authn.SocialUserErrorIds;

/**
 * Extracts Social identity and places it in a request attribute to be used by
 * the IdP's external authentication interface.
 */
@WebServlet(name = "SocialUserAuthServlet", urlPatterns = { "/Authn/SocialUser/*" })
public class SocialUserAuthServlet extends HttpServlet {

    /** Serial UID. */
    private static final long serialVersionUID = -3162157736238514852L;

    /** Class logger. */
    @Nonnull
    private final Logger log = LoggerFactory
            .getLogger(SocialUserAuthServlet.class);

    /** Constructor. */
    public SocialUserAuthServlet() {
    }

    /** {@inheritDoc} */
    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }

    /** {@inheritDoc} */
    @Override
    protected void service(final HttpServletRequest httpRequest,
            final HttpServletResponse httpResponse) throws ServletException,
            IOException {
        log.trace("Entering");

        try {
            SocialIdentityFactory sif = (SocialIdentityFactory) getServletContext()
                    .getAttribute(
                            "socialUserImplementationFactoryBeanInServletContext");
            SocialRedirectAuthenticator socialRedirectAuthenticator = sif
                    .getAuthenticator(httpRequest);
            if (socialRedirectAuthenticator == null) {
                // Authentication not possible, use some other flow;
                httpRequest.setAttribute(
                        ExternalAuthentication.AUTHENTICATION_ERROR_KEY,
                        SocialUserErrorIds.EXCEPTION);
                ExternalAuthentication.finishExternalAuthentication(
                        getAuthenticationKey(httpRequest), httpRequest,
                        httpResponse);
                log.trace("Leaving");
                return;
            }
            Subject subject;
            try {
                subject = socialRedirectAuthenticator.getSubject(httpRequest);
            } catch (SocialUserAuthenticationException e) {
                // Authentication has been interrupted;
                httpRequest.setAttribute(
                        ExternalAuthentication.AUTHENTICATION_ERROR_KEY,
                        e.getAuthEventId());
                ExternalAuthentication.finishExternalAuthentication(
                        getAuthenticationKey(httpRequest), httpRequest,
                        httpResponse);
                log.trace("Leaving");
                return;
            }
            if (subject == null) {
                // Start authentication sequence, there is no user nor Subject
                startAuthentication(httpRequest);
                log.debug("Making a redirect to authenticate the user");
                httpResponse.sendRedirect(socialRedirectAuthenticator
                        .getRedirectUrl(httpRequest));
                log.trace("Leaving");
                return;
            }
            httpRequest.setAttribute(ExternalAuthentication.SUBJECT_KEY,
                    subject);
            ExternalAuthentication.finishExternalAuthentication(
                    getAuthenticationKey(httpRequest), httpRequest,
                    httpResponse);

        } catch (final ExternalAuthenticationException e) {
            log.trace("Leaving");
            throw new ServletException(
                    "Error processing external authentication request", e);
        }
        log.trace("Leaving");
    }

    /* Returns authentication key. Starts the sequence */
    private void startAuthentication(final HttpServletRequest httpRequest)
            throws ExternalAuthenticationException {
        log.trace("Entering");
        String key = ExternalAuthentication
                .startExternalAuthentication(httpRequest);
        httpRequest.getSession().setAttribute("ext_auth_start_key", key);
        log.trace("Leaving");
    }

    /* Returns authentication key. Starts the sequence if not already started */
    private String getAuthenticationKey(final HttpServletRequest httpRequest)
            throws ExternalAuthenticationException {
        log.trace("Entering");
        String key = (String) httpRequest.getSession().getAttribute(
                "ext_auth_start_key");
        if (key == null || key.isEmpty()) {
            key = ExternalAuthentication
                    .startExternalAuthentication(httpRequest);
            httpRequest.getSession().setAttribute("ext_auth_start_key", key);
        }
        log.trace("Leaving");
        return key;
    }

}
