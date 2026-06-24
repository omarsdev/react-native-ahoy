import { GoogleAuth } from 'google-auth-library';

// FCM HTTP v1 high-priority push sender (the legacy server-key API is removed).
// Sends a DATA-ONLY message at android.priority "high".
//
// !!! DATA-ONLY — do NOT add a `notification` key. A notification payload is
// routed to the system tray and AhoyMessagingService.onMessageReceived NEVER runs
// in the background, so the killed/locked device cannot be woken to ring.
//
// android.priority "high" is what wakes a Dozing device AND qualifies the message
// as the background-start exemption for the phoneCall foreground service (API 31+).
export function createFcmSender({ serviceAccountPath, projectId }) {
  const auth = new GoogleAuth({
    keyFile: serviceAccountPath,
    scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
  });

  return async function sendCallPush(fcmToken, call) {
    const client = await auth.getClient();
    const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
    const res = await client.request({
      url,
      method: 'POST',
      data: {
        message: {
          token: fcmToken,
          android: { priority: 'high' }, // REQUIRED
          // DATA-ONLY — values must be strings. Read by AhoyMessagingService.
          data: {
            type: 'incoming_call',
            callUUID: call.uuid,
            handle: String(call.handle ?? ''),
            callerName: String(call.callerName ?? ''),
          },
        },
      },
    });
    console.log(`[fcm] sent -> ${res.data?.name ?? res.status}`);
    return res.data;
  };
}
