import { useEffect, useState } from 'react';
import { Text, View, StyleSheet } from 'react-native';
import Ahoy from 'react-native-ahoy';

export default function App() {
  // T1 sanity: proves getEnforcing('Ahoy') resolved the native module and the
  // typed event API generated correctly (subscription with a working .remove()).
  const [moduleResolved] = useState(() => Ahoy != null);
  const [lastAnswered, setLastAnswered] = useState<string>('none');

  useEffect(() => {
    const sub = Ahoy.onAnswerCall(({ uuid }) => setLastAnswered(uuid));
    return () => sub.remove();
  }, []);

  return (
    <View style={styles.container}>
      <Text>Ahoy module resolved: {String(moduleResolved)}</Text>
      <Text>Last answered call: {lastAnswered}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#fff',
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
