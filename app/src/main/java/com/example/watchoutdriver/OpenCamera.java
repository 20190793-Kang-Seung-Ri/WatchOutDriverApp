package com.example.watchoutdriver;

import android.os.Handler;
import android.content.DialogInterface;
import android.view.View;

import androidx.appcompat.app.AlertDialog; // Import for AlertDialog

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
    private int frame_send_count = 5;
    private SleepAlertService sleepAlertService;
    private TextView drowsinessInfo;
    private String server_url = "http://34.64.80.214:8000/";
    private String[] sleep_message = {"양호", "약간 졸림", "많이 졸림", "수면"};
    private int[] sleep_message_color = {
            0xFF00FF00, // 0: 양호 (Green)
            0xFFFFFF00, // 1: 약간 졸림 (Yellow)
            0xFFFFA500, // 2: 많이 졸림 (Orange) 
            0xFFFF0000  // 3: 수면 (Red)
    };
    private int[] sleepLevelCount = {0, 0, 0, 0};
    private boolean AlertShown = false;
    private boolean isBlinking = false; // 깜빡임 상태를 추적할 변수
    private long lastAlertTime = 0; // 마지막 알림창 표시 시간 기록
    private static final long ALERT_DELAY = 7500; // 5초 지연

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        previewView = findViewById(R.id.viewFinder);
        drowsinessInfo = findViewById(R.id.drowsinessInfo);

        sleepAlertService = new SleepAlertService(this); // 서비스 초기화
        sendInitializationRequest();
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
//                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);

                // JPG로 압축
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream); // 품질을 85로 조정
                byte[] jpegData = outputStream.toByteArray();

                // 프레임 카운트를 증가시키고 10프레임마다 서버로 전송
                frameCount++;
                if (frameCount % frame_send_count == 0) {
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
                .url(server_url + "process_video/") // URL을 올바르게 설정하세요
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
                            drowsinessInfo.setText("현재 상태 : " + sleep_message[sleep_level]);
                            drowsinessInfo.setTextColor(sleep_message_color[sleep_level]);
                            onResponseFromServer(sleep_level);
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

    private void onResponseFromServer(int sleep_level) {


        if (sleep_level == 3) {
            // 3단계에서는 즉시 알림
            sleepAlertService.setSleepState(sleep_level);

            // 처음 수면 상태 시 알림 창 표시
            if (!AlertShown) {
                long currentTime = System.currentTimeMillis();

                // 마지막 알림이 표시된 지 5초가 지나지 않았다면 알림을 무시함
                if (currentTime - lastAlertTime < ALERT_DELAY) {
                    return;
                }

                lastAlertTime = currentTime; // 알림 표시 시간 업데이트
                showSleepAlert(sleep_level); // 알림창 표시 및 경고음 반복 재생 시작
            }

            blinkText();
            resetSleepLevelCounts();
        } else {
            stopBlinkingText(); // 다른 단계에서는 깜빡임 중지

            if (sleep_level == 1) {
                // 1단계는 60회 카운트 후 알림
                sleepLevelCount[sleep_level]++;
                if (sleepLevelCount[sleep_level] >= 60) { // 1초에 6장
                    sleepAlertService.setSleepState(sleep_level);
                    resetSleepLevelCounts();
                }
            } else if (sleep_level == 2) {
                // 2단계는 30회 카운트 후 알림
                sleepLevelCount[sleep_level]++;
                if (sleepLevelCount[sleep_level] >= 30) {
                    sleepAlertService.setSleepState(sleep_level);
                    resetSleepLevelCounts();
                }
            } else if (sleep_level == 0) {
                // 졸음 레벨이 0일 때 알림 멈추지 않도록 처리
                sleepLevelCount[sleep_level]++;
                if (sleepLevelCount[sleep_level] >= 180) {
                    sleepAlertService.setSleepState(sleep_level);
                    resetSleepLevelCounts();
                }
            }
        }
    }

    private void showSleepAlert(int sleep_level) {
        runOnUiThread(() -> {
            // 알림창이 닫히기 전까지 소리를 반복 재생
            Handler handler = new Handler();
            Runnable soundRunnable = new Runnable() {
                @Override
                public void run() {
                    if (AlertShown) { // 알림창이 열려 있는 동안만 반복 재생
                        sleepAlertService.playAlert(sleep_level);
                        handler.postDelayed(this, 2100); // 2초마다 재생
                    }
                }
            };
            handler.post(soundRunnable); // 첫 실행

            new AlertDialog.Builder(OpenCamera.this)
                    .setTitle("경고")
                    .setMessage("현재 수면 상태입니다. 주의가 필요합니다!")
                    .setPositiveButton("확인", (dialog, which) -> {
                        dialog.dismiss();
                        AlertShown = false; // 알림 창 닫기
                        sleepAlertService.stopAlert(); // 소리 멈춤
                        handler.removeCallbacks(soundRunnable); // 반복 재생 중지
                    })
                    .setCancelable(false)
                    .show();

            AlertShown = true; // 알림창 표시 상태
        });
    }

    // 모든 sleepLevelCount를 초기화하는 함수
    private void resetSleepLevelCounts() {
        for (int i = 0; i < sleepLevelCount.length; i++) {
            sleepLevelCount[i] = 0;
        }
    }

    // 수면 단계일 때 텍스트를 깜빡이게 하는 메서드
    private void blinkText() {
        final Handler handler = new Handler();
        isBlinking = true;

        Runnable runnable = new Runnable() {
            boolean visible = true; // 텍스트 가시성 상태를 추적

            @Override
            public void run() {
                if (!isBlinking) return; // 수면 단계가 아니면 깜빡임 중지

                // 텍스트 가시성을 토글
                drowsinessInfo.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
                visible = !visible; // 가시성 전환

                // 500ms 후 다시 실행
                handler.postDelayed(this, 500);
            }
        };

        handler.post(runnable);
    }

    // 깜빡임을 멈추는 메서드
    private void stopBlinkingText() {
        isBlinking = false;
        drowsinessInfo.setVisibility(View.VISIBLE); // 가시성을 항상 표시로 설정
    }

    private void sendInitializationRequest() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(server_url + "initialize/") // 서버 초기화 URL
                .post(RequestBody.create(null, new byte[0])) // 빈 요청 본문
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("InitRequestError", "초기화 요청 실패: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(OpenCamera.this,
                        "서버 초기화 실패", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d("InitRequestSuccess", "서버 초기화 성공");
                    runOnUiThread(() -> Toast.makeText(OpenCamera.this,
                            "서버 초기화 성공", Toast.LENGTH_SHORT).show());
                } else {
                    Log.e("InitRequestError", "서버 초기화 실패: " + response.message());
                    runOnUiThread(() -> Toast.makeText(OpenCamera.this,
                            "서버 초기화 실패: " + response.message(), Toast.LENGTH_SHORT).show());
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
