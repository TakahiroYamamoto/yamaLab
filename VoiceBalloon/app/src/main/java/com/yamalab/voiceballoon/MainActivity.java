package com.yamalab.voiceballoon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Face;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private SpeechRecognizer recog;
    private Runnable readyRecognizeSpeech;
    private Handler handler = new Handler();

    private Camera camera;
    private SurfaceView preview;
    private SurfaceView overlay;
    private CameraListener cameraListener;
    private OverlayListener overlayListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = (SurfaceView) findViewById(R.id.preview);
        cameraListener = new CameraListener(preview);

        overlay = (SurfaceView) findViewById(R.id.overlay);
        overlayListener = new OverlayListener(overlay);

        // Voice2Text
        recog = SpeechRecognizer.createSpeechRecognizer(this);
        recog.setRecognitionListener(new RecogListener(this));

        readyRecognizeSpeech = new Runnable() {
            @Override public void run() {
                startRecognizeSpeech();
            }
        };

        // listener登録
        Button b = (Button)findViewById(R.id.start_recognize);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecognizeSpeech();
            }
        });
        startRecognizeSpeech();
    }
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        preview.getHolder().addCallback(cameraListener);
        overlay.getHolder().addCallback(overlayListener);
    }

    private class CameraListener implements
            SurfaceHolder.Callback,
            Camera.FaceDetectionListener
    {
        private SurfaceView surfaceView;
        private SurfaceHolder surfaceHolder;

        public CameraListener(SurfaceView surfaceView) {
            this.surfaceView = surfaceView;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surfaceHolder = holder;
            try {
                int cameraId = -1;
                // フロントカメラを探す。
                Camera.CameraInfo info = new Camera.CameraInfo();
                for (int id = 0; id < Camera.getNumberOfCameras(); id++) {
                    Camera.getCameraInfo(id, info);
                    if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                        cameraId = id;
                        break;
                    }
                }
                camera = Camera.open(cameraId);
                camera.setPreviewDisplay(holder);
                camera.getParameters().setPreviewFpsRange(1, 20);
                camera.setDisplayOrientation(90); // portrate 固定
                // 顔認証機能サポートチェック。
                if (camera.getParameters().getMaxNumDetectedFaces() == 0) {
                    throw new Error("Not supported face detected.");
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString(), e);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int width, int height) {
            surfaceHolder = holder;
            camera.startPreview();
            camera.setFaceDetectionListener(cameraListener);
            camera.startFaceDetection();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            camera.setFaceDetectionListener(null);
            camera.release();
            camera = null;
        }

        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            if (faces.length == 0) return;
            Face face = faces[0];
            if (face.score < 30) return;

            overlayListener.drawFace(faceRect2PixelRect(face), Color.RED);
        }

        /**
         * 顔認識範囲を描画用に座標変換する。
         * - Face.rect の座標系はプレビュー画像に対し -1000～1000 の相対座標。
         * - 座標(-1000,-1000)が左上、座標(0,0) が画像中心となる。
         * - 座標系のプレビュー画像はlandscapeとなる。portraitの場合が90度回転が必要。
         * @param face 顔認識情報
         * @return 描画用矩形範囲
         */
        private Rect faceRect2PixelRect(Face face) {
            int w = surfaceView.getWidth();
            int h = surfaceView.getHeight();
            Rect rect = new Rect();

            // フロントカメラなので左右反転、portraitなので座標軸反転
            rect.left = w * (-face.rect.top + 1000) / 2000;
            rect.right = w * (-face.rect.bottom + 1000) / 2000;
            rect.top = h * (-face.rect.left + 1000) / 2000;
            rect.bottom = h * (-face.rect.right + 1000) / 2000;
            //Log.d(TAG, "rect=" + face.rect + "=>" + rect);
            return rect;
        }

    }

    private class OverlayListener implements SurfaceHolder.Callback
    {
        private SurfaceView surfaceView;
        private SurfaceHolder surfaceHolder;

        private Paint paint = new Paint();

        public OverlayListener(SurfaceView surfaceView) {
            this.surfaceView = surfaceView;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surfaceHolder = holder;
            surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(surfaceView.getWidth() / 100);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surfaceHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // nop.
        }

        public void drawFace(Rect rect1, int color) {
            try {
                Canvas canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    try {
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        paint.setColor(color);
                        canvas.drawRect(rect1, paint);
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            } catch (IllegalArgumentException e) {
                Log.w(TAG, e.toString());
            }
        }

    }


    private void startRecognizeSpeech() {
        handler.removeCallbacks(readyRecognizeSpeech);

        Intent intent = RecognizerIntent.getVoiceDetailsIntent(getApplicationContext());
        recog.startListening(intent);

        ((TextView)findViewById(R.id.status)).setText("");
        ((TextView)findViewById(R.id.sub_status)).setText("");
        findViewById(R.id.start_recognize).setEnabled(false);
    }

    private static class RecogListener implements RecognitionListener {
        private MainActivity caller;
        private TextView status;
        private TextView subStatus;

        RecogListener(MainActivity a) {
            caller = a;
            status = (TextView)a.findViewById(R.id.status);
            subStatus = (TextView)a.findViewById(R.id.sub_status);
        }

        // 音声認識準備完了
        @Override
        public void onReadyForSpeech(Bundle params) {
            status.setText("ready for speech");
            Log.v(TAG,"ready for speech");
        }

        // 音声入力開始
        @Override
        public void onBeginningOfSpeech() {
            status.setText("beginning of speech");
            Log.v(TAG,"beginning of speech");
        }

        // 録音データのフィードバック用
        @Override
        public void onBufferReceived(byte[] buffer) {
            //status.setText("onBufferReceived");
            //Log.v(TAG,"onBufferReceived");
        }

        // 入力音声のdBが変化した
        @Override
        public void onRmsChanged(float rmsdB) {
            String s = String.format("recieve : % 2.2f[dB]", rmsdB);
            subStatus.setText(s);
            //Log.v(TAG,"recieve : " + rmsdB + "dB");
        }

        // 音声入力終了
        @Override
        public void onEndOfSpeech() {
            status.setText("end of speech");
            Log.v(TAG,"end of speech");
            caller.handler.postDelayed(caller.readyRecognizeSpeech, 500);
        }

        // ネットワークエラー又は、音声認識エラー
        @Override
        public void onError(int error) {
            status.setText("on error");
            Log.v(TAG,"on error");
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    // 音声データ保存失敗
                    subStatus.setText("ERROR_AUDIO");
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    // Android端末内のエラー(その他)
                    subStatus.setText("ERROR_CLIENT");
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    // 権限無し
                    subStatus.setText("ERROR_INSUFFICIENT_PERMISSIONS");
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    // ネットワークエラー(その他)
                    subStatus.setText("ERROR_NETWORK");
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    // ネットワークタイムアウトエラー
                    subStatus.setText("ERROR_NETWORK_TIMEOUT");
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    // 音声認識結果無し
                    subStatus.setText("ERROR_NO_MATCH");
                    caller.handler.postDelayed(caller.readyRecognizeSpeech,1000);
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    // RecognitionServiceへ要求出せず
                    subStatus.setText("ERROR_RECOGNIZER_BUSY");
                    caller.handler.postDelayed(caller.readyRecognizeSpeech,1000);
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    // Server側からエラー通知
                    subStatus.setText("ERROR_SERVER");
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    // 音声入力無し
                    subStatus.setText("ERROR_SPEECH_TIMEOUT");
                    caller.handler.postDelayed(caller.readyRecognizeSpeech,1000);
                    break;
                default:
            }
        }

        // イベント発生時に呼び出される
        @Override
        public void onEvent(int eventType, Bundle params) {
            status.setText("on event");
            Log.v(TAG,"on event");
        }

        // 部分的な認識結果が得られる場合に呼び出される
        @Override
        public void onPartialResults(Bundle partialResults) {
            status.setText("on partial results");
            Log.v(TAG,"on results");
        }

        // 認識結果
        @Override
        public void onResults(Bundle data) {
            status.setText("on results");
            Log.v(TAG,"on results");

            ArrayList<String> results = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            TextView t = (TextView)caller.findViewById(R.id.result);
            t.setText("");
            for (String s : results) {
                t.append(s + "\n");
            }

            boolean end=false;
            for (String s : results) {
                if (s.equals("終わり"))
                    end=true;
                if (s.equals("おわり"))
                    end=true;
                if (s.equals("キャンセル"))
                    end=true;
            }
            if (end)
                caller.findViewById(R.id.start_recognize).setEnabled(true);
            else
                caller.startRecognizeSpeech();
        }
    }
}