package com.example.watchoutdriver;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.widget.Toast;


public class MainScreen extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button driveButton = (Button) findViewById(R.id.driveButton);
        Button tmapButton = (Button) findViewById(R.id.tmapButton);

        driveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), OpenCamera.class);
                startActivity(intent);
            }
        });

        tmapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 티맵 패키지 이름
                String tmapPackageName = "com.skt.tmap.ku";

                // 티맵이 설치되어 있는지 확인
                PackageManager pm = getPackageManager();
                Intent intent = pm.getLaunchIntentForPackage(tmapPackageName);

                if (intent != null) {
                    // 티맵 실행 후 "졸음쉼터" 검색 쿼리 전달
                    String tmapSearchUri = "tmap://search?keyword=졸음쉼터";
                    Intent searchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(tmapSearchUri));
                    startActivity(searchIntent);
                } else {
                    // 티맵이 설치되지 않았으면, 티맵 설치 페이지로 이동
                    Intent playStoreIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + tmapPackageName));
                    startActivity(playStoreIntent);
                }
            }
        });
    }
}
