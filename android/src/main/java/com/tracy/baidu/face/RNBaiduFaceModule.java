package com.tracy.baidu.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.baidu.idl.face.main.api.FaceApi;
import com.baidu.idl.face.main.listener.SdkInitListener;
import com.baidu.idl.face.main.manager.FaceSDKManager;
import com.baidu.idl.face.main.model.ImportFeatureResult;
import com.baidu.idl.face.main.model.SingleBaseConfig;
import com.baidu.idl.face.main.model.User;
import com.baidu.idl.face.main.utils.BitmapUtils;
import com.baidu.idl.face.main.utils.FileUtils;
import com.baidu.idl.main.facesdk.model.BDFaceSDKCommon;
import com.baidu.vis.unified.license.AndroidLicenser;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.openni.OpenNI;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

public class RNBaiduFaceModule extends ReactContextBaseJavaModule {
    private static final String TAG = "RNBaiduFaceModule";
    private final ReactApplicationContext reactContext;

    public RNBaiduFaceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
//        Main.getInstance().mactivity = reactContext.getCurrentActivity();

        // 设置SDK Log 日志是否输出
        OpenNI.setLogAndroidOutput(true);
        // 设置Log日志输出级别
        OpenNI.setLogMinSeverity(0);
        // 初始化SDK
        OpenNI.initialize();
    }

    @Override
    public String getName() {
        return "RNBaiduFace";
    }

    /**
     * 获取百度人脸设备指纹
     */
    @ReactMethod
    public void getDeviceId(Promise promise) {
        String deviceId = AndroidLicenser.getDeviceId(reactContext.getApplicationContext());
        WritableMap res = Arguments.createMap();
        res.putString("deviceId", deviceId);
        promise.resolve(res);
    }


    /**
     * 鉴权初始化
     */
    @ReactMethod
    public void initLicenseOnline(String licenseId, final Promise promise) {
        FaceSDKManager.getInstance().initLicenseOnline(reactContext, licenseId, new SdkInitListener() {
            @Override
            public void initStart() {
            }

            @Override
            public void initLicenseSuccess() {
                promise.resolve(null);
            }

            @Override
            public void initLicenseFail(int errorCode, String msg) {
                promise.reject(String.valueOf(errorCode), msg);
            }

            @Override
            public void initModelSuccess() {

            }

            @Override
            public void initModelFail(int errorCode, String msg) {

            }
        });
    }

    /**
     * 鉴权初始化
     */
    @ReactMethod
    public void initLicenseBatchLine(String licenseId, final Promise promise) {
        FaceSDKManager.getInstance().initLicenseBatchLine(reactContext, licenseId, new SdkInitListener() {
            @Override
            public void initStart() {
            }

            @Override
            public void initLicenseSuccess() {
                promise.resolve(null);
            }

            @Override
            public void initLicenseFail(int errorCode, String msg) {
                promise.reject(String.valueOf(errorCode), msg);
            }

            @Override
            public void initModelSuccess() {

            }

            @Override
            public void initModelFail(int errorCode, String msg) {

            }
        });
    }

    /**
     * 模型初始化
     */
    @ReactMethod
    public void initModel(final Promise promise) {
        if (FaceSDKManager.initStatus == FaceSDKManager.SDK_MODEL_LOAD_SUCCESS) {
            promise.resolve(null);
            return;
        }

        FaceSDKManager.getInstance().initModel(reactContext, new SdkInitListener() {
            @Override
            public void initStart() {

            }

            @Override
            public void initLicenseSuccess() {

            }

            @Override
            public void initLicenseFail(int errorCode, String msg) {

            }

            @Override
            public void initModelSuccess() {
                promise.resolve(null);
            }

            @Override
            public void initModelFail(int errorCode, String msg) {
                promise.reject(String.valueOf(errorCode), msg);
            }
        });
    }

    /**
     * 导入用户
     */
    @ReactMethod
    public void importUser(ReadableMap data, Promise promise) {
        String userId = data.getString("userId");
        String avatarUrl = data.getString("avatarUrl");
        String userName = data.getString("userName");
        String userInfo = data.getString("userInfo");
        if (TextUtils.isEmpty(avatarUrl)) {
            promise.reject("1001","头像url不能为空");
            return;
        }
        if (TextUtils.isEmpty(userId)) {
            promise.reject("1002","userId参数错误");
        }

        // 头像转bitmap
        Bitmap bitmap = null;
        try {
            bitmap = getBitmap(avatarUrl);
        } catch (Exception e) {
            promise.reject("1003","用户头像下载失败");
            return;
        }

        try{
            bitmap.getWidth();
        }catch(Exception e) {
        	Log.v("Bitmap===", "图片出错");
            promise.reject("1003","用户头像下载失败，网络图片可能损坏");
            return;
        }
        Log.e("Bitmap===", bitmap.toString());

        // 图片缩放
        if (bitmap.getWidth() * bitmap.getHeight() > 3000 * 2000) {
            if (bitmap.getWidth() > bitmap.getHeight()) {
                float scale = 1 / (bitmap.getWidth() * 1.0f / 1000.0f);
                bitmap = BitmapUtils.scale(bitmap, scale);
            } else {
                float scale = 1 / (bitmap.getHeight() * 1.0f / 1000.0f);
                bitmap = BitmapUtils.scale(bitmap, scale);
            }
        }
        Log.e("Bitmap222===", bitmap.toString());

        byte[] bytes = new byte[512];
        ImportFeatureResult result;
        // 10、走人脸SDK接口，通过人脸检测、特征提取拿到人脸特征值
        result = FaceApi.getInstance().getFeature(bitmap, bytes,
                BDFaceSDKCommon.FeatureType.BDFACE_FEATURE_TYPE_LIVE_PHOTO);

        String picName = userId+'_'+userName+".jpg";

        // 11、判断是否提取成功：128为成功，-1为参数为空，-2表示未检测到人脸
        Log.i(TAG, "live_photo = " + result.getResult());
        if (result.getResult() == -1) {
            Log.e(TAG, userName + "：bitmap参数为空");
            promise.reject("1004", "bitmap参数为空");
            return;
        } else if (result.getResult() == -2) {
            Log.e(TAG, userName + "：未检测到人脸");
            promise.reject("1004", "未检测到人脸");
            return;
        } else if (result.getResult() == -3) {
            Log.e(TAG, userName + "：抠图失败");
            promise.reject("1004", "抠图失败");
            return;
        } else if (result.getResult() == 128) {

        } else {
            Log.e(TAG, picName + "：未检测到人脸");
            promise.reject("1004", "未检测到人脸");
            return;
        }

        // 检查是否已经有用户
        User user = FaceApi.getInstance().getUserByUserId(userId);
        boolean importDBSuccess = false;
        if (user != null) {
            // 更新数据
            importDBSuccess = FaceApi.getInstance().userUpdate(userId, userName, picName, bytes);

        } else {
            // 添加数据
            // 将用户信息保存到数据库中
            importDBSuccess = FaceApi.getInstance().registerUserInfoIntoDBmanager(userId,
                    userName, picName, userInfo, bytes);
        }

        // 保存数据库成功
        if (importDBSuccess) {
            promise.resolve(null);
//            // 保存图片到新目录中
//            File facePicDir = FileUtils.getBatchImportSuccessDirectory();
//            if (facePicDir != null) {
//                File savePicPath = new File(facePicDir, picName);
//                if (FileUtils.saveBitmap(savePicPath, result.getBitmap())) {
//                    Log.i(TAG, "图片保存成功");
//                    promise.resolve(null);
//                } else {
//                    Log.i(TAG, "图片保存失败");
//                    promise.reject("1006", "图片保存失败");
//                }
//
//            }
        } else {
            Log.e(TAG, picName + "：保存到数据库失败");
            promise.reject("1007", "保存到数据库失败");
        }

        // 图片回收
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }

    }

    /**
     * 删除用户
     */
    @ReactMethod
    public void deleteUser(String userId, Promise promise) {
        Boolean result = FaceApi.getInstance().userDeleteByUserId(userId);
        if (result) {
            promise.resolve(null);
        } else {
            promise.reject("1001","删除失败");
        }
    }

    /**
     * 删除用户
     */
    @ReactMethod
    public void deleteUserAll(Promise promise) {
        Boolean result = FaceApi.getInstance().userDeleteAll();
        if (result) {
            promise.resolve(null);
        } else {
            promise.reject("1001","删除失败");
        }
    }

    /**
     * 查询用户信息
     */
    @ReactMethod
    public void getUserByUserId(String userId, Promise promise) {
        User user = FaceApi.getInstance().getUserByUserId(userId);
        if (user != null) {
            WritableMap map = Arguments.createMap();
            map.putString("userId",user.getUserId());
            map.putString("userName",user.getUserName());
            map.putString("userInfo",user.getUserInfo());
            map.putString("groupId",user.getGroupId());
            map.putString("ctime", String.valueOf(user.getCtime()));
            map.putString("updateTime", String.valueOf(user.getUpdateTime()));
            File facePicDir = FileUtils.getBatchImportSuccessDirectory();
            if (facePicDir != null) {
                File facePicPath = new File(facePicDir, user.getImageName());
                map.putString("avatarPath", facePicPath.getAbsolutePath());
            }
            promise.resolve(map);
        } else {
            promise.reject("1001","数据不存在");
        }
    }

    @ReactMethod
    public void getAllUserList(Promise promise) {
        WritableArray datas = Arguments.createArray();
        List<User> list = FaceApi.getInstance().getAllUserList();
        for (int i = 0; i < list.size(); i++) {
            User user = list.get(i);
            WritableMap map = Arguments.createMap();
            map.putString("userId", user.getUserId());
            map.putString("userName", user.getUserName());
            map.putString("userInfo", user.getUserInfo());
            map.putString("groupId", user.getGroupId());
            map.putString("ctime", String.valueOf(user.getCtime()));
            map.putString("updateTime", String.valueOf(user.getUpdateTime()));

            File facePicDir = FileUtils.getBatchImportSuccessDirectory();
            if (facePicDir != null) {
                File facePicPath = new File(facePicDir, user.getImageName());
                map.putString("avatarPath", facePicPath.getAbsolutePath());
            }

            datas.pushMap(map);
        }
        promise.resolve(datas);
    }

    @ReactMethod
    public void initDatabases() {
        // 数据变化，更新内存
        FaceApi.getInstance().initDatabases(true);
    }

    /**
     * 网络图片转bitmap
     */
    public Bitmap getBitmap(String url) {
        Bitmap bm = null;
        try {
            URL iconUrl = new URL(url);
            URLConnection conn = iconUrl.openConnection();
            HttpURLConnection http = (HttpURLConnection) conn;

            int length = http.getContentLength();

            conn.connect();
            // 获得图像的字符流
            InputStream is = conn.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is, length);
            bm = BitmapFactory.decodeStream(bis);
            bis.close();
            is.close();// 关闭流
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bm;
    }

    /**
     * 设置配置
     * @param config
     * @param promise
     */
    @ReactMethod
    public void setConfig(ReadableMap config, Promise promise) {
        Log.d("setConfig", config.toString());
        // 设备通信密码
        if (config.hasKey("dPass")) {
            SingleBaseConfig.getBaseConfig().setdPass(config.getString("dPass"));
        }
        // RGB检测帧回显
        if (config.hasKey("display")) {
            SingleBaseConfig.getBaseConfig().setDisplay(config.getBoolean("display"));
        }
        // RGB预览Y轴转向falese为0，true为180
        if (config.hasKey("rgbRevert")) {
            SingleBaseConfig.getBaseConfig().setRgbRevert(config.getBoolean("rgbRevert"));
        }
        // NIR或depth实时视频预览

        if (config.hasKey("isNirOrDepth")) {
            SingleBaseConfig.getBaseConfig().setNirOrDepth(config.getBoolean("isNirOrDepth"));
        }
        // 默认为false。可选项为"true"、"false"，是否开启调试显示，将会作用到所有视频流识别页面，包含1：N、1：1采集人脸图片环节。
        if (config.hasKey("debug")) {
            SingleBaseConfig.getBaseConfig().setDebug(config.getBoolean("debug"));
        }
        // 默认为0。可传入0、90、180、270四个选项。
        if (config.hasKey("videoDirection")) {
            int val = config.getInt("videoDirection");
            if (val == 0 || val == 90 || val == 180 || val == 270) {
                SingleBaseConfig.getBaseConfig().setVideoDirection(val);
            }
        }
        // 默认为wireframe。可选项为"wireframe"、"fixedarea"，如选择fixed_area，需要传入半径，px像素为单位
        if (config.hasKey("detectFrame")) {
            String val = config.getString("detectFrame");
            if (val.equals("wireframe") || val.equals("fixed_area")) {
                SingleBaseConfig.getBaseConfig().setDetectFrame(val);
            }
        }
        // 当选择fixed_area，需要传入半径信息，以px为单位，如50px
//    private int radius = 50;
        // 默认为0。可传入0、90、180、270四个选项
        if (config.hasKey("detectDirection")) {
            int val = config.getInt("detectDirection");
            if (val == 0 || val == 90 || val == 180 || val == 270) {
                SingleBaseConfig.getBaseConfig().setDetectDirection(val);
            }
        }
        // 默认为max。分为"max" 、"none"三个方式，分别是最大人脸 ，和不检测人脸
        if (config.hasKey("trackType")) {
            String val = config.getString("trackType");
            if (val.equals("max") || val.equals("none")) {
                SingleBaseConfig.getBaseConfig().setTrackType(val);
            }
        }
        // 默认为80px。可传入大于50px的数值，小于此大小的人脸不予检测
        if (config.hasKey("minimumFace")) {
            int val = config.getInt("minimumFace");
            if (val < 50) {
                val = 50;
            }
            SingleBaseConfig.getBaseConfig().setMinimumFace(val);
        }
        // 模糊度设置，默认0.5。取值范围[0~1]，0是最清晰，1是最模糊
        if (config.hasKey("blur")) {
            float val = (float) config.getDouble("blur");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setBlur(val);
        }
        // 光照设置，默认40.取值范围[0~255], 数值越大，光线越强
        if (config.hasKey("illumination")) {
            int val = config.getInt("illumination");
            if (val < 0) {
                val = 0;
            } else if (val > 255) {
                val = 255;
            }
            SingleBaseConfig.getBaseConfig().setIllumination(val);
        }
        // 姿态阈值
        if (config.hasKey("gesture")) {
            float val = (float) config.getDouble("gesture");
            SingleBaseConfig.getBaseConfig().setGesture(val);
        }
        // 三维旋转之俯仰角度[-90(上), 90(下)]，默认20
        if (config.hasKey("pitch")) {
            float val = (float) config.getDouble("pitch");
            if (val < -90) {
                val = -90;
            } else if (val > 90) {
                val = 90;
            }
            SingleBaseConfig.getBaseConfig().setPitch(val);
        }
        // 平面内旋转角[-180(逆时针), 180(顺时针)]，默认20
        if (config.hasKey("roll")) {
            float val = (float) config.getDouble("roll");
            if (val < -180) {
                val = -180;
            } else if (val > 180) {
                val = 180;
            }
            SingleBaseConfig.getBaseConfig().setRoll(val);
        }
        // 三维旋转之左右旋转角[-90(左), 90(右)]，默认20
        if (config.hasKey("yaw")) {
            float val = (float) config.getDouble("yaw");
            if (val < -90) {
                val = -90;
            } else if (val > 90) {
                val = 90;
            }
            SingleBaseConfig.getBaseConfig().setYaw(val);
        }
        // 遮挡阈值
        if (config.hasKey("occlusion")) {
            float val = (float) config.getDouble("occlusion");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setOcclusion(val);
        }
        // 左眼被遮挡的阈值，默认0.6
        if (config.hasKey("leftEye")) {
            float val = (float) config.getDouble("leftEye");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setLeftEye(val);
        }
        // 右眼被遮挡的阈值，默认0.6
        if (config.hasKey("rightEye")) {
            float val = (float) config.getDouble("rightEye");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setRightEye(val);
        }
        // 鼻子被遮挡的阈值，默认0.7
        if (config.hasKey("nose")) {
            float val = (float) config.getDouble("nose");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setNose(val);
        }
        // 嘴巴被遮挡的阈值，默认0.7
        if (config.hasKey("mouth")) {
            float val = (float) config.getDouble("mouth");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setMouth(val);
        }
        // 左脸颊被遮挡的阈值，默认0.8
        if (config.hasKey("leftCheek")) {
            float val = (float) config.getDouble("leftCheek");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setLeftCheek(val);
        }
        // 右脸颊被遮挡的阈值，默认0.8
        if (config.hasKey("rightCheek")) {
            float val = (float) config.getDouble("rightCheek");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setRightCheek(val);
        }
        // 下巴被遮挡阈值，默认为0.6
        if (config.hasKey("chinContour")) {
            float val = (float) config.getDouble("chinContour");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setChinContour(val);
        }
        // 人脸完整度，默认为1。0为人脸溢出图像边界，1为人脸都在图像边界内
        if (config.hasKey("completeness")) {
            float val = (float) config.getDouble("completeness");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setCompleteness(val);
        }
        // 识别阈值，0-100，默认为80分,需要选择具体模型的阈值。live：80、idcard：80
        if (config.hasKey("liveThreshold")) {
            int val = config.getInt("liveThreshold");
            if (val < 0) {
                val = 0;
            } else if (val > 100) {
                val = 100;
            }
            SingleBaseConfig.getBaseConfig().setLiveThreshold(val);
        }
        // 识别阈值，0-100，默认为80分,需要选择具体模型的阈值。live：80、idcard：80
        if (config.hasKey("idThreshold")) {
            int val = config.getInt("idThreshold");
            if (val < 0) {
                val = 0;
            } else if (val > 100) {
                val = 100;
            }
            SingleBaseConfig.getBaseConfig().setIdThreshold(val);
        }
        // 使用的特征抽取模型默认为生活照：1；证件照：2
        if (config.hasKey("activeModel")) {
            int val = config.getInt("activeModel");
            if (val == 1 || val == 2) {
                SingleBaseConfig.getBaseConfig().setActiveModel(val);
            }
        }
        // 识别结果出来后的演示展示，默认为0ms
        if (config.hasKey("timeLapse")) {
            int val = config.getInt("timeLapse");
            if (val < 0) {
                val = 0;
            }
            SingleBaseConfig.getBaseConfig().setTimeLapse(val);
        }
        // 活体选择模式，默认为不使用活体，"1"
        // 不使用活体：1
        // RGB活体：2
        // RGB+NIR活体：3
        // RGB+Depth活体：4
        if (config.hasKey("type")) {
            int val = config.getInt("type");
            if (val == 1 || val == 2 || val == 3 || val == 4) {
                SingleBaseConfig.getBaseConfig().setType(val);
            }
        }
        // 是否开启质量检测开关
        if (config.hasKey("qualityControl")) {
            boolean val = config.getBoolean("qualityControl");
            SingleBaseConfig.getBaseConfig().setQualityControl(val);
        }
        // RGB活体阀值
        if (config.hasKey("rgbLiveScore")) {
            float val = (float) config.getDouble("rgbLiveScore");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setRgbLiveScore(val);
        }
        // NIR活体阀值
        if (config.hasKey("nirLiveScore")) {
            float val = (float) config.getDouble("nirLiveScore");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setNirLiveScore(val);
        }
        // Depth活体阀值
        if (config.hasKey("depthLiveScore")) {
            float val = (float) config.getDouble("depthLiveScore");
            if (val < 0) {
                val = 0;
            } else if (val > 1) {
                val = 1;
            }
            SingleBaseConfig.getBaseConfig().setDepthLiveScore(val);
        }
        // 0:奥比中光Astra Mini、Mini S系列(结构光)
        // 1:奥比中光 Astra Pro 、Pro S 、蝴蝶（结构光）
        // 2:奥比中光Atlas（结构光）
        // 3:奥比中光大白、海燕(结构光)
        // 4:奥比中光Deeyea(结构光)
        // 5:华捷艾米A100S、A200(结构光)
        // 6:Pico DCAM710(ToF)
        if (config.hasKey("cameraType")) {
            int val = config.getInt("cameraType");
            if (val == 0 || val == 1 || val == 2 || val == 3 || val == 4 || val == 5 || val == 6) {
                SingleBaseConfig.getBaseConfig().setCameraType(val);
            }
        }

        // 0：RGB无镜像，1：有镜像
        if (config.hasKey("mirrorRGB")) {
            int val = config.getInt("mirrorRGB");
            if (val == 0 || val == 1) {
                SingleBaseConfig.getBaseConfig().setMirrorRGB(val);
            }
        }
        // 0：NIR无镜像，1：有镜像
        if (config.hasKey("mirrorNIR")) {
            int val = config.getInt("mirrorNIR");
            if (val == 0 || val == 1) {
                SingleBaseConfig.getBaseConfig().setMirrorNIR(val);
            }
        }

        // 是否开启属性检测
        if (config.hasKey("attribute")) {
            boolean val = config.getBoolean("attribute");
            SingleBaseConfig.getBaseConfig().setAttribute(val);
        }

        // rgb和nir摄像头宽
        if (config.hasKey("rgbAndNirWidth")) {
            int val = config.getInt("rgbAndNirWidth");
            if (val < 0) {
                val = 0;
            }
            SingleBaseConfig.getBaseConfig().setRgbAndNirWidth(val);
        }
        // rgb和nir摄像头高
        if (config.hasKey("rgbAndNirHeight")) {
            int val = config.getInt("rgbAndNirHeight");
            if (val < 0) {
                val = 0;
            }
            SingleBaseConfig.getBaseConfig().setRgbAndNirHeight(val);
        }
        // depth摄像头宽
        if (config.hasKey("depthWidth")) {
            int val = config.getInt("depthWidth");
            if (val < 0) {
                val = 0;
            }
            SingleBaseConfig.getBaseConfig().setDepthWidth(val);
        }
        // depth摄像头高
        if (config.hasKey("depthHeight")) {
            int val = config.getInt("depthHeight");
            if (val < 0) {
                val = 0;
            }
            SingleBaseConfig.getBaseConfig().setDepthHeight(val);
        }
        // rgbCameraIndex
        if (config.hasKey("rgbCameraIndex")) {
            int val = config.getInt("rgbCameraIndex");
            if (val < 0) {
                val = 0;
            }
            if (val > 3) {
                val = 3;
            }
            Main.getInstance().rgbCameraIndex = val;
        }
        // nirCameraIndex
        if (config.hasKey("nirCameraIndex")) {
            int val = config.getInt("nirCameraIndex");
            if (val < 0) {
                val = 0;
            }
            if (val > 3) {
                val = 3;
            }
            Main.getInstance().nirCameraIndex = val;
        }
        Log.d("setConfig", "finsh=========================");
        promise.resolve(null);
    }

    /**
     * 获取配置
     * @param promise
     */
    @ReactMethod
    public void getConfig(Promise promise) {
        WritableMap config = Arguments.createMap();
        // 设备通信密码
        config.putString("dPass", SingleBaseConfig.getBaseConfig().getdPass());
        // RGB检测帧回显
        config.putBoolean("display", SingleBaseConfig.getBaseConfig().getDisplay());
        // RGB预览Y轴转向falese为0，true为180
        config.putBoolean("rgbRevert", SingleBaseConfig.getBaseConfig().getRgbRevert());
        // NIR或depth实时视频预览
        config.putBoolean("isNirOrDepth", SingleBaseConfig.getBaseConfig().getNirOrDepth());
        // 默认为false。可选项为"true"、"false"，是否开启调试显示，将会作用到所有视频流识别页面，包含1：N、1：1采集人脸图片环节。
        config.putBoolean("debug", SingleBaseConfig.getBaseConfig().isDebug());
        // 默认为0。可传入0、90、180、270四个选项。
        config.putInt("videoDirection", SingleBaseConfig.getBaseConfig().getVideoDirection());
        // 默认为wireframe。可选项为"wireframe"、"fixedarea"，如选择fixed_area，需要传入半径，px像素为单位
        config.putString("detectFrame", SingleBaseConfig.getBaseConfig().getDetectFrame());
        // 默认为0。可传入0、90、180、270四个选项
        config.putInt("detectDirection", SingleBaseConfig.getBaseConfig().getDetectDirection());
        // 默认为max。分为"max" 、"none"三个方式，分别是最大人脸 ，和不检测人脸
        config.putString("trackType", SingleBaseConfig.getBaseConfig().getTrackType());
        // 默认为80px。可传入大于50px的数值，小于此大小的人脸不予检测
        config.putInt("minimumFace", SingleBaseConfig.getBaseConfig().getMinimumFace());
        // 模糊度设置，默认0.5。取值范围[0~1]，0是最清晰，1是最模糊
        config.putDouble("blur", SingleBaseConfig.getBaseConfig().getBlur());
        // 光照设置，默认40.取值范围[0~255], 数值越大，光线越强
        config.putInt("illumination", SingleBaseConfig.getBaseConfig().getIllumination());
        // 姿态阈值
        config.putDouble("gesture", SingleBaseConfig.getBaseConfig().getGesture());
        // 三维旋转之俯仰角度[-90(上), 90(下)]，默认20
        config.putDouble("pitch", SingleBaseConfig.getBaseConfig().getPitch());
        // 平面内旋转角[-180(逆时针), 180(顺时针)]，默认20
        config.putDouble("roll", SingleBaseConfig.getBaseConfig().getRoll());
        // 三维旋转之左右旋转角[-90(左), 90(右)]，默认20
        config.putDouble("yaw", SingleBaseConfig.getBaseConfig().getYaw());
        // 遮挡阈值
        config.putDouble("occlusion", SingleBaseConfig.getBaseConfig().getOcclusion());
        // 左眼被遮挡的阈值，默认0.6
        config.putDouble("leftEye", SingleBaseConfig.getBaseConfig().getLeftEye());
        // 右眼被遮挡的阈值，默认0.6
        config.putDouble("rightEye", SingleBaseConfig.getBaseConfig().getRightEye());
        // 鼻子被遮挡的阈值，默认0.7
        config.putDouble("nose", SingleBaseConfig.getBaseConfig().getNose());
        // 嘴巴被遮挡的阈值，默认0.7
        config.putDouble("mouth", SingleBaseConfig.getBaseConfig().getMouth());
        // 左脸颊被遮挡的阈值，默认0.8
        config.putDouble("leftCheek", SingleBaseConfig.getBaseConfig().getLeftCheek());
        // 右脸颊被遮挡的阈值，默认0.8
        config.putDouble("rightCheek", SingleBaseConfig.getBaseConfig().getRightCheek());
        // 下巴被遮挡阈值，默认为0.6
        config.putDouble("chinContour", SingleBaseConfig.getBaseConfig().getChinContour());
        // 人脸完整度，默认为1。0为人脸溢出图像边界，1为人脸都在图像边界内
        config.putDouble("completeness", SingleBaseConfig.getBaseConfig().getCompleteness());
        // 识别阈值，0-100，默认为80分,需要选择具体模型的阈值。live：80、idcard：80
        config.putInt("liveThreshold", SingleBaseConfig.getBaseConfig().getLiveThreshold());
        // 识别阈值，0-100，默认为80分,需要选择具体模型的阈值。live：80、idcard：80
        config.putInt("idThreshold", SingleBaseConfig.getBaseConfig().getIdThreshold());
        // 使用的特征抽取模型默认为生活照：1；证件照：2
        config.putInt("activeModel", SingleBaseConfig.getBaseConfig().getActiveModel());
        // 识别结果出来后的演示展示，默认为0ms
        config.putInt("timeLapse", SingleBaseConfig.getBaseConfig().getTimeLapse());
        // 活体选择模式，默认为不使用活体，"1"
        // 不使用活体：1
        // RGB活体：2
        // RGB+NIR活体：3
        // RGB+Depth活体：4
        config.putInt("type", SingleBaseConfig.getBaseConfig().getType());
        // 是否开启质量检测开关
        config.putBoolean("qualityControl", SingleBaseConfig.getBaseConfig().isQualityControl());
        // RGB活体阀值
        config.putDouble("rgbLiveScore", SingleBaseConfig.getBaseConfig().getRgbLiveScore());
        // NIR活体阀值
        config.putDouble("nirLiveScore", SingleBaseConfig.getBaseConfig().getNirLiveScore());
        // Depth活体阀值
        config.putDouble("depthLiveScore", SingleBaseConfig.getBaseConfig().getDepthLiveScore());

        // 0:奥比中光Astra Mini、Mini S系列(结构光)
        // 1:奥比中光 Astra Pro 、Pro S 、蝴蝶（结构光）
        // 2:奥比中光Atlas（结构光）
        // 3:奥比中光大白、海燕(结构光)
        // 4:奥比中光Deeyea(结构光)
        // 5:华捷艾米A100S、A200(结构光)
        // 6:Pico DCAM710(ToF)
        config.putInt("cameraType", SingleBaseConfig.getBaseConfig().getCameraType());
        // 0：RGB无镜像，1：有镜像
        config.putInt("mirrorRGB", SingleBaseConfig.getBaseConfig().getMirrorRGB());
        // 0：NIR无镜像，1：有镜像
        config.putInt("mirrorNIR", SingleBaseConfig.getBaseConfig().getMirrorNIR());
        // 是否开启属性检测
        config.putBoolean("attribute", SingleBaseConfig.getBaseConfig().isAttribute());
        // rgb和nir摄像头宽
        config.putInt("rgbAndNirWidth", SingleBaseConfig.getBaseConfig().getRgbAndNirWidth());
        // rgb和nir摄像头高
        config.putInt("rgbAndNirHeight", SingleBaseConfig.getBaseConfig().getRgbAndNirHeight());
        // depth摄像头宽
        config.putInt("depthWidth", SingleBaseConfig.getBaseConfig().getDepthWidth());
        // depth摄像头高
        config.putInt("depthHeight", SingleBaseConfig.getBaseConfig().getDepthHeight());

        // rgbCameraIndex
        config.putInt("rgbCameraIndex", Main.getInstance().rgbCameraIndex);
        // nirCameraIndex
        config.putInt("nirCameraIndex", Main.getInstance().nirCameraIndex);

        promise.resolve(config);
    }

    /**
     * 关闭摄像头人脸检测（导入用户的时候用，因为摄像头人脸检索和导入用户有冲突，可能会造成闪退）
     */
    @ReactMethod
    public void stopCameraCheck() {
        Main.getInstance().isCameraCheck = false;
    }

    /**
     * 开启摄像头人脸检测（导入用户的时候用，因为摄像头人脸检索和导入用户有冲突，可能会造成闪退）
     */
    @ReactMethod
    public void startCameraCheck() {
        Main.getInstance().isCameraCheck = true;
    }
}
