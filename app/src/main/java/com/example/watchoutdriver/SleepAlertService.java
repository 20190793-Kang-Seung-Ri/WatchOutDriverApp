package com.example.watchoutdriver;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;

public class SleepAlertService {
    private Context context;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int currentSleepState = -1;
    private int originalVolume; // 원래 볼륨을 저장하기 위한 변수

    public SleepAlertService(Context context) {
        this.context = context;
        this.mediaPlayer = new MediaPlayer();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); // 변경: 알람 볼륨 대신 음악 볼륨
    }

    public void setSleepState(int sleepState) {
        if (currentSleepState == sleepState) return; // 상태가 변하지 않으면 무시
        currentSleepState = sleepState;

        // 졸음 레벨이 0일 때 알림을 멈추지 않음
        if (sleepState == 0) {
            stopAlert(); // 0단계에서는 알림을 멈춤
            return;
        }

        playAlert(sleepState); // 상태에 맞는 알림 실행
    }

    public void playAlert(int sleepState) {
        switch (sleepState) {
            case 1:
                playVoiceMessage("window_open_stretch");
                break;
            case 2:
                playVoiceMessage("rest_area_warning");
                break;
            case 3:
                setMaxVolume(); // 알람 최대 볼륨으로 설정
                playVoiceMessage("alarm");
                break;
            default:
                stopAlert();
                break;
        }
    }

    private void playVoiceMessage(String message) {
        int resId = context.getResources().getIdentifier(message, "raw", context.getPackageName());
        if (resId != 0) {
            mediaPlayer.reset();
            mediaPlayer = MediaPlayer.create(context, resId);

            // 음성을 시작하기 전에 볼륨을 이미 최대로 설정한 상태에서 시작
            mediaPlayer.start();

            // 재생 완료 후 원래 볼륨으로 복원
            mediaPlayer.setOnCompletionListener(mp -> resetVolume());
        }
    }

    // 알람을 최대 볼륨으로 설정
    private void setMaxVolume() {
        // 음악 스트림으로 최대 볼륨 설정
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0);
    }

    // 원래 볼륨으로 복원
    private void resetVolume() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0);
    }

    public void stopAlert() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        resetVolume(); // 알람을 멈추면 원래 볼륨으로 복원
    }
}
