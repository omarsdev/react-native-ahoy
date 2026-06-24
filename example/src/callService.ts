// T7 REFERENCE (unverified — needs two physical devices + a reachable coturn).
//
// The media half of a call. ahoy owns the call UI/lifecycle; this owns the
// RTCPeerConnection + audio track. ahoy never imports react-native-webrtc — it
// lives in example/ only (DECISIONS #6). Wire it to ahoy events in callBindings.ts.
import {
  RTCPeerConnection,
  RTCSessionDescription,
  RTCIceCandidate,
  mediaDevices,
  type MediaStream,
} from 'react-native-webrtc';

// STUN finds your public address; TURN relays media when a direct path is
// impossible (symmetric NAT / cellular) — mandatory for real-world calls. Point
// `turn:` at your own coturn; ahoy is uninvolved in how you supply credentials.
const ICE_SERVERS = [
  { urls: 'stun:stun.l.google.com:19302' },
  {
    urls: 'turn:YOUR_COTURN_HOST:3478',
    username: 'demo',
    credential: 'demo-secret',
  },
];

export type CallSession = {
  pc: RTCPeerConnection;
  localStream: MediaStream;
};

type Handlers = {
  // Send your local ICE candidates to the peer over signaling.
  onIceCandidate: (candidate: unknown) => void;
  // A remote media stream arrived — route it to your audio output / UI.
  onRemoteStream: (stream: MediaStream) => void;
};

// Create the peer connection + capture the mic. Audio-only here; add video by
// requesting `{ video: true }` and an `RTCView` in the UI.
export async function createCallSession(h: Handlers): Promise<CallSession> {
  const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });

  // @ts-expect-error react-native-webrtc event typings are loose
  pc.addEventListener('icecandidate', (e: { candidate?: unknown }) => {
    if (e.candidate) h.onIceCandidate(e.candidate);
  });
  // @ts-expect-error react-native-webrtc event typings are loose
  pc.addEventListener('track', (e: { streams: MediaStream[] }) => {
    if (e.streams && e.streams[0]) h.onRemoteStream(e.streams[0]);
  });

  const localStream = await mediaDevices.getUserMedia({ audio: true });
  localStream.getTracks().forEach((track) => pc.addTrack(track, localStream));

  return { pc, localStream };
}

// Caller side: create + send the offer.
export async function makeOffer(pc: RTCPeerConnection) {
  const offer = await pc.createOffer({});
  await pc.setLocalDescription(offer);
  return pc.localDescription;
}

// Callee side: accept the remote offer and answer.
export async function makeAnswer(pc: RTCPeerConnection, remoteOffer: unknown) {
  await pc.setRemoteDescription(
    new RTCSessionDescription(remoteOffer as never)
  );
  const answer = await pc.createAnswer();
  await pc.setLocalDescription(answer);
  return pc.localDescription;
}

// Caller side: apply the callee's answer.
export async function applyAnswer(pc: RTCPeerConnection, answer: unknown) {
  await pc.setRemoteDescription(new RTCSessionDescription(answer as never));
}

export async function addRemoteIce(pc: RTCPeerConnection, candidate: unknown) {
  await pc.addIceCandidate(new RTCIceCandidate(candidate as never));
}

export function setMuted(session: CallSession, muted: boolean) {
  session.localStream.getAudioTracks().forEach((t) => (t.enabled = !muted));
}

export function closeSession(session: CallSession) {
  session.localStream.getTracks().forEach((t) => t.stop());
  session.pc.close();
}
