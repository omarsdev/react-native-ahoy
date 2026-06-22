import { useEffect, useRef, useState } from 'react';
import {
  Text,
  View,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Platform,
  PermissionsAndroid,
} from 'react-native';
import Ahoy from 'react-native-ahoy';

// Hermes has no crypto.randomUUID; small RFC4122-v4 generator for the demo.
function uuidv4(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    const v = c === 'x' ? r : (r % 4) + 8;
    return v.toString(16);
  });
}

// CallKit upper-cases UUIDs, so normalize everywhere we compare/track.
const norm = (uuid: string) => uuid.toLowerCase();

export default function App() {
  const [log, setLog] = useState<string[]>([]);
  const [active, setActive] = useState<string[]>([]);
  // All calls we believe are live, keyed by normalized uuid.
  const activeCalls = useRef<Set<string>>(new Set());

  // Mirror to the on-screen log AND the Metro console ([Ahoy:JS] prefix) so the
  // JS half can be pasted alongside the native [Ahoy] device logs.
  const append = (line: string) => {
    console.log(`[Ahoy:JS] ${line}`);
    setLog((prev) => [`${new Date().toLocaleTimeString()}  ${line}`, ...prev]);
  };

  const syncActive = () => setActive([...activeCalls.current]);
  const track = (uuid: string) => {
    activeCalls.current.add(norm(uuid));
    syncActive();
  };
  const untrack = (uuid: string) => {
    activeCalls.current.delete(norm(uuid));
    syncActive();
  };

  useEffect(() => {
    // Android 13+: notifications need runtime permission to be visible.
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      PermissionsAndroid.request(
        'android.permission.POST_NOTIFICATIONS' as Parameters<
          typeof PermissionsAndroid.request
        >[0]
      )
        .then((r) => append(`POST_NOTIFICATIONS: ${r}`))
        .catch(() => {});
    }
    // Register the call account (Android: self-managed PhoneAccount; iOS: no-op).
    Ahoy.setup({ label: 'Ahoy Example' })
      .then(() => append('setup ok'))
      .catch((e) => append(`setup failed: ${e}`));

    append('subscribing to events');
    const subs = [
      Ahoy.onStartCallAction(({ uuid }) =>
        append(`event onStartCallAction uuid=${uuid}`)
      ),
      Ahoy.onAnswerCall(({ uuid }) =>
        append(`event onAnswerCall uuid=${uuid}`)
      ),
      // A call genuinely ended (native CXEndCallAction path): drop it.
      Ahoy.onEndCall(({ uuid }) => {
        append(`event onEndCall uuid=${uuid}`);
        untrack(uuid);
      }),
      Ahoy.onToggleMute(({ uuid, muted }) =>
        append(`event onToggleMute uuid=${uuid} muted=${muted}`)
      ),
      Ahoy.onToggleHold(({ uuid, onHold }) =>
        append(`event onToggleHold uuid=${uuid} onHold=${onHold}`)
      ),
      Ahoy.onDidActivateAudioSession(() =>
        append('event onDidActivateAudioSession')
      ),
      Ahoy.onDidDeactivateAudioSession(() =>
        append('event onDidDeactivateAudioSession')
      ),
      Ahoy.onProviderReset(() => {
        append('event onProviderReset');
        activeCalls.current.clear();
        syncActive();
      }),
    ];
    return () => subs.forEach((s) => s.remove());
    // Subscribe once on mount; helpers close over stable refs/setters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startOutgoing = () => {
    const uuid = uuidv4();
    append(`call startCall uuid=${uuid} handle=+15551234567`);
    Ahoy.startCall({ uuid, handle: '+15551234567', hasVideo: false });
    track(uuid);
    // Simulate signaling reporting the remote answered after a moment.
    setTimeout(() => {
      if (activeCalls.current.has(norm(uuid))) {
        append(`call reportConnectedOutgoingCall uuid=${uuid}`);
        Ahoy.reportConnectedOutgoingCall(uuid);
      }
    }, 2000);
  };

  const simulateIncoming = () => {
    const uuid = uuidv4();
    append(`call displayIncomingCall uuid=${uuid} caller=Ada Lovelace`);
    Ahoy.displayIncomingCall({
      uuid,
      handle: '+15557654321',
      localizedCallerName: 'Ada Lovelace',
    });
    track(uuid);
  };

  // End ONE specific call (in-app hang-up / decline an incoming): CXEndCallAction
  // transaction. Native emits onEndCall, which untracks it. Other calls survive.
  const endOne = (uuid: string) => {
    append(`call endCall uuid=${uuid}`);
    Ahoy.endCall(uuid);
  };

  const endAll = () => {
    append('call endAllCalls');
    Ahoy.endAllCalls();
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Ahoy — CallKit demo (T2)</Text>
      <View style={styles.row}>
        <Button label="Start outgoing" onPress={startOutgoing} />
        <Button label="Simulate incoming" onPress={simulateIncoming} />
        <Button label="End all" onPress={endAll} />
      </View>
      <Text style={styles.subtitle}>Active calls ({active.length})</Text>
      {active.length === 0 ? (
        <Text style={styles.muted}>none</Text>
      ) : (
        active.map((uuid) => (
          <View key={uuid} style={styles.callRow}>
            <Text style={styles.callId}>{uuid.slice(0, 8)}…</Text>
            <Button label="End / Decline" onPress={() => endOne(uuid)} />
          </View>
        ))
      )}
      <Text style={styles.subtitle}>Events</Text>
      <ScrollView style={styles.log}>
        {log.map((line, i) => (
          <Text key={i} style={styles.logLine}>
            {line}
          </Text>
        ))}
      </ScrollView>
    </View>
  );
}

function Button({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <TouchableOpacity style={styles.button} onPress={onPress}>
      <Text style={styles.buttonText}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 16, paddingTop: 64 },
  title: { fontSize: 18, fontWeight: '600', marginBottom: 12 },
  subtitle: { fontSize: 14, fontWeight: '600', marginTop: 16, marginBottom: 4 },
  muted: { fontSize: 13, color: '#999' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  callRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 4,
  },
  callId: { fontFamily: 'Menlo', fontSize: 13, flex: 1 },
  button: {
    backgroundColor: '#0a7',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
  },
  buttonText: { color: '#fff', fontWeight: '600' },
  log: { flex: 1, marginTop: 4 },
  logLine: { fontFamily: 'Menlo', fontSize: 11, paddingVertical: 1 },
});
