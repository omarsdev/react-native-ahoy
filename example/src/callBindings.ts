// T7 REFERENCE (unverified — needs two physical devices + coturn). The glue that
// makes ahoy (call UI/lifecycle) and react-native-webrtc (media) one calling app.
//
// ahoy emits lifecycle events; this maps them to WebRTC + signaling:
//   answerCall  -> answer the buffered remote offer
//   endCall     -> close the peer connection + send `bye`
//   toggleMute  -> enable/disable the local audio track
// and signaling messages drive the negotiation (offer/answer/ice).
import Ahoy from 'react-native-ahoy';
import {
  createCallSession,
  makeOffer,
  makeAnswer,
  applyAnswer,
  addRemoteIce,
  setMuted,
  closeSession,
  type CallSession,
} from './callService';
import {
  connectSignaling,
  type SignalingClient,
  type SignalMessage,
} from './signalingClient';

export type CallController = {
  // Outgoing: ring `to` (the backend pushes them), then negotiate media.
  placeCall: (to: string, handle: string, callerName: string) => Promise<void>;
  dispose: () => void;
};

export function wireAhoyToWebRTC(opts: {
  selfId: string;
  signalingUrl: string;
  uuid: () => string;
}): CallController {
  let session: CallSession | null = null;
  let peerId: string | null = null;
  let currentUuid: string | null = null;
  let pendingOffer: unknown = null; // remote offer waiting for the user to answer

  const signaling: SignalingClient = connectSignaling(
    opts.signalingUrl,
    onSignal
  );

  async function ensureSession() {
    if (session) return session;
    session = await createCallSession({
      onIceCandidate: (candidate) =>
        signaling.send({ type: 'ice', to: peerId!, candidate }),
      onRemoteStream: () => {
        /* react-native-webrtc plays remote audio automatically */
      },
    });
    return session;
  }

  // ---- incoming signaling ----
  async function onSignal(msg: SignalMessage) {
    switch (msg.type) {
      case 'offer':
        // The caller's offer arrived. Show the incoming UI via ahoy; keep the
        // offer until the user answers. (Killed/locked devices were already rung
        // natively by the push; this covers the foreground case.)
        peerId = msg.from ?? null;
        currentUuid = msg.uuid ?? null;
        pendingOffer = msg.sdp;
        if (currentUuid) {
          await Ahoy.displayIncomingCall({
            uuid: currentUuid,
            handle: msg.handle ?? '',
            localizedCallerName: msg.callerName,
          });
        }
        break;
      case 'answer':
        if (session) await applyAnswer(session.pc, msg.sdp);
        break;
      case 'ice':
        if (session) await addRemoteIce(session.pc, msg.candidate);
        break;
      case 'bye':
        if (currentUuid) Ahoy.endCall(currentUuid);
        break;
    }
  }

  // ---- ahoy lifecycle -> media ----
  const subs = [
    Ahoy.onAnswerCall(async ({ uuid }) => {
      currentUuid = uuid;
      const s = await ensureSession();
      if (pendingOffer) {
        const answer = await makeAnswer(s.pc, pendingOffer);
        pendingOffer = null;
        signaling.send({ type: 'answer', to: peerId!, uuid, sdp: answer });
      }
    }),
    Ahoy.onEndCall(({ uuid }) => {
      if (peerId) signaling.send({ type: 'bye', to: peerId, uuid });
      if (session) closeSession(session);
      session = null;
      peerId = null;
      pendingOffer = null;
      currentUuid = null;
    }),
    Ahoy.onToggleMute(({ muted }) => {
      if (session) setMuted(session, muted);
    }),
  ];

  return {
    async placeCall(to, handle, callerName) {
      peerId = to;
      currentUuid = opts.uuid();
      // Tell the backend to ring `to` (it sends the APNs/FCM push)…
      signaling.send({
        type: 'call',
        from: opts.selfId,
        to,
        uuid: currentUuid,
        handle,
        callerName,
      });
      await Ahoy.startCall({ uuid: currentUuid, handle });
      // …then make + send the WebRTC offer over signaling.
      const s = await ensureSession();
      const offer = await makeOffer(s.pc);
      signaling.send({ type: 'offer', to, uuid: currentUuid, sdp: offer });
    },
    dispose() {
      subs.forEach((sub) => sub.remove());
      if (session) closeSession(session);
      signaling.close();
    },
  };
}
