/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.consensys.cava.crypto.sodium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.consensys.cava.bytes.Bytes;

import org.junit.jupiter.api.Test;

class SignerTest {

  @Test
  void testEqualityAndRecovery() {
    Signer.KeyPair kp = Signer.KeyPair.random();
    Signer.KeyPair otherKp = Signer.KeyPair.forSecretKey(kp.secretKey());
    assertEquals(kp, otherKp);
  }


  @Test
  void checkDetachedSignVerify() {
    Signer.KeyPair kp = Signer.KeyPair.random();
    Bytes signature = Signer.signDetached(Bytes.fromHexString("deadbeef"), kp.secretKey());
    boolean result = Signer.verifyDetached(Bytes.fromHexString("deadbeef"), signature, kp.publicKey());
    assertTrue(result);
  }


}