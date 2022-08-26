package com.sample.javayollo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String TAG = "opencv";

    //사진 매트릭스 객체
    private Mat matResult;

    //화면에 보여주는 카메라뷰
    private CameraBridgeViewBase mOpenCvCameraView;

    private Net dnnNet;

    static {

        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }

    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                mOpenCvCameraView.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //권한 확인하기
        permissionCheck();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);


        mOpenCvCameraView = findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(1); // front-camera(1),  back-camera(0)




    }

    //권한 체크
    private void permissionCheck() {

        //객체 생성
        //권한 요청 클래스
        PermissionSupport permissionSupport = new PermissionSupport(this, this);

        permissionSupport.onCheckPermission();
    }

    // permissionCheck 대한 결과 값 받고나서
    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        if (requestCode == PermissionSupport.MULTIPLE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한 확인", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "권한 취소");
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResume :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    public void onDestroy() {
        //화면 보여주기 끊기
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        super.onDestroy();
    }

    // implements CameraBridgeViewBase.CvCameraViewListener2 메소드
    /* camera started : 카메라 프리뷰가 시작되면 호출된다.
       camera viewStopped : 카메라 프리뷰가 어떤 이유로 멈추면 호출된다.
       camera frame : 프레임 전달이 필요한 경우 호출 된다.
    */

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat matInput = inputFrame.rgba();

        if (matResult == null)

            matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());


        Core.flip(matInput, matInput, 1);

        //여길 우예하리오...
       return detect(matInput);

    }

    private void copyFile(String filename) {

        AssetManager assetManager = this.getAssets();
        File outputFile = new File(getFilesDir() + "/" + filename);

        try {
            InputStream inputStream = assetManager.open(filename);
            OutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    public void readOnnx(){
        copyFile("yolov5s.onnx");

        String onnxFilePath = getFilesDir().getAbsolutePath()+ "/yolov5s.onnx";

        // read generated ONNX model into org.opencv.dnn.Net object
        dnnNet = Dnn.readNetFromONNX(onnxFilePath);
    }

    public Mat detect(Mat matInput) {
        dnnNet.setInput(matInput);

        Mat dnnImage = dnnNet.forward();

        dnnImage = Dnn.blobFromImage(dnnImage,1.0,new Size(1080,1920));
        dnnNet.setInput(dnnImage);
      //  return Dnn.blobFromImage(dnnImage,1.0,new Size(1080,1920));


       return dnnImage;
    }



    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }


    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase : cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
                readOnnx();
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        onCameraPermissionGranted();
    }

}