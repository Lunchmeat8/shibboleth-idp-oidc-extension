/*
 * GÉANT BSD Software License
 *
 * Copyright (c) 2017 - 2020, GÉANT
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the GÉANT nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * Disclaimer:
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.geant.idpextension.oidc.attribute.encoding.impl;

import javax.annotation.Nonnull;

import org.opensaml.profile.context.ProfileRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;

import net.minidev.json.JSONObject;
import net.shibboleth.idp.attribute.AttributeEncoder;
import net.shibboleth.idp.attribute.AttributeEncodingException;
import net.shibboleth.idp.attribute.IdPAttribute;
import net.shibboleth.utilities.java.support.annotation.constraint.NonnullAfterInit;
import net.shibboleth.utilities.java.support.annotation.constraint.NotEmpty;
import net.shibboleth.utilities.java.support.component.AbstractInitializableComponent;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.primitive.StringSupport;

/**
 * abstract class for OIDC attribute encoders. Needs some work still, activation
 * condition needs to be verified to work.
 */
public abstract class AbstractOIDCAttributeEncoder extends AbstractInitializableComponent implements
        AttributeEncoder<JSONObject> {

    /** Class logger. */
    @Nonnull
    private final Logger log = LoggerFactory.getLogger(AbstractOIDCAttributeEncoder.class);

    /** The name of the attribute. */
    @NonnullAfterInit
    private String name;

    /** Condition for use of this encoder. */
    @SuppressWarnings("rawtypes")
    @Nonnull
    private Predicate<ProfileRequestContext> activationCondition;

    /**
     * Set whether to encode type information. Not Supported. Encoder parser
     * sets this if defined.
     * 
     * @param flag
     *            flag to set
     */
    public void setEncodeType(final boolean flag) {
        log.warn("Encode type parameter is not supported");
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    @Override
    @Nonnull
    public Predicate<ProfileRequestContext> getActivationCondition() {
        return activationCondition;
    }

    /**
     * Set the activation condition for this encoder.
     * 
     * @param condition
     *            condition to set
     */
    @SuppressWarnings("rawtypes")
    public void setActivationCondition(@Nonnull final Predicate<ProfileRequestContext> condition) {
        // TODO: activation condition must be tested and be verified to work
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        activationCondition = Constraint.isNotNull(condition, "Activation condition cannot be null");
    }

    /**
     * Get the name of the attribute.
     * 
     * @return name of the attribute
     */
    @NonnullAfterInit
    public final String getName() {
        return name;
    }

    /**
     * Set the name of the attribute.
     * 
     * @param attributeName
     *            name of the attribute
     */
    public void setName(@Nonnull @NotEmpty final String attributeName) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        name = Constraint.isNotNull(StringSupport.trimOrNull(attributeName), "Attribute name cannot be null or empty");
    }

    /** {@inheritDoc} */
    @Override
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();
        if (name == null) {
            throw new ComponentInitializationException("Attribute name cannot be null or empty");
        }
    }

    @Override
    public abstract JSONObject encode(IdPAttribute idpAttribute) throws AttributeEncodingException;

    @Override
    public String getProtocol() {
        return "http://openid.net/specs/openid-connect-core-1_0.html";
    }

}