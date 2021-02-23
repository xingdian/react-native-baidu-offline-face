package com.tracy.baidu.face;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.usb.UsbDevice;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.baidu.idl.face.main.callback.CameraDataCallback;
import com.baidu.idl.face.main.callback.FaceDetectCallBack;
import com.baidu.idl.face.main.camera.AutoTexturePreviewView;
import com.baidu.idl.face.main.camera.NirCameraPreviewManager;
import com.baidu.idl.face.main.camera.RgbCameraPreviewManager;
import com.baidu.idl.face.main.manager.FaceSDKManager;
import com.baidu.idl.face.main.model.LivenessModel;
import com.baidu.idl.face.main.model.SingleBaseConfig;
import com.baidu.idl.face.main.model.User;
import com.baidu.idl.face.main.utils.BitmapUtils;
import com.baidu.idl.face.main.utils.FaceOnDrawTexturViewUtil;
import com.baidu.idl.main.facesdk.FaceInfo;
import com.baidu.idl.main.facesdk.model.BDFaceImageInstance;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.ThemedReactContext;

import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.ImageRegistrationMode;
import org.openni.OpenNI;
import org.openni.PixelFormat;
import org.openni.SensorType;
import org.openni.VideoFrameRef;
import org.openni.VideoMode;
import org.openni.VideoStream;
import org.openni.android.OpenNIHelper;
import org.openni.android.OpenNIView;
import org.openni.OpenNI;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class RNBaiduFaceCameraView extends FrameLayout {
    // 图片越大，性能消耗越大，也可以选择640*480， 1280*720
    private static final int PREFER_WIDTH = SingleBaseConfig.getBaseConfig().getRgbAndNirWidth();
    private static final int PREFER_HEIGHT = SingleBaseConfig.getBaseConfig().getRgbAndNirHeight();

    private ThemedReactContext mcontext;
    private TextureView mDrawDetectFaceView;
    private AutoTexturePreviewView mAutoCameraPreviewView;
    private AutoTexturePreviewView nirPreviewView;

    private RectF rectF;
    private Paint paint;
    private User mUser;
    private Paint paintBg;
    private boolean isTime = true;
    private long startTime;
    private boolean detectCount;
    private int mLiveType;
    private float mRgbLiveScore;

    // 摄像头采集数据
    private volatile byte[] rgbData;
    private volatile byte[] nirData;
    private Size rgbSize;
    private Size nirSize;
    private Size depthSize;
    private float rgbLiveScore;
    private float nirLiveScore;

    //===================DEPTH============================
    private float depthLiveScore;
    // 显示Depth图
    private OpenNIView depthGLView;
    // 设备初始化状态标记
    private boolean initOk = false;
    // 摄像头驱动
    private Device mDevice;
    private Thread thread;
    private OpenNIHelper mOpenNIHelper;
    private VideoStream mDepthStream;
    private Object sync = new Object();
    // 循环取深度图像数据
    private boolean exit = false;
    // 当前摄像头类型
    private static int cameraType;
    // 摄像头采集数据
    private volatile byte[] depthData;

    // Depth摄像头图像宽和高
    private static final int DEPTH_WIDTH = SingleBaseConfig.getBaseConfig().getDepthWidth();
    private static final int DEPTH_HEIGHT = SingleBaseConfig.getBaseConfig().getDepthHeight();

    //===================DEPTH============================
    public RNBaiduFaceCameraView(ThemedReactContext context) {
        super(context);
        mcontext = context;
        initView();
    }

    public RNBaiduFaceCameraView(ThemedReactContext context, AttributeSet attrs) {
        super(context, attrs);
        mcontext = context;
        initView();
    }

    public RNBaiduFaceCameraView(ThemedReactContext context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mcontext = context;
        initView();
    }


    public void initView() {
        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        // 图像预览
        mAutoCameraPreviewView = new AutoTexturePreviewView(mcontext);
        mAutoCameraPreviewView.setLayoutParams(layoutParams);
        addView(mAutoCameraPreviewView);

        // NIR
        nirPreviewView = new AutoTexturePreviewView(mcontext);
        nirPreviewView.setLayoutParams(layoutParams);
        nirPreviewView.setAlpha(0);
        addView(nirPreviewView);

//        depthGLView = new OpenNIView(mcontext);
//        depthGLView.setLayoutParams(layoutParams);
//        depthGLView.setAlpha(0);
//        addView(depthGLView);


        // 画人脸框
        rectF = new RectF();
        paint = new Paint();
        paintBg = new Paint();
        mDrawDetectFaceView = new TextureView(mcontext);
        mDrawDetectFaceView.setLayoutParams(layoutParams);
        mDrawDetectFaceView.setOpaque(false);
        mDrawDetectFaceView.setKeepScreenOn(true);
        addView(mDrawDetectFaceView);

        // 活体状态
        mLiveType = SingleBaseConfig.getBaseConfig().getType();
        // 活体阈值
        mRgbLiveScore = SingleBaseConfig.getBaseConfig().getRgbLiveScore();
        // depth 阈值
        depthLiveScore = SingleBaseConfig.getBaseConfig().getDepthLiveScore();

        if (mLiveType == 1 || mLiveType == 2) {
            startRGBRegisterFunction();
        } else if (mLiveType == 3) { // RGB+NIR活体
            // RGB 阈值
            rgbLiveScore = SingleBaseConfig.getBaseConfig().getRgbLiveScore();
            // Live 阈值
            nirLiveScore = SingleBaseConfig.getBaseConfig().getNirLiveScore();

            int nirCameraIndex = Main.getInstance().nirCameraIndex;

            // 双摄像头
            int mCameraNum = Camera.getNumberOfCameras();
            if (mCameraNum < 2) {
                TextView textview = new TextView(mcontext);
                textview.setText("未检测到2个摄像头");
                textview.setTextColor(Color.parseColor("#333333"));
                textview.setTextSize(40);
                textview.setGravity(Gravity.CENTER);
                textview.setLayoutParams(layoutParams);
                addView(textview);
                return;
            } else if (nirCameraIndex >= mCameraNum) {
                TextView textview = new TextView(mcontext);
                textview.setText("NIR摄像头" + nirCameraIndex + "不存在");
                textview.setTextColor(Color.parseColor("#333333"));
                textview.setTextSize(40);
                textview.setGravity(Gravity.CENTER);
                textview.setLayoutParams(layoutParams);
                addView(textview);
                return;
            } else {
                if (SingleBaseConfig.getBaseConfig().getNirOrDepth()) {
                    nirPreviewView.setAlpha(1);
                }
                startNirRegisterFunction();
            }

        } else if (mLiveType == 4) { // RGB+Depth活体
            // 初始化 深度摄像头
            mOpenNIHelper = new OpenNIHelper(Main.getInstance().mactivity);
            mOpenNIHelper.requestDeviceOpen(new OpenNIHelper.DeviceOpenListener() {
                @Override
                public void onDeviceOpened(UsbDevice usbDevice) {
                    initUsbDevice(usbDevice);
                    mDepthStream = VideoStream.create(mDevice, SensorType.DEPTH);
                    if (mDepthStream != null) {
                        List<VideoMode> mVideoModes = mDepthStream.getSensorInfo().getSupportedVideoModes();
                        for (VideoMode mode : mVideoModes) {
                            int x = mode.getResolutionX();
                            int y = mode.getResolutionY();
                            int fps = mode.getFps();
                            if (cameraType == 2) {
                                if (x == DEPTH_HEIGHT && y == DEPTH_WIDTH && mode.getPixelFormat() == PixelFormat.DEPTH_1_MM) {
                                    mDepthStream.setVideoMode(mode);
                                    mDevice.setImageRegistrationMode(ImageRegistrationMode.DEPTH_TO_COLOR);
                                    break;
                                }
                            } else {
                                if (x == DEPTH_WIDTH && y == DEPTH_HEIGHT && mode.getPixelFormat() == PixelFormat.DEPTH_1_MM) {
                                    mDepthStream.setVideoMode(mode);
                                    mDevice.setImageRegistrationMode(ImageRegistrationMode.DEPTH_TO_COLOR);
                                    break;
                                }
                            }

                        }
                        startThread();
                    }
                }

                @Override
                public void onDeviceOpenFailed(String s) {
                    Log.e("RNBaiduFaceCameraView", s);
                    LayoutParams layoutParams = new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT);
                    TextView textview = new TextView(mcontext);
                    textview.setText("打开设备失败：" + s);
                    textview.setTextColor(Color.parseColor("#333333"));
                    textview.setTextSize(40);
                    textview.setGravity(Gravity.CENTER);
                    textview.setLayoutParams(layoutParams);
                    addView(textview);
                }

                @Override
                public void onDeviceNotFound() {
                    Log.e("RNBaiduFaceCameraView", "onDeviceNotFound");
                    LayoutParams layoutParams = new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT);
                    TextView textview = new TextView(mcontext);
                    textview.setText("未找到设备");
                    textview.setTextColor(Color.parseColor("#333333"));
                    textview.setTextSize(40);
                    textview.setGravity(Gravity.CENTER);
                    textview.setLayoutParams(layoutParams);
                    addView(textview);
                }
            });

//            if (Main.getInstance().isDebug) {
//                depthGLView.setAlpha(1);
//            }

            startDepthRegisterFunction();
        }


    }

    //================================depth========================================

    /**
     * 开启线程接收深度数据
     */
    private void startThread() {
        initOk = true;
        thread = new Thread() {

            @Override
            public void run() {

                List<VideoStream> streams = new ArrayList<VideoStream>();

                streams.add(mDepthStream);
                mDepthStream.start();
                while (!exit) {

                    try {
                        OpenNI.waitForAnyStream(streams, 2000);

                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        continue;
                    }

                    synchronized (sync) {
                        if (mDepthStream != null) {
//                            depthGLView.update(mDepthStream);
                            VideoFrameRef videoFrameRef = mDepthStream.readFrame();
                            ByteBuffer depthByteBuf = videoFrameRef.getData();
                            if (depthByteBuf != null) {
                                int depthLen = depthByteBuf.remaining();
                                byte[] depthByte = new byte[depthLen];
                                depthByteBuf.get(depthByte);
                                depthData = depthByte;
                                depthCheckData();
                            }
                            videoFrameRef.release();
                        }
                    }

                }
            }
        };

        thread.start();
    }

    /**
     * 在device 启动时候初始化USB 驱动
     *
     * @param device
     */
    private void initUsbDevice(UsbDevice device) {
        List<DeviceInfo> opennilist = OpenNI.enumerateDevices();
        if (opennilist.size() <= 0) {
            LayoutParams layoutParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            TextView textview = new TextView(mcontext);
            textview.setText("设备不存在");
            textview.setTextColor(Color.parseColor("#333333"));
            textview.setTextSize(40);
            textview.setGravity(Gravity.CENTER);
            textview.setLayoutParams(layoutParams);
            addView(textview);
            return;
        }
        this.mDevice = null;
        // Find mDevice ID
        for (int i = 0; i < opennilist.size(); i++) {
            if (opennilist.get(i).getUsbProductId() == device.getProductId()) {
                this.mDevice = Device.open();
                break;
            }
        }

        if (this.mDevice == null) {
            LayoutParams layoutParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            TextView textview = new TextView(mcontext);
            textview.setText("打开设备失败: " + device.getDeviceName());
            textview.setTextColor(Color.parseColor("#333333"));
            textview.setTextSize(40);
            textview.setGravity(Gravity.CENTER);
            textview.setLayoutParams(layoutParams);
            addView(textview);
            return;
        }
    }

    // RGB+DEPTH
    private void startDepthRegisterFunction() {
        RgbCameraPreviewManager.getInstance().startPreview(mcontext, mAutoCameraPreviewView,
                PREFER_WIDTH, PREFER_HEIGHT, new CameraDataCallback() {
                    @Override
                    public void onGetCameraData(byte[] data, Camera camera, int width, int height) {
                        // 摄像头预览数据进行人脸检测
                        rgbSize = new Size(width, height);
                        rgbData = data;
                        depthCheckData();
                    }
                });
    }

    private void depthCheckData() {
        if (rgbData != null && depthData != null) {
            if (!Main.getInstance().isCameraCheck) {
                return;
            }
            FaceSDKManager.getInstance().onDetectCheck(rgbData, null, depthData,
                    rgbSize, nirSize, depthSize, 3, new FaceDetectCallBack() {
                        @Override
                        public void onFaceDetectCallback(LivenessModel livenessModel) {
                            faceDetectResult(livenessModel);

                        }

                        @Override
                        public void onTip(int code, String msg) {
                        }

                        @Override
                        public void onFaceDetectDarwCallback(LivenessModel livenessModel) {
                            // 绘制人脸框
                            showFrame(livenessModel);
                        }
                    });
            rgbData = null;
            depthData = null;
        }
    }
    //================================depth========================================

    //================================RGB+NIR========================================
    // RGB+NIR
    private void startNirRegisterFunction() {
        RgbCameraPreviewManager.getInstance().startPreview(mcontext, mAutoCameraPreviewView,
                PREFER_WIDTH, PREFER_HEIGHT, new CameraDataCallback() {
                    @Override
                    public void onGetCameraData(byte[] data, Camera camera, int width, int height) {
                        // 摄像头预览数据进行人脸检测
                        rgbSize = new Size(width, height);
                        rgbData = data;
                        nirCheckData();
                    }
                });
        NirCameraPreviewManager.getInstance().startPreview(mcontext, nirPreviewView,
                PREFER_WIDTH, PREFER_HEIGHT, new CameraDataCallback() {
                    @Override
                    public void onGetCameraData(byte[] data, Camera camera, int width, int height) {
                        // 摄像头预览数据进行人脸检测
                        nirSize = new Size(width, height);
                        nirData = data;
                    }
                });
    }

    private void nirCheckData() {
        if (rgbData != null && nirData != null) {
            if (!Main.getInstance().isCameraCheck) {
                return;
            }

            FaceSDKManager.getInstance().onDetectCheck(rgbData, nirData, null,
                    rgbSize, nirSize, depthSize, mLiveType, new FaceDetectCallBack() {
                        @Override
                        public void onFaceDetectCallback(LivenessModel livenessModel) {
                            faceDetectResult(livenessModel);

                        }

                        @Override
                        public void onTip(int code, String msg) {
                        }

                        @Override
                        public void onFaceDetectDarwCallback(LivenessModel livenessModel) {
                            // 绘制人脸框
                            showFrame(livenessModel);
                        }
                    });
            rgbData = null;
            nirData = null;
        }
    }
    //================================RGB+NIR========================================

    //================================RGB========================================
    private void startRGBRegisterFunction() {
//        RgbCameraPreviewManager.getInstance().setCameraFacing(Main.getInstance().camera);
        RgbCameraPreviewManager.getInstance().startPreview(mcontext, mAutoCameraPreviewView,
                PREFER_WIDTH, PREFER_HEIGHT, new CameraDataCallback() {
                    @Override
                    public void onGetCameraData(byte[] data, Camera camera, int width, int height) {
                        if (!Main.getInstance().isCameraCheck) {
                            return;
                        }
                        rgbSize = new Size(width, height);
                        // 摄像头预览数据进行人脸检测
                        FaceSDKManager.getInstance().onDetectCheck(data, null, null,
                                rgbSize, nirSize, depthSize, mLiveType, new FaceDetectCallBack() {
                                    @Override
                                    public void onFaceDetectCallback(LivenessModel livenessModel) {
                                        faceDetectResult(livenessModel);
                                    }

                                    @Override
                                    public void onTip(int code, String msg) {
                                    }

                                    @Override
                                    public void onFaceDetectDarwCallback(LivenessModel livenessModel) {
                                        // 绘制人脸框
                                        showFrame(livenessModel);
                                    }
                                });
                    }
                });
    }

    // ***************结果输出*************
    private void faceDetectResult(final LivenessModel livenessModel) {
        if (livenessModel == null) {
            WritableMap map = Arguments.createMap();
            map.putString("check_timestamp", "0"); // 检测耗时
            map.putString("rgb_timestamp", "0"); // RGB活体检测耗时
            map.putString("rgb_live_score", "0"); // RGB活体得分
            map.putString("feature_timestamp", "0"); // 特征抽取耗时
            map.putString("contrast_timestamp", "0");  // 特征比对耗时
            map.putString("all_time", "0"); // 总耗时
            sendEvent("FaceCameraCheckFail", map);
            return;
        }


        User user = null;
        if (mLiveType == 1) { // 不是活体检测
            user = livenessModel.getUser();
        } else {
            float rgbLivenessScore = livenessModel.getRgbLivenessScore();
            if (rgbLivenessScore < mRgbLiveScore) {
                // 未检测到rgb活体

            } else {
                user = livenessModel.getUser();
            }
        }

        WritableMap map = Arguments.createMap();
        map.putString("rgb_detect_duration", livenessModel.getRgbDetectDuration() + ""); // 检测耗时
        map.putString("rgb_liveness_duration", livenessModel.getRgbLivenessDuration() + "");// RGB活体检测耗时
        map.putString("rgb_liveveness_score", livenessModel.getRgbLivenessScore() + ""); // RGB活体得分
        map.putString("ir_liveness_duration", livenessModel.getIrLivenessDuration() + ""); // NIR活体检测耗时
        map.putString("ir_liveness_score", livenessModel.getIrLivenessScore() + ""); // NIR活体得分
        map.putString("depth_liveness_duration", livenessModel.getDepthtLivenessDuration() + ""); // depth活体检测耗时
        map.putString("depth_liveness_score", livenessModel.getDepthLivenessScore() + ""); // depth活体得分
        map.putString("feature_duration", livenessModel.getFeatureDuration() + ""); // 特征抽取耗时
        map.putString("check_duration", livenessModel.getCheckDuration() + ""); // 特征比对耗时
        map.putString("all_detect_duration", livenessModel.getAllDetectDuration() + ""); // 总耗时
        map.putString("feature", Base64.encodeToString(livenessModel.getFeature(), Base64.DEFAULT) + ""); // 特征信息Base64

        // 识别图
        String base64Image = "";
        BDFaceImageInstance image = livenessModel.getBdFaceImageInstance();
        if (image != null) {
            Bitmap bitmap = BitmapUtils.getInstaceBmp(image);

            // bitmap转base64图片
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20, bos);
            byte[] imageByte = bos.toByteArray();
            base64Image = Base64.encodeToString(imageByte, Base64.DEFAULT);
            image.destory();
            bitmap.recycle();
        }
        map.putString("bd_face_image_base64", base64Image);

        if (user != null) {
            map.putString("userId", user.getUserId());
            map.putString("userName", user.getUserName());
            map.putString("userInfo", user.getUserInfo());
        }
        sendEvent("FaceCameraCheck", map);
    }
    //================================RGB========================================

    /**
     * 绘制人脸框
     */
    private void showFrame(final LivenessModel model) {
        Canvas canvas = mDrawDetectFaceView.lockCanvas();
        if (canvas == null) {
            mDrawDetectFaceView.unlockCanvasAndPost(canvas);
            return;
        }
        if (model == null) {
            // 清空canvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            mDrawDetectFaceView.unlockCanvasAndPost(canvas);
            return;
        }
        FaceInfo[] faceInfos = model.getTrackFaceInfo();
        if (faceInfos == null || faceInfos.length == 0) {
            // 清空canvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            mDrawDetectFaceView.unlockCanvasAndPost(canvas);
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        FaceInfo faceInfo = faceInfos[0];

        rectF.set(FaceOnDrawTexturViewUtil.getFaceRectTwo(faceInfo));

        // 检测图片的坐标和显示的坐标不一样，需要转换。
        FaceOnDrawTexturViewUtil.mapFromOriginalRect(rectF,
                mAutoCameraPreviewView, model.getBdFaceImageInstance());
        // 人脸框颜色
        FaceOnDrawTexturViewUtil.drawFaceColor(mUser, paint, paintBg, model);
        // 绘制人脸框
        FaceOnDrawTexturViewUtil.drawCircle(canvas, mAutoCameraPreviewView,
                rectF, paint, paintBg, faceInfo);
        // 清空canvas
        mDrawDetectFaceView.unlockCanvasAndPost(canvas);

    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        reLayout();
    }

    public void reLayout() {
        if (getWidth() > 0 && getHeight() > 0) {
            int w = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY);
            int h = MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY);
            measure(w, h);
            layout(getPaddingLeft() + getLeft(), getPaddingTop() + getTop(), getWidth() + getPaddingLeft() + getLeft(), getHeight() + getPaddingTop() + getTop());

        }
    }

    public void sendEvent(String eventName, WritableMap params) {
        mcontext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
