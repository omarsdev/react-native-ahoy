import http2 from 'node:http2';
import { readFileSync } from 'node:fs';
import jwt from 'jsonwebtoken';

// APNs VoIP push sender via raw http2 (no stale deps). A VoIP push is the ONLY
// way to ring a killed iOS app: the app's PushKit handler reports the call to
// CallKit synchronously (T2). Custom keys (uuid/handle/callerName) ride at the top
// level of the payload and are read by AhoyVoipPushManager.
//
// Non-negotiable APNs rules:
//   apns-push-type: voip          apns-topic: <bundleId>.voip   (the .voip suffix)
//   apns-priority: 10             ES256 JWT from the .p8 (kid=KeyId, iss=TeamId)
//   host: api.push.apple.com (prod build) / api.sandbox.push.apple.com (dev build)
export function createVoipSender({ p8Path, keyId, teamId, bundleId, host }) {
  const p8 = readFileSync(p8Path);
  const apnsHost =
    host === 'prod'
      ? 'https://api.push.apple.com:443'
      : 'https://api.sandbox.push.apple.com:443';

  return function sendVoipPush(deviceToken, call) {
    return new Promise((resolve, reject) => {
      const bearer = jwt.sign(
        { iss: teamId, iat: Math.floor(Date.now() / 1000) },
        p8,
        { algorithm: 'ES256', header: { alg: 'ES256', kid: keyId } }
      );
      const client = http2.connect(apnsHost);
      client.on('error', reject);
      const req = client.request({
        ':method': 'POST',
        ':path': `/3/device/${deviceToken}`,
        'authorization': `bearer ${bearer}`,
        'apns-topic': `${bundleId}.voip`, // .voip suffix is mandatory
        'apns-push-type': 'voip',
        'apns-priority': '10',
        'apns-expiration': '0',
      });
      let status = 0;
      let body = '';
      req.on('response', (h) => (status = h[':status']));
      req.on('data', (d) => (body += d));
      req.on('end', () => {
        client.close();
        if (status === 200) {
          console.log(`[voip] sent uuid=${call.uuid} (${apnsHost})`);
          resolve({ status });
        } else {
          console.error(`[voip] APNs ${status}: ${body}`);
          reject(new Error(`APNs ${status}: ${body}`));
        }
      });
      req.end(
        JSON.stringify({
          callUUID: call.uuid,
          handle: call.handle ?? '',
          callerName: call.callerName ?? '',
          hasVideo: false,
        })
      );
    });
  };
}
