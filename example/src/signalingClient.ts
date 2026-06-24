// T7 REFERENCE (unverified). WebSocket client for the reference backend
// (examples/server). It carries WebRTC negotiation (offer/answer/ice/bye) and the
// `call` request that tells the backend to push-ring the callee. ahoy is uninvolved
// — this is "your signaling layer".

export type SignalMessage = {
  type: 'register' | 'call' | 'offer' | 'answer' | 'ice' | 'bye';
  from?: string;
  to?: string;
  uuid?: string;
  handle?: string;
  callerName?: string;
  sdp?: unknown;
  candidate?: unknown;
  platform?: 'ios' | 'android';
  voipToken?: string;
  fcmToken?: string;
};

export type SignalingClient = {
  register: (info: Omit<SignalMessage, 'type'> & { userId: string }) => void;
  send: (msg: SignalMessage) => void;
  close: () => void;
};

// `url` e.g. ws://192.168.1.10:8080 (your machine on the LAN, reachable from the
// devices). onMessage receives relayed offer/answer/ice/bye for this peer.
export function connectSignaling(
  url: string,
  onMessage: (msg: SignalMessage) => void
): SignalingClient {
  const ws = new WebSocket(url);
  const queue: string[] = [];

  const rawSend = (data: string) => {
    if (ws.readyState === WebSocket.OPEN) ws.send(data);
    else queue.push(data);
  };

  ws.onopen = () => {
    queue.splice(0).forEach((d) => ws.send(d));
  };
  ws.onmessage = (e) => {
    try {
      onMessage(JSON.parse(String(e.data)));
    } catch {
      // ignore malformed frames
    }
  };

  return {
    register: ({ userId, ...rest }) =>
      rawSend(JSON.stringify({ type: 'register', userId, ...rest })),
    send: (msg) => rawSend(JSON.stringify(msg)),
    close: () => ws.close(),
  };
}
