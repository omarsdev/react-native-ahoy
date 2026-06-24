import { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import Ahoy, { type AhoyIncomingCallProps } from 'react-native-ahoy';

// T6: the FULL-SCREEN call UI the library renders over the lock screen (even on a
// killed app). WhatsApp-style: it shows the RINGING layout, then transitions to
// the IN-CALL layout after Answer — the same component, the same activity. Answer
// dismisses the keyguard natively; Decline ends the call and leaves the phone
// locked. It also reacts to answers/ends triggered elsewhere (notification button
// or a remote cancel) so the UI stays in sync.
export default function IncomingCallScreen({
  uuid,
  callerName,
  handle,
}: AhoyIncomingCallProps) {
  const [answered, setAnswered] = useState(false);
  const [muted, setMuted] = useState(false);
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    const subs = [
      // Answered from the notification action (not this screen's button).
      Ahoy.onAnswerCall(({ uuid: u }) => {
        if (u === uuid) setAnswered(true);
      }),
      Ahoy.onToggleMute(({ uuid: u, muted: m }) => {
        if (u === uuid) setMuted(m);
      }),
    ];
    return () => subs.forEach((s) => s.remove());
  }, [uuid]);

  // Tick the in-call duration once answered.
  useEffect(() => {
    if (!answered) return;
    const t = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [answered]);

  const answer = () => {
    Ahoy.answerIncomingCall(uuid);
    setAnswered(true);
  };
  const decline = () => Ahoy.rejectCall(uuid);
  const hangUp = () => Ahoy.endCall(uuid);
  const toggleMute = () => Ahoy.setMutedCall(uuid, !muted);

  const mmss = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(
    seconds % 60
  ).padStart(2, '0')}`;

  return (
    <View style={styles.root}>
      <View style={styles.header}>
        <Text style={styles.name}>{callerName || 'Incoming call'}</Text>
        {!!handle && <Text style={styles.handle}>{handle}</Text>}
        <Text style={styles.badge}>{answered ? mmss : 'Incoming call'}</Text>
      </View>

      {answered ? (
        <View style={styles.row}>
          <TouchableOpacity
            style={[styles.btn, muted ? styles.active : styles.neutral]}
            onPress={toggleMute}
          >
            <Text style={styles.btnText}>{muted ? 'Unmute' : 'Mute'}</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.btn, styles.decline]}
            onPress={hangUp}
          >
            <Text style={styles.btnText}>Hang up</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={styles.row}>
          <TouchableOpacity
            style={[styles.btn, styles.decline]}
            onPress={decline}
          >
            <Text style={styles.btnText}>Decline</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.btn, styles.answer]}
            onPress={answer}
          >
            <Text style={styles.btnText}>Answer</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#0B1221',
    paddingTop: 96,
    paddingBottom: 56,
    paddingHorizontal: 24,
    justifyContent: 'space-between',
  },
  header: { alignItems: 'center' },
  name: { color: '#fff', fontSize: 30, fontWeight: '600', textAlign: 'center' },
  handle: { color: '#9AA4B2', fontSize: 16, marginTop: 8 },
  badge: { color: '#4B5563', fontSize: 14, marginTop: 16 },
  row: { flexDirection: 'row', justifyContent: 'space-evenly' },
  btn: {
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 12,
    minWidth: 128,
    alignItems: 'center',
  },
  decline: { backgroundColor: '#E5484D' },
  answer: { backgroundColor: '#30A46C' },
  neutral: { backgroundColor: '#1F2A3C' },
  active: { backgroundColor: '#3358D4' },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
