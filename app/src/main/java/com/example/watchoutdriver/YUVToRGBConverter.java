package com.example.watchoutdriver;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;


public class YUVToRGBConverter {
    public static Bitmap convert(byte[] yData, byte[] uData, byte[] vData, int width, int height) {
        // 간단한 YUV -> RGB 변환 예시 (실제 성능 개선이 필요할 수 있음)
        int[] pixels = new int[width * height];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int y = yData[i * width + j] & 0xFF;  // Y 값
                int u = uData[(i / 2) * (width / 2) + (j / 2)] & 0xFF;  // U 값
                int v = vData[(i / 2) * (width / 2) + (j / 2)] & 0xFF;  // V 값

                // YUV -> RGB 변환 수식
                int r = (int) (y + 1.402 * (v - 128));
                int g = (int) (y - 0.344136 * (u - 128) - 0.714136 * (v - 128));
                int b = (int) (y + 1.772 * (u - 128));

                // 범위 제한
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                // RGB 값을 픽셀 배열에 저장
                pixels[i * width + j] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        // Bitmap으로 변환
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }
}

