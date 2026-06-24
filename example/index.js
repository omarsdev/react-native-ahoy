import { AppRegistry } from 'react-native';
import { registerAhoyIncomingCallComponent } from 'react-native-ahoy';
import App from './src/App';
import IncomingCallScreen from './src/IncomingCallScreen';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);

// T6: the React component the library renders full-screen over the lock screen
// for an incoming call (killed app / screen off).
registerAhoyIncomingCallComponent(IncomingCallScreen);

if (typeof document !== 'undefined') {
  AppRegistry.runApplication(appName, {
    rootTag: document.getElementById('root'),
  });
}
