package com.example.watchoutdriver;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

public class SleepAlertService {
    private Context context;
    private MediaPlayer mediaPlayer;
    private Handler handler;
    private int currentSleepState = -1;

    public SleepAlertService(Context context) {
        this.context = context;
        this.mediaPlayer = new MediaPlayer();
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setSleepState(int sleepState) {
        if (currentSleepState == sleepState) return; // 상태가 변하지 않으면 무시
        currentSleepState = sleepState;

        handler.removeCallbacksAndMessages(null); // 기존 실행 취소
        handler.postDelayed(() -> playAlert(sleepState), 0); // 바로 알림 실행
    }

    private void playAlert(int sleepState) {
        // 단계별 음성 및 알림 출력
        switch (sleepState) {
            case 1:
                playVoiceMessage("window_open_stretch");
                break;
            case 2:
                playVoiceMessage("rest_area_warning");
//                playWarningSound();
                break;
            default:
                stopAlert();
                break;
        }

        // 1분 간격 반복
        handler.postDelayed(() -> playAlert(sleepState), 60000);
    }

    private void playVoiceMessage(String message) {
        // message에 따라 미디어 파일 선택 (예: "window_open_stretch"와 "rest_area_warning")
        int resId = context.getResources().getIdentifier(message, "raw", context.getPackageName());
        if (resId != 0) {
            mediaPlayer.reset();
            mediaPlayer = MediaPlayer.create(context, resId);
            mediaPlayer.start();
        }
    }

    private void playWarningSound() {
        // 경보음을 위한 미디어 파일 재생 (경고음 파일을 raw 폴더에 저장)
        int resId = context.getResources().getIdentifier("alarm_sound", "raw", context.getPackageName());
        if (resId != 0) {
            mediaPlayer.reset();
            mediaPlayer = MediaPlayer.create(context, resId);
            mediaPlayer.start();
        }
    }

    public void stopAlert() {
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }
}