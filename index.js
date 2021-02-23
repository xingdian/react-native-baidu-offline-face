import { NativeModules, requireNativeComponent } from 'react-native';

const { RNBaiduFace } = NativeModules;
const RNBaiduFaceCameraView = requireNativeComponent('RNBaiduFaceCameraView');

export { RNBaiduFace as RNBaiduFaceSdk, RNBaiduFaceCameraView };
