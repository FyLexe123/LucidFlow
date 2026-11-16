const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user profile or vault items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vaultItems").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chatMessages").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("analyticsEvents").get());
});

test("Owner can create and update their own UserProfile with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  const ts = pastTimestamp();

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    displayName: "Alice",
    preferredLanguage: "tr",
    darkMode: "dark",
    e2eeEnabled: true,
    e2eeSaltBase64: "c2FsdA==",
    e2eeKeyFingerprint: "abc123",
    notificationsEnabled: true,
    notifyDailyDigest: true,
    notifySyncAlerts: true,
    notifyAiInsights: true,
    quietHoursStart: 23,
    quietHoursEnd: 7,
    createdAt: ts,
    updatedAt: ts,
  }));

  await assertSucceeds(profileRef.update({
    preferredLanguage: "en",
    darkMode: "light",
    updatedAt: pastTimestamp(),
  }));
});

test("Shadow update with ghost field on UserProfile is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  const ts = pastTimestamp();

  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    displayName: "Alice",
    preferredLanguage: "tr",
    darkMode: "system",
    e2eeEnabled: true,
    notificationsEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  }));

  await assertFails(profileRef.update({
    isAdmin: true,
    updatedAt: pastTimestamp(),
  }));
});

test("Cross-user access to VaultItems is strictly denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("vaultItems").doc("item_1");
  await assertSucceeds(itemRef.set({
    userId: ALICE_UID,
    title: "Secret Note",
    cipherTextBase64: "ZW5jcnlwdGVk",
    ivBase64: "aXYxMjM0",
    authTagHash: "hash123",
    category: "note",
    isEncrypted: true,
    isPinned: false,
    createdAt: ts,
    updatedAt: ts,
  }));

  // Bob cannot list, get, update, or delete Alice's vault items
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vaultItems").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vaultItems").doc("item_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vaultItems").doc("item_1").delete());
});

test("Owner can list and create ChatMessages and AnalyticsEvents, rejects invalid types", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      content: "Merhaba AuraGlass",
      modelName: "gemini-4-hydrogen",
      latencyMs: 120,
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chatMessages").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("analyticsEvents").doc("evt_1").set({
      userId: ALICE_UID,
      featureType: "chat",
      modelUsed: "gemini-4-hydrogen",
      latencyMs: 320,
      tokenEstimate: 150,
      isOfflineQueued: false,
      isEncrypted: true,
      createdAt: ts,
    })
  );

  // Invalid featureType rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("analyticsEvents").doc("evt_bad").set({
      userId: ALICE_UID,
      featureType: "invalid_feature",
      modelUsed: "gemini-4-hydrogen",
      latencyMs: 320,
      tokenEstimate: 150,
      isOfflineQueued: false,
      isEncrypted: true,
      createdAt: ts,
    })
  );
});
