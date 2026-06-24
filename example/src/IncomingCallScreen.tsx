import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import Ahoy, { type AhoyIncomingCallProps } from 'react-native-ahoy';

// T6: the FULL-SCREEN incoming-call UI, rendered by the library over the lock
// screen (even when the app was killed). This is a normal React component — your
// branding, your layout. It gets { uuid, callerName, handle } as initial props.
// Answer/decline route straight into the native call lifecycle.
export default function IncomingCallScreen({
  uuid,
  callerName,
  handle,
}: AhoyIncomingCallProps) {
  return (
    <View style={styles.root}>
      <View style={styles.header}>
        <Text style={styles.name}>{callerName || 'Incoming call'}</Text>
        {!!handle && <Text style={styles.handle}>{handle}</Text>}
        <Text style={styles.badge}>React Native · over the lock screen</Text>
      </View>
      <View style={styles.row}>
        <TouchableOpacity
          style={[styles.btn, styles.decline]}
          onPress={() => Ahoy.rejectCall(uuid)}
        >
          <Text style={styles.btnText}>Decline</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.btn, styles.answer]}
          onPress={() => Ahoy.answerIncomingCall(uuid)}
        >
          <Text style={styles.btnText}>Answer</Text>
        </TouchableOpacity>
      </View>
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
  badge: { color: '#4B5563', fontSize: 12, marginTop: 16 },
  row: { flexDirection: 'row', justifyContent: 'space-evenly' },
  btn: { paddingVertical: 16, paddingHorizontal: 36, borderRadius: 12 },
  decline: { backgroundColor: '#E5484D' },
  answer: { backgroundColor: '#30A46C' },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
