package org.alexmond.unitrack.web.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts/decrypts channel secrets at rest with AES-256-GCM (a fresh IV per value, so
 * equal plaintexts produce different ciphertexts). The key comes from
 * {@link AlertProperties}; using the built-in dev default logs a warning so it isn't
 * shipped to production unnoticed.
 */
@Component
public class SecretCipher {

	private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

	private static final String DEV_KEY = "unitrack-dev-insecure-key";

	private final TextEncryptor encryptor;

	public SecretCipher(AlertProperties props) {
		if (DEV_KEY.equals(props.getEncryptionKey())) {
			log.warn("unitrack.alerts.encryption-key is the insecure dev default — "
					+ "set UNITRACK_ALERTS_ENCRYPTION_KEY before storing real channel secrets.");
		}
		this.encryptor = Encryptors.delux(props.getEncryptionKey(), props.getEncryptionSalt());
	}

	/** Encrypts a secret, or returns null/blank unchanged. */
	public String encrypt(String plaintext) {
		return (plaintext == null || plaintext.isBlank()) ? plaintext : this.encryptor.encrypt(plaintext);
	}

	/** Decrypts a stored secret, or returns null/blank unchanged. */
	public String decrypt(String ciphertext) {
		return (ciphertext == null || ciphertext.isBlank()) ? ciphertext : this.encryptor.decrypt(ciphertext);
	}

}
