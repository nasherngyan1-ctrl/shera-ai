const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-shraai-a10eb64c-2e11-43f1-98d6-d4d6566641dc";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read watchlist", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watchlist").get()
  );
});

test("Authenticated user can create and query own watchlist item", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("BTC-USDT").set({
      instId: "BTC-USDT",
      userId: ALICE_UID,
      note: "Core BTC pair",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Cross-user access to watchlist is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("ETH-USDT").set({
      instId: "ETH-USDT",
      userId: ALICE_UID,
      note: "Core ETH",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("ETH-USDT").get()
  );
});

test("Ghost field injection in watchlist is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("BTC-USDT").set({
      instId: "BTC-USDT",
      userId: ALICE_UID,
      note: "Core BTC",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Valid alertRule creation and toggle update succeed, invalid ruleType fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("alertRules").doc("rule_1");
  await assertSucceeds(
    docRef.set({
      instId: "BTC-USDT",
      userId: ALICE_UID,
      ruleType: "PRICE_ABOVE",
      thresholdValue: 95000.0,
      timeframe: "1H",
      enabled: true,
      cooldownMinutes: 30,
      lastTriggeredEpochMs: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    docRef.update({
      enabled: false,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    docRef.update({
      ruleType: "GUARANTEED_WIN",
      updatedAt: new Date(),
    })
  );
});

test("Valid signalHistory record creation succeeds, invalid confluenceScore fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = new Date();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("signalHistory").doc("sig_1");
  await assertSucceeds(
    docRef.set({
      instId: "SOL-USDT",
      userId: ALICE_UID,
      sourceType: "CHART_IMAGE_AI",
      timeframe: "4H",
      signalCategory: "BUY_SETUP",
      confluenceScore: 78,
      riskLevel: "MEDIUM",
      entryPrice: 180.5,
      invalidationPrice: 171.0,
      target1Price: 194.0,
      target2Price: 208.0,
      summaryKurdish: "شیکاری تەکنیکی لەسەر بنەمای RSI و پشتگیری",
      summaryEnglish: "Technical confluence setup based on RSI and support zone",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("signalHistory").doc("sig_bad").set({
      instId: "SOL-USDT",
      userId: ALICE_UID,
      sourceType: "CHART_IMAGE_AI",
      timeframe: "4H",
      signalCategory: "BUY_SETUP",
      confluenceScore: 150,
      riskLevel: "MEDIUM",
      entryPrice: 180.5,
      invalidationPrice: 171.0,
      target1Price: 194.0,
      target2Price: 208.0,
      summaryKurdish: "شیکاری",
      summaryEnglish: "Analysis",
      createdAt: now,
      updatedAt: now,
    })
  );
});
