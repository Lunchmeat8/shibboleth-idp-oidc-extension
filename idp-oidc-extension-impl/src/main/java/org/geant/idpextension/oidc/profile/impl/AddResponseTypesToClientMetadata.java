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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.geant.idpextension.oidc.config.logic.AuthorizationCodeFlowEnabledPredicate;
import org.geant.idpextension.oidc.config.logic.ImplicitFlowEnabledPredicate;
import org.opensaml.profile.action.ActionSupport;
import org.opensaml.profile.action.EventIds;
import org.opensaml.profile.context.ProfileRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.openid.connect.sdk.OIDCResponseTypeValue;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.logic.Constraint;

/**
 * An action that adds response_types to the OIDC client metadata.
 * 
 * The default set of supported response_types as defined in the OIDC registration specification:
 * code: if authorization_code enabled
 * id_token: if implicit enabled
 * token id_token: if implicit enabled
 * code id_token: if both authorization_code, implicit enabled
 * code token: if both authorization_code, implicit enabled
 * code token id_token: if both authorization_code, implicit enabled
 */
@SuppressWarnings("rawtypes")
public class AddResponseTypesToClientMetadata extends AbstractOIDCClientMetadataPopulationAction {

    /** Class logger. */
    @Nonnull
    private final Logger log = LoggerFactory.getLogger(AddResponseTypesToClientMetadata.class);
    
    /** Predicate used to indicate whether authorization code flow is enabled. */
    @Nonnull private Predicate<ProfileRequestContext> authorizationCodeFlowPredicate;
    
    /** Predicate used to indicate whether implicit flow is enabled. */
    @Nonnull private Predicate<ProfileRequestContext> implicitFlowPredicate;
    
    /** Map of supported response types and their corresponding predicates. */
    @Nonnull private Map<ResponseType, Predicate<ProfileRequestContext>> supportedResponseTypes;
    
    /** Constructor. */
    public AddResponseTypesToClientMetadata() {
        authorizationCodeFlowPredicate = new AuthorizationCodeFlowEnabledPredicate();
        implicitFlowPredicate = new ImplicitFlowEnabledPredicate();

        supportedResponseTypes = new HashMap<>();
        supportedResponseTypes.put(new ResponseType(ResponseType.Value.CODE), authorizationCodeFlowPredicate);
        supportedResponseTypes.put(new ResponseType(OIDCResponseTypeValue.ID_TOKEN), implicitFlowPredicate);
        supportedResponseTypes.put(new ResponseType(ResponseType.Value.TOKEN, OIDCResponseTypeValue.ID_TOKEN), 
                implicitFlowPredicate);
        supportedResponseTypes.put(new ResponseType(ResponseType.Value.CODE, OIDCResponseTypeValue.ID_TOKEN), 
                Predicates.and(implicitFlowPredicate, authorizationCodeFlowPredicate));
        supportedResponseTypes.put(new ResponseType(ResponseType.Value.CODE, ResponseType.Value.TOKEN), 
                Predicates.and(implicitFlowPredicate, authorizationCodeFlowPredicate));
        supportedResponseTypes.put(new ResponseType(ResponseType.Value.CODE, ResponseType.Value.TOKEN, 
                OIDCResponseTypeValue.ID_TOKEN), 
                Predicates.and(implicitFlowPredicate, authorizationCodeFlowPredicate));
    }
    
    /** {@inheritDoc} */
    protected void doInitialize() throws ComponentInitializationException {
        super.doInitialize();
    }
    
    /**
     * Get predicate used to indicate whether authorization code flow is enabled.
     * @return Predicate used to indicate whether authorization code flow is enabled.
     */
    public Predicate<ProfileRequestContext> getAuthorizationCodeFlowEnabled() {
        return authorizationCodeFlowPredicate;
    }
    
    /**
     * Set predicate used to indicate whether authorization code flow is enabled.
     * @param predicate What to set.
     */
    public void setAuthorizationCodeFlowEnabled(final Predicate<ProfileRequestContext> predicate) {
        authorizationCodeFlowPredicate = Constraint.isNotNull(predicate, 
                "Predicate used to indicate whether authorization code flow is supported cannot be null");
    }

    /**
     * Get predicate used to indicate whether hybrid flow is enabled.
     * @return Predicate used to indicate whether hybrid flow is enabled.
     */
    public Predicate<ProfileRequestContext> getImplicitFlowEnabled() {
        return implicitFlowPredicate;
    }
    
    /**
     * Set predicate used to indicate whether hybrid flow is enabled.
     * @param predicate What to set.
     */
    public void setImplicitFlowEnabled(final Predicate<ProfileRequestContext> predicate) {
        implicitFlowPredicate = Constraint.isNotNull(predicate, 
                "Predicate used to indicate whether hybrid flow is supported cannot be null");
    }
    
    /**
     * Set map of supported response types and their corresponding predicates.
     * @param types What to set.
     */
    public void setSupportedResponseTypes(final Map<ResponseType, Predicate<ProfileRequestContext>> types) {
        supportedResponseTypes = Constraint.isNotNull(types, "Supported response types cannot be null!");
    }
    
    /**
     * Get map of supported response types and their corresponding predicates.
     * @return Map of supported response types and their corresponding predicates.
     */
    public Map<ResponseType, Predicate<ProfileRequestContext>> getSupportedResponseTypes() {
        return supportedResponseTypes;
    }
    
    /** {@inheritDoc} */
    @Override
    protected void doExecute(@Nonnull final ProfileRequestContext profileRequestContext) {
        final Set<ResponseType> requestedTypes = getInputMetadata().getResponseTypes();
        final Set<ResponseType> responseTypes = new HashSet<>();
        if (requestedTypes != null && !requestedTypes.isEmpty()) {
            for (final ResponseType requestedType : requestedTypes) {
                if (supportedResponseTypes.keySet().contains(requestedType)) {
                    addResponseTypeIfEnabled(responseTypes, requestedType, supportedResponseTypes.get(requestedType), 
                            profileRequestContext);
                } else {
                    log.warn("{} Dropping unsupported requested response type {}", getLogPrefix(), requestedType);
                }
            }
            if (responseTypes.isEmpty()) {
                log.error("{} No supported response types requested", getLogPrefix());
                ActionSupport.buildEvent(profileRequestContext, EventIds.INVALID_MESSAGE);
                return;
            }
        } else {
            // if no response types requested, setting it to default 'code' if code flow is enabled
            addResponseTypeIfEnabled(responseTypes, new ResponseType(ResponseType.Value.CODE),
                    authorizationCodeFlowPredicate, profileRequestContext);
        }
        getOutputMetadata().setResponseTypes(responseTypes);
    }

    /**
     * Adds a given response type to the given set of response types, if the given predicate is true.
     * @param resultTypes The result set where the response type is potentially added.
     * @param responseType The response type to check.
     * @param predicate The predicate used for checking.
     * @param profileRequestContext The profile context used as an input for the predicate.
     */
    protected void addResponseTypeIfEnabled(final Set<ResponseType> resultTypes, final ResponseType responseType, 
            final Predicate<ProfileRequestContext> predicate, final ProfileRequestContext profileRequestContext) {
        if (predicate.apply(profileRequestContext)) {
            log.debug("{} Adding {} to the list of enabled types", getLogPrefix(), responseType);
            resultTypes.add(responseType);
        } else {
            log.debug("{} Response type {} is not enabled", getLogPrefix(), responseType);
        }
    }
}