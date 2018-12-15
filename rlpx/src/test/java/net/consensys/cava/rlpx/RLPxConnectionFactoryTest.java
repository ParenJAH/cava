/*
 * Copyright 2018 ConsenSys AG.
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
package net.consensys.cava.rlpx;

import static org.junit.jupiter.api.Assertions.*;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.concurrent.AsyncResult;
import net.consensys.cava.crypto.SECP256K1;
import net.consensys.cava.crypto.SECP256K1.KeyPair;
import net.consensys.cava.crypto.SECP256K1.SecretKey;
import net.consensys.cava.junit.BouncyCastleExtension;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BouncyCastleExtension.class)
class RLPxConnectionFactoryTest {

  @Test
  void roundtripPayload() {
    KeyPair exampleKeyPair = SECP256K1.KeyPair.fromSecretKey(
        SecretKey
            .fromBytes(Bytes32.fromHexString("0xEE647A774DF811AB577BA5F397D56BE6567DA58AF7A65368F01DD7A8313812D8")));

    Bytes payload = Bytes.fromHexString(
        "0xF8A7B84135A22239600070940908090D5F051B2C597981B090E386360B87163A8AF1EDF0434A84AA31A582DF93A0396D4CC2E3574C919B0E8D47DCEA095647446C88B36D01B840B8497006E23B1F35BD6E988EC53EE759BC852162049972F777B92B5E029B840BE8BE93F513DA55B81AEE463254930EE30667825B0B6FE30938FFFA7024A03C5AA02B310D67A36F599EAB6B8D03FECB9D782CC7A0EB12FECBFF454A4094557A2EB704");
    InitiatorHandshakeMessage initial = InitiatorHandshakeMessage.decode(payload, exampleKeyPair.secretKey());
    Bytes encoded = initial.encode();
    assertEquals(payload, encoded);
  }

  @Test
  void roundtripInitiatorHandshakeBytes() {
    KeyPair keyPair = KeyPair.random();
    KeyPair peerKeyPair = KeyPair.random();
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);

    Bytes payload = RLPxConnectionFactory.init(keyPair, peerKeyPair.publicKey(), KeyPair.random(), Bytes32.wrap(nonce));
    InitiatorHandshakeMessage init = RLPxConnectionFactory.read(payload, peerKeyPair.secretKey());
    assertEquals(keyPair.publicKey(), init.publicKey());
    assertEquals(Bytes.wrap(nonce), init.nonce());
  }

  @Test
  void roundtripResponseHandshakeBytes() {
    KeyPair keyPair = KeyPair.random();
    KeyPair peerKeyPair = KeyPair.random();
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);

    Bytes payload = RLPxConnectionFactory.init(keyPair, peerKeyPair.publicKey(), KeyPair.random(), Bytes32.wrap(nonce));

    AtomicReference<Bytes> ref = new AtomicReference<>();
    RLPxConnectionFactory.respondToHandshake(payload, peerKeyPair.secretKey(), ref::set);
    HandshakeMessage responder = RLPxConnectionFactory.readResponse(ref.get(), keyPair.secretKey());
    assertNotNull(responder);
  }

  @Test
  void createHandshake() {
    KeyPair keyPair = KeyPair.random();
    KeyPair peerKeyPair = KeyPair.random();
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);

    KeyPair ephemeralKeyPair = KeyPair.random();

    Bytes payload = RLPxConnectionFactory.init(keyPair, peerKeyPair.publicKey(), ephemeralKeyPair, Bytes32.wrap(nonce));

    AtomicReference<Bytes> ref = new AtomicReference<>();
    RLPxConnection conn = RLPxConnectionFactory.respondToHandshake(payload, peerKeyPair.secretKey(), ref::set);
    HandshakeMessage responder = RLPxConnectionFactory.readResponse(ref.get(), keyPair.secretKey());

    assertNotNull(conn);
    assertNotNull(responder);
  }

  @Test
  void createHandshakeAsync() throws TimeoutException, InterruptedException {
    KeyPair keyPair = KeyPair.random();
    KeyPair peerKeyPair = KeyPair.random();

    AtomicReference<RLPxConnection> peerConnectionReference = new AtomicReference<>();
    Function<Bytes, AsyncResult<Bytes>> wireBytes = (bytes) -> {
      AtomicReference<Bytes> responseReference = new AtomicReference<>();
      peerConnectionReference
          .set(RLPxConnectionFactory.respondToHandshake(bytes, peerKeyPair.secretKey(), responseReference::set));
      return AsyncResult.completed(responseReference.get());
    };
    AsyncResult<RLPxConnection> futureConn =
        RLPxConnectionFactory.createHandshake(keyPair, peerKeyPair.publicKey(), wireBytes);

    RLPxConnection conn = futureConn.get(1, TimeUnit.SECONDS);
    assertNotNull(conn);
    assertTrue(RLPxConnection.isComplementedBy(conn, peerConnectionReference.get()));
  }

  @Test
  void fixedMessage() throws Exception {
    RLPxConnection conn = new RLPxConnection(
        Bytes32.fromHexString("0x401EED08125776F3A23201D09847EEBEC539FD18E9CB793A53B21F7A23CEFED4"),
        Bytes32.fromHexString("0x82808970451A460E89DBA968ADAA99B56BC4C6270C4285DA1CB0D40116BB02B7"),
        Bytes32.fromHexString("0xEDC55BCCD06BAA6A2D593D3836D7580407FB0C01A96544C63EAA05D863E05744"),
        Bytes.fromHexString(
            "0x2B72DAFCF28E915725B511BC0A73C760C785B5704EB303E961D1954D21BCC9B801F90474C2A4769AC420A0C26387A2C963264B2596CE626C679588EA733600EC4091BC7B06157E24E0CC741DDBA1E5C6645D83E1149B2CB95AF3915DC52B9485E0122EE6B4AD0B1A80D53D2CBD50BF67E22C4FCB80059CBC3EA672681350765F360F58FB934F0165B50AE928A4D8CB37F68A9B5FC845960E1D37E0869AA593E6C63ABFEB384E6512E511075F7C5D8EB16067A0C59F4882CC4F7EB415F231CDC6A0D78FC38629E38FA0A5741535378680D7E5426ED397304B83AEAEBC43F812C8172E48497DA5E52CE087267A1FEAD8221BA34398B68C3A54E9F0D18B4CECA4472C177E5BE45D631C9D5DC525E0B8D31BD926AB15465922DFD01EE9AE50D67BF78B0CCCB415D034FD89A8A1F3C4E58F1F0DC2FA87AA4E4A956CDB459102571DBB67637CE05927806D09E218EC66ADB2B6A1702C6CBC40CF33DD5FCA373E9C63570C4F4CEC523881199579447B4B557674D9428BEB035FD9807D36AF304CB05C680F8BD9752E9F347C5B9EB02DCB9E09177B5ECE2CA65E7693932B932A98798DE4B428A7D5420173BC2F5BB5AEC985D565A4BD1B7F987906D7F2D4BF51726D279850C46CF65FAF1D1EF81565630618705FF673FB8BC714991796382A07294E1100D51E5321123DB87B248CED97EEB65C3274685CF9C791114756C9B8F0B1824C4A3CFA4EB172A238025EC20973992945ACC886D593DD91555C"),
        Bytes.fromHexString(
            "0xE23AC9D32A1BAD577821EB0858615BFED01C5030A5A39342DC992802679C3C1F0189049E5A5FD009FF4BE044146296CFA5AF029BF1CE5F0913D4DB477D89360E484363C8DFC8E96C6398B20440639323591C8F6337DF2A1DFE7B56DB9B2447401771FEF89700CCB6DDDCF7BA0AC80AE57374449E34F82F65FD0280306E9F62C808690390946CDFF9E9C8A5243ED7BA88E29AA7AC128DD9AE4E79497237B75B4F6D5511FDDD2E1916057ED7CC95B512299C20E90EC2134E2DDA87B7F73F3BFBFD4D68417976AA0F7C74E39BFECF677982E9EE3ED15BDEEC31D7867FA80A18A331AD0BC1C1687F9DB5AD3AB9C81B948E1EE11C1F5CFFE86B5A931CEFD2266D112458B80AA1AD6E9C7814B76CEA4B4C57B360132BFF81ACA35B9620D065F24E0000B4EB27D7B3B126DE353C6C265B391084E3CF27086FCBDF11DA364A480DD61F0899E41539F0D8DD958C95D3D09F0EB4C11136686B5EBB9D89ADFB81FAC83C5895874860F0F75CBDA479EF461CA4B75C2EE0F30FF7E0BD259E31DFE4579E334688303AB29EEAC11DBB963A34F2C09D18242F00BCF5CB5340FB7E03DE02ADEE1F3042246B0E21EF4BAF"));
    RLPxMessage message = conn.read(
        Bytes.fromHexString(
            "0x5B5B56F71CE97B8DEA5F469808441FD10675F244B062222245F5831747924934841D86A431AD89EB3C825C867F0A9820E8E4134C8438A529BE15445073C2C2C33729E6ED374BF2EEC1CA5F3D60972892"));
  }
}
