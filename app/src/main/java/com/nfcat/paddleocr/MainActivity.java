package com.nfcat.paddleocr;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.baidu.paddle.fastdeploy.RuntimeOption;
import com.baidu.paddle.fastdeploy.pipeline.PPOCRv3;
import com.baidu.paddle.fastdeploy.vision.OCRResult;
import com.baidu.paddle.fastdeploy.vision.ocr.Classifier;
import com.baidu.paddle.fastdeploy.vision.ocr.DBDetector;
import com.baidu.paddle.fastdeploy.vision.ocr.Recognizer;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.huantansheng.easyphotos.EasyPhotos;
import com.huantansheng.easyphotos.callback.SelectCallback;
import com.huantansheng.easyphotos.models.album.entity.Photo;
import com.nfcat.paddleocr.adapter.GlideEngine;
import com.nfcat.paddleocr.databinding.ActivityMainBinding;
import com.nfcat.paddleocr.services.OcrWebSocketServer;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;
    public static AppCompatActivity activity;
    public static PPOCRv3 predictor = new PPOCRv3();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        activity = this;

        String[] permission = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
        };

        new RxPermissions(this)
                .requestEach(permission)
                .subscribe();

        init();

        new OcrWebSocketServer(new InetSocketAddress(40090)).start();

        binding.main.setOnClickListener(l -> {
            EasyPhotos.createAlbum(
                            this, true, true,
                            GlideEngine.getInstance()
                    )
                    .setFileProviderAuthority("com.nfcat.paddleOCR.FileProvider")
                    .start(new SelectCallback() {
                        @Override
                        public void onCancel() {

                        }

                        @Override
                        public void onResult(ArrayList<Photo> photos, boolean isOriginal) {
                            //获取file，进行对应操作
                            File file = new File(photos.get(0).path);
                            Glide.with(MainActivity.this)
                                    .asBitmap()
                                    .load(file)
                                    .into(new CustomTarget<Bitmap>() {
                                        @Override
                                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                            OCRResult predict = predictor.predict(resource);
                                            Toast.makeText(MainActivity.this, Arrays.toString(predict.mText), Toast.LENGTH_SHORT).show();
                                        }

                                        @Override
                                        public void onLoadCleared(@Nullable Drawable placeholder) {

                                        }
                                    });
                        }
                    });
        });
    }


    void init() {
        String realModelDir = getCacheDir() + "/modules";
        String detModelName = "ch_PP-OCRv3_det_infer";
        // String detModelName = "ch_ppocr_mobile_v2.0_det_infer";
        String clsModelName = "ch_ppocr_mobile_v2.0_cls_infer";
        // String recModelName = "ch_ppocr_mobile_v2.0_rec_infer";
        String recModelName = "ch_PP-OCRv3_rec_infer";
        String realDetModelDir = realModelDir + "/" + detModelName;
        String realClsModelDir = realModelDir + "/" + clsModelName;
        String realRecModelDir = realModelDir + "/" + recModelName;
        String srcDetModelDir = "ppocr/models/" + detModelName;
        String srcClsModelDir = "ppocr/models/" + clsModelName;
        String srcRecModelDir = "ppocr/models/" + recModelName;
        Utils.copyFolder(this, srcDetModelDir, realDetModelDir);
        Utils.copyFolder(this, srcClsModelDir, realClsModelDir);
        Utils.copyFolder(this, srcRecModelDir, realRecModelDir);
        String realLabelPath = getCacheDir() + "/ppocr/labels/ppocr_keys_v1.txt";
        Utils.copyFile(this, "ppocr/labels/ppocr_keys_v1.txt", realLabelPath);

        String detModelFile = realDetModelDir + "/" + "inference.pdmodel";
        String detParamsFile = realDetModelDir + "/" + "inference.pdiparams";
        String clsModelFile = realClsModelDir + "/" + "inference.pdmodel";
        String clsParamsFile = realClsModelDir + "/" + "inference.pdiparams";
        String recModelFile = realRecModelDir + "/" + "inference.pdmodel";
        String recParamsFile = realRecModelDir + "/" + "inference.pdiparams";
        String recLabelFilePath = realLabelPath; // ppocr_keys_v1.txt
        RuntimeOption detOption = new RuntimeOption();
        RuntimeOption clsOption = new RuntimeOption();
        RuntimeOption recOption = new RuntimeOption();
        detOption.setCpuThreadNum(2);
        clsOption.setCpuThreadNum(2);
        recOption.setCpuThreadNum(2);
        detOption.setLitePowerMode("LITE_POWER_HIGH");
        clsOption.setLitePowerMode("LITE_POWER_HIGH");
        recOption.setLitePowerMode("LITE_POWER_HIGH");

        detOption.enableLiteFp16();
        clsOption.enableLiteFp16();
        recOption.enableLiteFp16();

        DBDetector detModel = new DBDetector(detModelFile, detParamsFile, detOption);
        Classifier clsModel = new Classifier(clsModelFile, clsParamsFile, clsOption);
        Recognizer recModel = new Recognizer(recModelFile, recParamsFile, recLabelFilePath, recOption);
        predictor.init(detModel, clsModel, recModel);
    }
}