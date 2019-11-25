/*
 * Copyright (c) 2017 - 2020, GÉANT
 *
 * Licensed under the Apache License, Version 2.0 (the “License”); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.geant.idpextension.oidc.profile.impl;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.action.EventIds;
import org.opensaml.profile.context.ProfileRequestContext;
import org.opensaml.saml.saml2.profile.context.EncryptionContext;
import org.opensaml.xmlsec.EncryptionConfiguration;
import org.opensaml.xmlsec.EncryptionParameters;
import org.opensaml.xmlsec.EncryptionParametersResolver;
import org.opensaml.xmlsec.SecurityConfigurationSupport;
import org.opensaml.xmlsec.criterion.EncryptionConfigurationCriterion;
import org.opensaml.xmlsec.criterion.EncryptionOptionalCriterion;

import net.shibboleth.idp.profile.AbstractProfileAction;
import net.shibboleth.idp.profile.context.RelyingPartyContext;
import net.shibboleth.utilities.java.support.annotation.constraint.NonnullAfterInit;
import net.shibboleth.utilities.java.support.annotation.constraint.NonnullElements;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.component.ComponentSupport;
import net.shibboleth.utilities.java.support.logic.Constraint;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

import org.geant.idpextension.oidc.criterion.ClientInformationCriterion;
import org.geant.idpextension.oidc.messaging.context.OIDCMetadataContext;
import org.geant.idpextension.oidc.profile.context.navigate.DefaultOIDCMetadataContextLookupFunction;
import org.opensaml.messaging.context.navigate.ChildContextLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;

/**
 * Action that resolves and populates {@link EncryptionParameters} on an {@link EncryptionContext} created/accessed via
 * a lookup function, by default on a {@link RelyingPartyContext} child of the profile request context.
 * 
 * The parameters are used either to encrypt id token / userinfo response or to decrypt request object. For the first
 * case the parameters are set with {@link EncryptionContext#setAssertionEncryptionParameters}, for request object
 * decryption the parameters are set with {@link EncryptionContext#setAttributeEncryptionParameters}. Yes, we are
 * stealing and bit misusing existing Shib context for our own almost similar purposes.
 * 
 * 
 * <p>
 * The OpenSAML default, per-RelyingParty, and default per-profile {@link EncryptionConfiguration} objects are input to
 * the resolution process, along with the relying party's oidc client registration data, which in most cases will be the
 * source of the eventual encryption key.
 * </p>
 * 
 */
@SuppressWarnings("rawtypes")
public class PopulateOIDCEncryptionParameters extends AbstractProfileAction {

    /** Class logger. */
    @Nonnull
    private final Logger log = LoggerFactory.getLogger(PopulateOIDCEncryptionParameters.class);

    /** Whether we resolve encryption or decryption parameters. */
    private boolean forDecryption;

    /** Strategy used to look up the {@link EncryptionContext} to store parameters in. */
    @Nonnull
    private Function<ProfileRequestContext, EncryptionContext> encryptionContextLookupStrategy;

    /** Strategy used to look up a per-request {@link EncryptionConfiguration} list. */
    @NonnullAfterInit
    private Function<ProfileRequestContext, List<EncryptionConfiguration>> configurationLookupStrategy;

    /** Resolver for parameters to store into context. */
    @NonnullAfterInit
    private EncryptionParametersResolver encParamsresolver;

    /** Active configurations to feed into resolver. */
    @Nullable
    @NonnullElements
    private List<EncryptionConfiguration> encryptionConfigurations;

    /** Strategy used to look up a OIDC metadata context. */
    @Nullable
    private Function<ProfileRequestContext, OIDCMetadataContext> oidcMetadataContextLookupStrategy;

    /** Constructor. */
    public PopulateOIDCEncryptionParameters() {
        // Create context by default.
        oidcMetadataContextLookupStrategy = new DefaultOIDCMetadataContextLookupFunction();
        encryptionContextLookupStrategy = Functions.compose(new ChildContextLookup<>(EncryptionContext.class, true),
                new ChildContextLookup<ProfileRequestContext, RelyingPartyContext>(RelyingPartyContext.class));
    }

    /**
     * Whether we resolve encryption or decryption parameters.
     * 
     * @param forDecryption true if we should resolve decryption parameters.
     */
    public void setForDecryption(boolean value) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);
        forDecryption = value;
    }

    /**
     * Set the strategy used to look up the {@link EncryptionContext} to set the flags for.
     * 
     * @param strategy lookup strategy
     */
    public void setEncryptionContextLookupStrategy(
            @Nonnull final Function<ProfileRequestContext, EncryptionContext> strategy) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        encryptionContextLookupStrategy =
                Constraint.isNotNull(strategy, "EncryptionContext lookup strategy cannot be null");
    }

    /**
     * Set the strategy used to look up the {@link OIDCMetadataContext} to locate client registered encryption
     * parameters.
     * 
     * @param strategy lookup strategy
     */
    public void setOIDCMetadataContextContextLookupStrategy(
            @Nonnull final Function<ProfileRequestContext, OIDCMetadataContext> strategy) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        oidcMetadataContextLookupStrategy =
                Constraint.isNotNull(strategy, " OIDCMetadataContext lookup strategy cannot be null");
    }

    /**
     * Set the strategy used to look up a per-request {@link EncryptionConfiguration} list.
     * 
     * @param strategy lookup strategy
     */
    public void setConfigurationLookupStrategy(
            @Nonnull final Function<ProfileRequestContext, List<EncryptionConfiguration>> strategy) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        configurationLookupStrategy =
                Constraint.isNotNull(strategy, "EncryptionConfiguration lookup strategy cannot be null");
    }

    /**
     * Set the encParamsresolver to use for the parameters to store into the context.
     * 
     * @param newResolver encParamsresolver to use
     */
    public void setEncryptionParametersResolver(@Nonnull final EncryptionParametersResolver newResolver) {
        ComponentSupport.ifInitializedThrowUnmodifiabledComponentException(this);

        encParamsresolver = Constraint.isNotNull(newResolver, "EncryptionParametersResolver cannot be null");
    }

    /** {@inheritDoc} */
    @Override
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();

        if (encParamsresolver == null) {
            throw new ComponentInitializationException("EncryptionParametersResolver cannot be null");
        } else if (configurationLookupStrategy == null) {
            configurationLookupStrategy = new Function<ProfileRequestContext, List<EncryptionConfiguration>>() {
                public List<EncryptionConfiguration> apply(final ProfileRequestContext input) {
                    return Collections.singletonList(SecurityConfigurationSupport.getGlobalEncryptionConfiguration());
                }
            };
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doExecute(@Nonnull final ProfileRequestContext profileRequestContext) {

        log.debug("{} Resolving EncryptionParameters for request, purpose {}", getLogPrefix(),
                forDecryption ? "request object decryption" : "response encryption");
        final EncryptionContext encryptCtx = encryptionContextLookupStrategy.apply(profileRequestContext);
        if (encryptCtx == null) {
            log.debug("{} No EncryptionContext returned by lookup strategy", getLogPrefix());
            ActionSupport.buildEvent(profileRequestContext, EventIds.INVALID_PROFILE_CTX);
            return;
        }
        try {
            encryptionConfigurations = configurationLookupStrategy.apply(profileRequestContext);
            if (encryptionConfigurations == null || encryptionConfigurations.isEmpty()) {
                throw new ResolverException("No EncryptionConfigurations returned by lookup strategy");
            }
            CriteriaSet criteria = buildCriteriaSet(profileRequestContext);
            final EncryptionParameters params = encParamsresolver.resolveSingle(criteria);
            log.debug("{} {} EncryptionParameters for {}", getLogPrefix(),
                    params != null ? "Resolved" : "Failed to resolve",
                    forDecryption ? "request object decryption" : "response encryption");
            if (params != null) {
                if (forDecryption) {
                    // Decryption parameters for request object decryption
                    encryptCtx.setAttributeEncryptionParameters(params);
                } else {
                    // Indicates that id token or userinfo response should be encrypted
                    encryptCtx.setAssertionEncryptionParameters(params);
                }
                return;
            }
            final EncryptionOptionalCriterion encryptionOptionalCrit = criteria.get(EncryptionOptionalCriterion.class);
            if (encryptionOptionalCrit != null) {
                if (encryptionOptionalCrit.isEncryptionOptional()) {
                    log.debug("{} Encryption optional", getLogPrefix());
                    return;
                }
            }
        } catch (final ResolverException e) {
            log.error("{} Error resolving EncryptionParameters", getLogPrefix(), e);
        }
        ActionSupport.buildEvent(profileRequestContext, EventIds.INVALID_SEC_CFG);
    }

    /**
     * Build the criteria used as input to the {@link EncryptionParametersResolver}.
     * 
     * @param profileRequestContext current profile request context
     * 
     * @return the criteria set to use
     */
    @Nonnull
    private CriteriaSet buildCriteriaSet(@Nonnull final ProfileRequestContext profileRequestContext) {

        final CriteriaSet criteria = new CriteriaSet(new EncryptionConfigurationCriterion(encryptionConfigurations));
        final OIDCMetadataContext oidcMetadataCtx = oidcMetadataContextLookupStrategy.apply(profileRequestContext);
        if (oidcMetadataCtx != null && oidcMetadataCtx.getClientInformation() != null) {
            log.debug(
                    "{} Adding oidc client information to resolution criteria for key transport / encryption algorithms",
                    getLogPrefix());
            criteria.add(new ClientInformationCriterion(oidcMetadataCtx.getClientInformation()));
        } else {
            log.debug("{} oidcMetadataCtx is null", getLogPrefix());
        }
        return criteria;
    }

}