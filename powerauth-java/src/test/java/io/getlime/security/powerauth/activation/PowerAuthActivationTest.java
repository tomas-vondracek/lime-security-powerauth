/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.getlime.security.powerauth.activation;

import com.google.common.io.BaseEncoding;
import io.getlime.security.powerauth.client.activation.PowerAuthClientActivation;
import io.getlime.security.powerauth.lib.generator.KeyGenerator;
import io.getlime.security.powerauth.lib.util.KeyConversionUtils;
import io.getlime.security.powerauth.server.activation.PowerAuthServerActivation;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import javax.crypto.SecretKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author petrdvorak
 */
public class PowerAuthActivationTest {

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        // Add Bouncy Castle Security Provider
        Security.addProvider(new BouncyCastleProvider());
    }

    @After
    public void tearDown() {
    }
    
    @Test
    public void testGenerateKeys() throws Exception {
        KeyConversionUtils keyConversion = new KeyConversionUtils();
        KeyGenerator keyGenerator = new KeyGenerator();
        KeyPair kp = keyGenerator.generateKeyPair();
        System.out.println("Private Key: " + BaseEncoding.base64().encode(keyConversion.convertPrivateKeyToBytes(kp.getPrivate())));
        System.out.println("Public Key: " + BaseEncoding.base64().encode(keyConversion.convertPublicKeyToBytes(kp.getPublic())));
    }

    /**
     * Test of the complete activation process, orchestration between client and
     * server.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void testActivationProcess() throws Exception {

        System.out.println("TEST: Activation Process");

        // Prepare test data
        KeyGenerator keyGenerator = new KeyGenerator();
        PowerAuthClientActivation clientActivation = new PowerAuthClientActivation();
        PowerAuthServerActivation serverActivation = new PowerAuthServerActivation();
        KeyPair masterKeyPair = keyGenerator.generateKeyPair();

        // Generate master keypair
        PrivateKey masterPrivateKey = masterKeyPair.getPrivate();
        PublicKey masterPublicKey = masterKeyPair.getPublic();

        for (int i = 0; i < 100; i++) {

            // SERVER: Generate data for activation
            @SuppressWarnings("unused")
			String activationId = serverActivation.generateActivationId();
            String activationIdShort = serverActivation.generateActivationIdShort();
            String activationOTP = serverActivation.generateActivationOTP();
            byte[] activationSignature = serverActivation.generateActivationSignature(activationIdShort, activationOTP, masterPrivateKey);
            KeyPair serverKeyPair = serverActivation.generateServerKeyPair();
            PrivateKey serverPrivateKey = serverKeyPair.getPrivate();
            PublicKey serverPublicKey = serverKeyPair.getPublic();

            // CLIENT: Verify activation signature
            boolean activationSignatureOK = clientActivation.verifyActivationDataSignature(activationIdShort, activationOTP, activationSignature, masterPublicKey);
            assertTrue(activationSignatureOK);

            // CLIENT: Generate and send public key
            KeyPair deviceKeyPair = clientActivation.generateDeviceKeyPair();
            PrivateKey devicePrivateKey = deviceKeyPair.getPrivate();
            PublicKey devicePublicKey = deviceKeyPair.getPublic();
            byte[] activationNonceClient = clientActivation.generateActivationNonce();
            byte[] c_devicePublicKey = clientActivation.encryptDevicePublicKey(devicePublicKey, activationOTP, activationIdShort, activationNonceClient);

            // SERVER: Decrypt device public key
            PublicKey decryptedDevicePublicKey = serverActivation.decryptDevicePublicKey(c_devicePublicKey, activationIdShort, activationOTP, activationNonceClient);
            assertEquals(devicePublicKey, decryptedDevicePublicKey);

            // SERVER: Encrypt and send encrypted server public and it's signature
            KeyPair ephemeralKeyPair = keyGenerator.generateKeyPair();
            PrivateKey ephemeralPrivateKey = ephemeralKeyPair.getPrivate();
            PublicKey ephemeralPublicKey = ephemeralKeyPair.getPublic();
            byte[] activationNonceServer = serverActivation.generateActivationNonce();
            byte[] c_serverPublicKey = serverActivation.encryptServerPublicKey(serverPublicKey, devicePublicKey, ephemeralPrivateKey, activationOTP, activationIdShort, activationNonceServer);
            byte[] c_serverPublicKeySignature = serverActivation.computeServerPublicKeySignature(c_serverPublicKey, masterPrivateKey);

            // CLIENT: Validate server public key signature and decrypt server public key
            boolean serverPublicKeySignatureOK = clientActivation.verifyServerPublicKeySignature(c_serverPublicKey, c_serverPublicKeySignature, masterPublicKey);
            assertTrue(serverPublicKeySignatureOK);

            PublicKey decryptedServerPublicKey = clientActivation.decryptServerPublicKey(c_serverPublicKey, devicePrivateKey, ephemeralPublicKey, activationOTP, activationIdShort, activationNonceServer);
            assertEquals(serverPublicKey, decryptedServerPublicKey);

            // CLIENT and SERVER: Compute device public key fingerprint
            int devicePublicKeyFingerprintClient = clientActivation.computeDevicePublicKeyFingerprint(devicePublicKey);
            int devicePublicKeyFingerprintServer = serverActivation.computeDevicePublicKeyFingerprint(decryptedDevicePublicKey);
            assertEquals(devicePublicKeyFingerprintClient, devicePublicKeyFingerprintServer);

            // CLIENT and SERVER: Compute shared master secret
            SecretKey sharedMasterSecretDevice = keyGenerator.computeSharedKey(devicePrivateKey, serverPublicKey);
            SecretKey sharedMasterSecretServer = keyGenerator.computeSharedKey(serverPrivateKey, devicePublicKey);
            assertEquals(sharedMasterSecretDevice, sharedMasterSecretServer);

        }
    }

}
