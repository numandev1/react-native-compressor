import { registerCallableModule } from 'react-native';
import RCTDeviceEventEmitter from 'react-native/Libraries/EventEmitter/RCTDeviceEventEmitter';
import RCTNativeAppEventEmitter from 'react-native/Libraries/EventEmitter/RCTNativeAppEventEmitter';
import RCTLog from 'react-native/Libraries/Utilities/RCTLog';
import HMRClient from 'react-native/Libraries/Utilities/HMRClient';

if (global.RN_HARNESS) {
  registerCallableModule('RCTDeviceEventEmitter', RCTDeviceEventEmitter);
  registerCallableModule('RCTNativeAppEventEmitter', RCTNativeAppEventEmitter);
  registerCallableModule('RCTLog', RCTLog);
  registerCallableModule('HMRClient', HMRClient);
}
