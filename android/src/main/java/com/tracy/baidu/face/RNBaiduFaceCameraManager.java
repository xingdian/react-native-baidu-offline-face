package com.tracy.baidu.face;

import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;


public class RNBaiduFaceCameraManager extends ViewGroupManager<RNBaiduFaceCameraView> {
    public static final String REACT_CLASS = "RNBaiduFaceCameraView";
    ThemedReactContext context;

    @Override
    public String getName() {
        return REACT_CLASS;

}
    @Override
    public RNBaiduFaceCameraView createViewInstance(ThemedReactContext context) {
        this.context = context;
        final ThemedReactContext mcontext = context;
        final RNBaiduFaceCameraView cameraView = new RNBaiduFaceCameraView(context);

        return cameraView;
    }
}
