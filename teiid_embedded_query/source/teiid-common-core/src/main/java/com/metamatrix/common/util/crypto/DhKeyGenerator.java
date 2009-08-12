/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.common.util.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Properties;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;

import com.metamatrix.common.util.crypto.cipher.SymmetricCryptor;
import com.metamatrix.core.MetaMatrixRuntimeException;

/**
 * Helper class that supports anonymous ephemeral Diffie-Hellmen
 * 
 * Parameters are stored in the dh.properties file
 */
public class DhKeyGenerator {

	private static String ALGORITHM = "DH"; //$NON-NLS-1$
	private static String DIGEST = "SHA-256"; //$NON-NLS-1$
	private static DHParameterSpec DH_SPEC;

	static {
		Properties props = new Properties();
		try {
			props.load(DhKeyGenerator.class
					.getResourceAsStream("dh.properties")); //$NON-NLS-1$
		} catch (IOException e) {
			throw new MetaMatrixRuntimeException(e);
		}
		BigInteger p = new BigInteger(props.getProperty("p")); //$NON-NLS-1$
		BigInteger g = new BigInteger(props.getProperty("g")); //$NON-NLS-1$
		DH_SPEC = new DHParameterSpec(p, g, Integer.parseInt(props
				.getProperty("l"))); //$NON-NLS-1$
	}

	private PrivateKey privateKey;

	/*
	 * TODO: add support for configurable key sizes
	 */
	private int keySize = SymmetricCryptor.DEFAULT_KEY_BITS;

	public byte[] createPublicKey() throws CryptoException {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
			keyGen.initialize(DH_SPEC);
			KeyPair keypair = keyGen.generateKeyPair();

			privateKey = keypair.getPrivate();
			PublicKey publicKey = keypair.getPublic();

			return publicKey.getEncoded();
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new CryptoException(e);
		}
	}

	public SymmetricCryptor getSymmetricCryptor(byte[] peerPublicKeyBytes)
			throws CryptoException {
		if (privateKey == null) {
			throw new IllegalStateException(
					"KeyGenerator did not successfully generate public key"); //$NON-NLS-1$
		}
		try {
			X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(
					peerPublicKeyBytes);
			KeyFactory keyFact = KeyFactory.getInstance(ALGORITHM);
			PublicKey publicKey = keyFact.generatePublic(x509KeySpec);

			KeyAgreement ka = KeyAgreement.getInstance(ALGORITHM);
			ka.init(privateKey);
			ka.doPhase(publicKey, true);
			byte[] secret = ka.generateSecret();

			MessageDigest sha = MessageDigest.getInstance(DIGEST);
			byte[] hash = sha.digest(secret);
			byte[] symKey = new byte[keySize / 8];
			System.arraycopy(hash, 0, symKey, 0, symKey.length);
			return SymmetricCryptor.getSymmectricCryptor(symKey);
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(e);
		} catch (InvalidKeySpecException e) {
			throw new CryptoException(e);
		} catch (InvalidKeyException e) {
			throw new CryptoException(e);
		}
	}

	/**
	 * Can be used to generate new parameters
	 */
	public static void main(String[] args) throws Exception {
		AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator
				.getInstance(ALGORITHM);
		paramGen.init(1024);

		AlgorithmParameters params = paramGen.generateParameters();

		DHParameterSpec dhSpec = params.getParameterSpec(DHParameterSpec.class);
		System.out.println("l=" + dhSpec.getL());
		System.out.println("g=" + dhSpec.getG());
		System.out.println("p=" +dhSpec.getP());
	}

}
