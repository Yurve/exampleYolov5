package com.sample.javayollo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Supplier;
import io.reactivex.rxjava3.observers.DisposableObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;


public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "opencv";

    //화면에 보여주는 카메라뷰
    private CameraBridgeViewBase mOpenCvCameraView;
    //dnn 모듈 불러오기 클래스
    private Net dnnNet;
    //받아오는 화면
    public Mat frameMat;
    //비동기 클래스
    private CompositeDisposable disposables;


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

    //assets 파일들은 바로 불러올 수 없다. assetManager 를 통해 불러오기
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

    //위 copyFile 메소드를 통해 불러온 파일들을 읽기
    public void readOnnx() {
        copyFile("yolov5s.onnx");
        copyFile("coco.names");

        String onnxFilePath = getFilesDir().getAbsolutePath() + "/yolov5s.onnx";

        // read generated ONNX model into org.opencv.dnn.Net object
        dnnNet = Dnn.readNetFromONNX(onnxFilePath);
        if (dnnNet.empty()) {
            Log.d("ONNX:", "불러오기 실패");
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //권한 확인하기
        permissionCheck();

        //비동기 클래스
        disposables = new CompositeDisposable();

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

        //rxjava(비동기) 통로 비우기
        disposables.clear();

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
        //frame 을 mat 객체로 변환 openCV 쓰려고
        Mat matInput = inputFrame.rgba();
        //이미지 반전
        Core.flip(matInput, matInput, 1);

        //핸드폰 사진은 RGBA 근데 사진 비교하려면 RGB 형태여야 한다. 그래서 변환
        Imgproc.cvtColor(matInput, matInput, Imgproc.COLOR_RGB2BGR);

        //바로 아래 메소드
        detectScheduler(matInput);

        //openCV 에서 비교 하는 모델은 R <-> B가 변환되어있다. 따로 한번더 변환해서 원래 사진 보여주기
        if(frameMat != null){
            Imgproc.cvtColor(frameMat,frameMat,Imgproc.COLOR_RGB2BGR);
        }

        //이 매트릭스가 화면에 보여줌
        return frameMat;
    }

    //검출 비동기 구독 메소드 detect()라는 메소드가 실행되면 이 메소드도 실행
    public void detectScheduler(Mat Input) {
        disposables.add(detect(Input)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableObserver<String>() {
                    @Override
                    public void onNext(@NonNull String string) {
                        Log.d("Yolo v5 :",string);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.e(TAG, "onError()", e);
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete()");
                    }
                })
        );
    }

    public static int IMG_HEIGHT = 640;
    public static int IMG_WEIGHT = 640;

    //검출 메소드
    public Observable<String> detect(Mat Input) {
        return Observable.defer(new Supplier<ObservableSource<? extends String>>() {
            @Override
            public ObservableSource<? extends String> get() throws Throwable {

                // convert input image to float type
                // Create a 4D blob from a frame. 가로*세로 픽셀값 뿐아니라 다른 정보들도 가져옴.
                //size를 줄이고 싶어도, 모델을 처음에 만들때 640,640 으로 만들어놔서 수정 x
                Mat matGray = Dnn.blobFromImage(
                        Input, 1, new Size(IMG_WEIGHT, IMG_HEIGHT), new Scalar(0, 0, 0),
                        true, false, CvType.CV_8U
                );

                // Run a model. 모델 집어넣기
                dnnNet.setInput(matGray);


                //추론된 결과  확률 값 출력, 확률의 최댓값이 인식한 클래스 의미가 담겨있음, 연산 하루종일함 뭐 문제있는 듯.
                //거의 7초? 마다 되는거 보면 코드 잘 못 짰는지, 뭐 잘못 구현한 것 같은데 어떻게 해야 될지 모르겠습니다.
                //예상 이유 1. 모델 크기가 너무 크다 대부분 240*240 모델을 사용함. 근데 yolov5 -> onnx 한 파일들은 하나 같이 640*640으로 함.
                //예상 이유 2. 혹시 CPU 연산이 아니라 GPU 연산인가?? 이건 잘 모르겠습니다.
                //예상 이유 3. 32 비트 연산이라서? <-- 수정 했습니다 "CvType.CV_8U" 이게 8비트 연산 그래도 느립니다
                //예상 이유 4. 동기 처리? <--- 수정 했습니다. 비동기 처리하고 쪼끔? 더 빨라진 기분 별 차이없습니다. 그래도 느립니다.
                Mat matResult = dnnNet.forward();


                //이름 찾기 + 이후에는 매트릭스 객체를 넣어서 확인 과정 추가
                ArrayList<String> nameList = detectName();

                /*
                //추론된 매트릭스 객체 정보 가져오기 /얘는 또 왜안되냐? 이 메소드가 실행이 안되서 일단 비워놓습니다 추후에 찾아보겠습니다...
                //일단 추론까지 되는 거보면 여기 이후는 수정하기 쉬울 것 같습니다.  저 위 추론 메소드가 문제....
                Core.MinMaxLocResult mm = Core.minMaxLoc(matResult);

                //찾은 이미지 이름(숫자)
                double maxValIndex = mm.maxLoc.x;
                String imageName =  nameList.get((int) maxValIndex);
                 */

                // displaying the photo and putting the text on it
                //rect 객체를 받아오면 x랑 y를 rect 의 o.5정도 위로 하면될듯.
                Point pos = new Point(50, 50);
                Scalar color = new Scalar(0, 0, 0);
                Imgproc.putText(Input, "imageName", pos, Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, color, 2);


                frameMat = Input;
                return Observable.just("success");
            }
        });
    }

    //저장되어있는 모델의 이름들 불러오기
    public ArrayList<String> detectName() {
        ArrayList<String> imgLabels = null;
        String cocoPath = getFilesDir().getAbsolutePath() + "/coco.names";
        try {
            Stream<String> lines = Files.lines(Paths.get(cocoPath));
            imgLabels = lines.collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imgLabels;
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