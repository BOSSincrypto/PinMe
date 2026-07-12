// Encryption utilities using Web Crypto API with Capacitor support
const ALGORITHM = "AES-GCM";
const KEY_LENGTH = 256;
const PASSWORD_HASH_ITERATIONS = 310000;

// Base64 encoding that works in both browser and Capacitor
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

async function getKey(password: string, salt: Uint8Array): Promise<CryptoKey> {
  const encoder = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    encoder.encode(password),
    "PBKDF2",
    false,
    ["deriveKey"]
  );

  return crypto.subtle.deriveKey(
    {
      name: "PBKDF2",
      salt: salt as BufferSource,
      iterations: 100000,
      hash: "SHA-256",
    },
    keyMaterial,
    { name: ALGORITHM, length: KEY_LENGTH },
    false,
    ["encrypt", "decrypt"]
  );
}

export async function encryptData(data: string, password: string): Promise<string> {
  const encoder = new TextEncoder();
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await getKey(password, salt);

  const encrypted = await crypto.subtle.encrypt(
    { name: ALGORITHM, iv },
    key,
    encoder.encode(data)
  );

  // Combine salt + iv + encrypted data
  const combined = new Uint8Array(salt.length + iv.length + encrypted.byteLength);
  combined.set(salt, 0);
  combined.set(iv, salt.length);
  combined.set(new Uint8Array(encrypted), salt.length + iv.length);

  // Convert to base64 using Capacitor-compatible method
  return arrayBufferToBase64(combined.buffer);
}

export async function decryptData(encryptedData: string, password: string): Promise<string> {
  try {
    // Decode from base64 using Capacitor-compatible method
    const combinedBuffer = base64ToArrayBuffer(encryptedData);
    const combined = new Uint8Array(combinedBuffer);

    // Extract salt, iv, and encrypted data
    const salt = combined.slice(0, 16);
    const iv = combined.slice(16, 28);
    const data = combined.slice(28);

    const key = await getKey(password, salt);

    const decrypted = await crypto.subtle.decrypt(
      { name: ALGORITHM, iv },
      key,
      data
    );

    const decoder = new TextDecoder();
    return decoder.decode(decrypted);
  } catch {
    throw new Error("Неверный пароль или поврежденные данные");
  }
}

export async function createPasswordDigest(password: string): Promise<{
  hash: string;
  salt: string;
  iterations: number;
}> {
  const salt = crypto.getRandomValues(new Uint8Array(32));
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const hash = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations: PASSWORD_HASH_ITERATIONS,
      hash: "SHA-256",
    },
    keyMaterial,
    256,
  );
  return {
    hash: arrayBufferToBase64(hash),
    salt: arrayBufferToBase64(salt.buffer),
    iterations: PASSWORD_HASH_ITERATIONS,
  };
}

export async function verifyPasswordDigest(
  password: string,
  saltBase64: string,
  expectedHashBase64: string,
  iterations: number,
): Promise<boolean> {
  try {
    const keyMaterial = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(password),
      "PBKDF2",
      false,
      ["deriveBits"],
    );
    const actualHash = new Uint8Array(
      await crypto.subtle.deriveBits(
        {
          name: "PBKDF2",
          salt: new Uint8Array(base64ToArrayBuffer(saltBase64)),
          iterations,
          hash: "SHA-256",
        },
        keyMaterial,
        256,
      ),
    );
    const expectedHash = new Uint8Array(base64ToArrayBuffer(expectedHashBase64));
    if (actualHash.length !== expectedHash.length) {
      return false;
    }
    let difference = 0;
    for (let index = 0; index < actualHash.length; index += 1) {
      difference |= actualHash[index] ^ expectedHash[index];
    }
    return difference === 0;
  } catch {
    return false;
  }
}
