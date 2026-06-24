import { WebSocketServer } from 'ws';

// Reference, NOT production. A dumb room-based WebSocket relay: clients `register`
// a userId (+ their push token), and offer/answer/ice/bye messages are forwarded
// to the named peer. It contains NO call logic — that lives in the app over ahoy.
// ahoy itself ships no signaling; this only exists so a fresh clone can ring one
// real device from another.
//
// Message shapes (all JSON):
//   { type: 'register', userId, platform: 'ios'|'android', voipToken?, fcmToken? }
//   { type: 'call', from, to, uuid, handle, callerName }   -> triggers a push
//   { type: 'offer'|'answer'|'ice'|'bye', from, to, ... }  -> relayed to `to`
export function startSignaling(port, { onCall } = {}) {
  const peers = new Map(); // userId -> ws
  const tokens = new Map(); // userId -> { platform, voipToken?, fcmToken? }

  const wss = new WebSocketServer({ port });

  wss.on('connection', (ws) => {
    ws.on('message', (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw);
      } catch {
        return;
      }

      if (msg.type === 'register') {
        ws.userId = msg.userId;
        peers.set(msg.userId, ws);
        tokens.set(msg.userId, {
          platform: msg.platform,
          voipToken: msg.voipToken,
          fcmToken: msg.fcmToken,
        });
        console.log(`[signal] register ${msg.userId} (${msg.platform})`);
        return;
      }

      // A call request: ring the callee's device via push, then relay signaling.
      if (msg.type === 'call') {
        console.log(`[signal] call ${msg.from} -> ${msg.to} uuid=${msg.uuid}`);
        onCall?.(msg, tokens.get(msg.to));
      }

      // Relay everything addressed to a peer (offer/answer/ice/bye/call).
      const target = msg.to && peers.get(msg.to);
      if (target && target.readyState === target.OPEN) {
        target.send(JSON.stringify(msg));
      }
    });

    ws.on('close', () => {
      if (ws.userId) {
        peers.delete(ws.userId);
        tokens.delete(ws.userId);
        console.log(`[signal] disconnect ${ws.userId}`);
      }
    });
  });

  console.log(`[signal] WebSocket relay on ws://0.0.0.0:${port}`);
  return wss;
}
