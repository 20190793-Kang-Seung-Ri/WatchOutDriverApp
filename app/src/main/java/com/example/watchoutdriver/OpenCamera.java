package com.example.watchoutdriver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Size;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.Manifest;  // 권한 사용을 위한 import

public class OpenCamera extends AppCompatActivity {
    private PreviewView previewView;
    private int frameCount = 0; // 프레임 카운트 변수 추가
    private SleepAlertService sleepAlertService;
    private TextView drowsinessInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        previewView = findViewById(R.id.viewFinder);
        drowsinessInfo = findViewById(R.id.drowsinessInfo);

        sleepAlertService = new SleepAlertService(this); // 서비스 초기화
        requestCameraPermission();
        checkCameraFormats(this);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();  // 권한이 이미 있는 경우 카메라 시작
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT) // 또는 LENS_FACING_BACK
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::processImage);

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
    }

    private void processImage(ImageProxy imageProxy) {
        try {
            // YUV 데이터를 읽어들입니다.
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer(); // Y plane
            ByteBuffer uBuffer = planes[1].getBuffer(); // U plane
            ByteBuffer vBuffer = planes[2].getBuffer(); // V plane

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] yData = new byte[ySize];
            byte[] uData = new byte[uSize];
            byte[] vData = new byte[vSize];

            yBuffer.get(yData);
            uBuffer.get(uData);
            vBuffer.get(vData);

            // YUV -> RGB 변환
            Bitmap bitmap = YUVToRGBConverter.convert(yData, uData, vData, imageProxy.getWidth(), imageProxy.getHeight());

            if (bitmap != null) {
                // Bitmap을 처리
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);

                // JPG로 압축
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream); // 품질을 85로 조정
                byte[] jpegData = outputStream.toByteArray();

                // 프레임 카운트를 증가시키고 10프레임마다 서버로 전송
                frameCount++;
                if (frameCount % 30 == 0) {
                    sendImageToServer(jpegData);  // 10프레임마다 서버로 전송
                    frameCount = 0;
                }
            }

        } catch (Exception e) {
            Log.e("CameraError", "Error processing image", e);
        } finally {
            imageProxy.close();
        }
    }


    private void sendImageToServer(byte[] imageBytes) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // multipart/form-data 형식으로 전송
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.jpg",
                        RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url("http://192.168.137.1:8000/process_video/") // URL을 올바르게 설정하세요
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e("ImageUploadError", "이미지 전송 실패 이유: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(OpenCamera.this,
                        "이미지 전송 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }


            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // 서버로부터 응답을 받음
                    String responseData = response.body().string(); // 응답 본문을 문자열로 변환
//                    runOnUiThread(() -> Toast.makeText(OpenCamera.this,
//                            "서버로 전송 성공: " + responseData, Toast.LENGTH_SHORT).show()); // 전송 성공 시 메시지

                    // JSON 응답 파싱 (선택 사항)
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String filename = jsonResponse.getString("filename");
                        String message = jsonResponse.getString("message");
                        int sleep_level = jsonResponse.getInt("sleep_state");
                        int close_count = jsonResponse.getInt("close_count");

                        // UI 업데이트는 메인 스레드에서 실행
                        runOnUiThread(() -> {
                            drowsinessInfo.setText("Sleep Level : " + sleep_level);
                            sleepAlertService.setSleepState(sleep_level);
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d("ServerResponse", "Image send failed: " + response.message());
                    runOnUiThread(() -> Toast.makeText(OpenCamera.this,
                            "서버 오류: " + response.message(), Toast.LENGTH_SHORT).show()); // 서버 오류 시 메시지
                }
            }
        });
    }


    public static void checkCameraFormats(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                // 지원되는 포맷 확인
                int[] outputFormats = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputFormats();

                Log.d("CameraFormats", "Camera ID: " + cameraId);
                for (int format : outputFormats) {
                    Log.d("CameraFormats", "Supported Format: " + format);
                }
            }
        } catch (CameraAccessException e) {
            Log.e("CameraFormatChecker", "Camera Access Exception: " + e.getMessage());
        }
    }
}
