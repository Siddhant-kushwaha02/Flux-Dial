const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.initiateCall = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Login required");
  }

  const { calleeUid, callerUsername, callId } = data;

  // Get callee's FCM token from Firestore
  const calleeDoc = await admin.firestore()
    .collection("users")
    .doc(calleeUid)
    .get();

  if (!calleeDoc.exists) {
    throw new functions.https.HttpsError("not-found", "User not found");
  }

  const fcmToken = calleeDoc.data().fcmToken;

  // Save call record to Firestore
  await admin.firestore().collection("calls").doc(callId).set({
    callerUid: context.auth.uid,
    calleeUid: calleeUid,
    status: "ringing",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Send FCM notification to ring the callee's phone
  await admin.messaging().send({
    token: fcmToken,
    data: {
      type: "incoming_call",
      callerUsername: callerUsername,
      callId: callId,
    },
    android: {
      priority: "high",
    },
  });

  return { success: true };
});