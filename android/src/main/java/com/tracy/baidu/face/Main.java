package com.tracy.baidu.face;

import android.app.Activity;

public class Main {
    private static Main instance;
    public boolean isCameraCheck = false;

    public boolean isDebug = true;

    public int rgbCameraIndex = 0; // rgb摄像头索引
    public int nirCameraIndex = 1; // nir摄像头索引
    public int depthCameraIndex = 1; // depth摄像头索引

    public Activity mactivity;

    private Main() {

    }

    public static synchronized Main getInstance() {
        if (instance == null) {
            instance = new Main();
        }

        return instance;
    }
}
