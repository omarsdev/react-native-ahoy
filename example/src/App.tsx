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
import Ahoy, {
  getReliabilityStatus,
  type AhoyReliabilityStatus,
} from 'react-native-ahoy';

// Hermes has no crypto.randomUUID; small RFC4122-v4 generator for the demo.
function uuidv4(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16);
    const v = c === 'x' ? r : (r % 4) + 8;
    return v.toString(16);
  });
}

// Thin test harness: every button calls ONE native API and the screen logs the
// events the native module emits. No call-state logic lives here — the native
// module owns the lifecycle (call waiting, hold/resume, the single-outgoing
// guard, notifications). `lastCall` is just the uuid the per-call buttons target.
export default function App() {
  const [log, setLog] = useState<string[]>([]);
  const [status, setStatus] = useState<AhoyReliabilityStatus | null>(null);
  const lastCall = useRef<string | null>(null);

  const append = (line: string) => {
    console.log(`[Ahoy:JS] ${line}`);
    setLog((prev) => [`${new Date().toLocaleTimeString()}  ${line}`, ...prev]);
  };

  useEffect(() => {
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      PermissionsAndroid.request(
        'android.permission.POST_NOTIFICATIONS' as Parameters<
          typeof PermissionsAndroid.request
        >[0]
      ).catch(() => {});
    }
    Ahoy.setup({ label: 'Ahoy Example' })
      .then(() => append('setup ok'))
      .catch((e) => append(`setup failed: ${e}`));

    // T4: log the push token (Android FCM / iOS VoIP) so it's easy to copy for
    // the manual send scripts. Also fires via the onVoipPushToken event on iOS.
    Ahoy.getVoipPushToken()
      .then((t) => append(`push token: ${t}`))
      .catch((e) => append(`getVoipPushToken: ${e}`));

    // T6: snapshot the device reliability state on launch (re-check on resume in a
    // real app — OEM toggles reset on reboot/OS update).
    getReliabilityStatus()
      .then((s) => {
        setStatus(s);
        append(`reliability: ${JSON.stringify(s)}`);
      })
      .catch((e) => append(`getReliabilityStatus: ${e}`));

    const subs = [
      Ahoy.onStartCallAction(({ uuid }) =>
        append(`event onStartCallAction uuid=${uuid}`)
      ),
      Ahoy.onAnswerCall(({ uuid }) =>
        append(`event onAnswerCall uuid=${uuid}`)
      ),
      Ahoy.onEndCall(({ uuid }) => append(`event onEndCall uuid=${uuid}`)),
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
      Ahoy.onProviderReset(() => append('event onProviderReset')),
      Ahoy.onVoipPushToken(({ token }) =>
        append(`event onVoipPushToken: ${token}`)
      ),
    ];
    return () => subs.forEach((s) => s.remove());
  }, []);

  // Each handler is a single native call.

  const startOutgoing = () => {
    const uuid = uuidv4();
    append(`call startCall uuid=${uuid}`);
    Ahoy.startCall({ uuid, handle: '+15551234567', hasVideo: false })
      .then(() => {
        lastCall.current = uuid; // only track calls the native side accepted
        append('startCall resolved');
      })
      .catch((e) => append(`startCall rejected: ${e}`));
  };

  const markConnected = () => {
    const uuid = lastCall.current;
    if (!uuid) return;
    append(`call reportConnectedOutgoingCall uuid=${uuid}`);
    Ahoy.reportConnectedOutgoingCall(uuid);
  };

  const simulateIncoming = () => {
    const uuid = uuidv4();
    append(`call displayIncomingCall uuid=${uuid}`);
    Ahoy.displayIncomingCall({
      uuid,
      handle: '+15557654321',
      localizedCallerName: 'Ada Lovelace',
    })
      .then(() => {
        lastCall.current = uuid;
        append('displayIncomingCall resolved');
      })
      .catch((e) => append(`displayIncomingCall rejected: ${e}`));
  };

  const answerLast = () => {
    const uuid = lastCall.current;
    if (!uuid) return;
    append(`call answerIncomingCall uuid=${uuid}`);
    Ahoy.answerIncomingCall(uuid);
  };

  const holdLast = (onHold: boolean) => {
    const uuid = lastCall.current;
    if (!uuid) return;
    append(`call setOnHold uuid=${uuid} hold=${onHold}`);
    Ahoy.setOnHold(uuid, onHold);
  };

  const endLast = () => {
    const uuid = lastCall.current;
    if (!uuid) return;
    append(`call endCall uuid=${uuid}`);
    Ahoy.endCall(uuid);
  };

  const endAll = () => {
    append('call endAllCalls');
    Ahoy.endAllCalls();
  };

  // T6: OEM reliability surface. Each button is one native call; the status line
  // shows getReliabilityStatus() so onboarding can branch on the real device state.
  const refreshStatus = () => {
    getReliabilityStatus()
      .then((s) => {
        setStatus(s);
        append(`reliability: ${JSON.stringify(s)}`);
      })
      .catch((e) => append(`getReliabilityStatus: ${e}`));
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Ahoy — native test harness</Text>
      <View style={styles.row}>
        <Button label="Start outgoing" onPress={startOutgoing} />
        <Button label="Mark connected" onPress={markConnected} />
        <Button label="Simulate incoming" onPress={simulateIncoming} />
        <Button label="Answer last" onPress={answerLast} />
        <Button label="Hold last" onPress={() => holdLast(true)} />
        <Button label="Resume last" onPress={() => holdLast(false)} />
        <Button label="End last" onPress={endLast} />
        <Button label="End all" onPress={endAll} />
      </View>
      <Text style={styles.subtitle}>Reliability (T6)</Text>
      {status && (
        <Text style={styles.status}>
          {status.manufacturer} (api {status.sdkInt}) · battery-exempt:{' '}
          {status.isIgnoringBatteryOptimizations ? 'yes' : 'no'} · fsi:{' '}
          {status.canUseFullScreenIntent ? 'yes' : 'no'} · autostart:{' '}
          {status.hasAutostartSettings ? 'yes' : 'no'}
        </Text>
      )}
      <View style={styles.row}>
        <Button label="Reliability status" onPress={refreshStatus} />
        <Button
          label="Battery settings"
          onPress={() => Ahoy.openBatteryOptimizationSettings()}
        />
        <Button
          label="Battery exempt"
          onPress={() => Ahoy.requestBatteryOptimizationExemption()}
        />
        <Button
          label="OEM autostart"
          onPress={() =>
            Ahoy.openManufacturerAutostartSettings().then((ok) =>
              append(`openManufacturerAutostartSettings -> ${ok}`)
            )
          }
        />
        <Button
          label="FSI settings"
          onPress={() => Ahoy.openFullScreenIntentSettings()}
        />
      </View>
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
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  button: {
    backgroundColor: '#0a7',
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
  },
  buttonText: { color: '#fff', fontWeight: '600' },
  status: { fontFamily: 'Menlo', fontSize: 11, color: '#333', marginBottom: 6 },
  log: { flex: 1, marginTop: 4 },
  logLine: { fontFamily: 'Menlo', fontSize: 11, paddingVertical: 1 },
});
