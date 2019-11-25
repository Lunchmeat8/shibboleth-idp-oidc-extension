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

package org.geant.idpextension.oidc.algorithm.descriptors;

import junit.framework.Assert;

import org.geant.idpextension.oidc.crypto.support.SignatureConstants;
import org.opensaml.security.crypto.JCAConstants;
import org.opensaml.xmlsec.algorithm.AlgorithmDescriptor.AlgorithmType;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link SignatureRS512}
 */
public class SignatureRS512Test {

	private SignatureRS512 algorithm = new SignatureRS512();

	@Test
	public void testInitialState() {
		Assert.assertEquals(JCAConstants.KEY_ALGO_RSA, algorithm.getKey());
		Assert.assertEquals(SignatureConstants.ALGO_ID_SIGNATURE_RS_512, algorithm.getURI());
		Assert.assertEquals(AlgorithmType.Signature, algorithm.getType());
		Assert.assertEquals(JCAConstants.SIGNATURE_RSA_SHA512, algorithm.getJCAAlgorithmID());
		Assert.assertEquals(JCAConstants.DIGEST_SHA512, algorithm.getDigest());
	}

}