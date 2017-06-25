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
import android.graphics.RectF;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.os.AsyncTask;
import java.io.IOException;


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

        startRecognizeSpeech();
    }
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        preview.getHolder().addCallback(cameraListener);
        overlay.getHolder().addCallback(overlayListener);
    }

    private class CameraListener implements
            SurfaceHolder.Callback
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
            } catch (Exception e) {
                Log.e(TAG, e.toString(), e);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int width, int height) {
            surfaceHolder = holder;
            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            camera.release();
            camera = null;
        }
    }

    public class OverlayListener implements SurfaceHolder.Callback
    {
        public SurfaceView surfaceView;
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

        public void drawFace(float left, float top, int color, String text) {
            try {
                Canvas canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    try {
                        int texth = 80;
                        int textw = 80*text.length();
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        paint.setColor(color);
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawRoundRect(new RectF(left, top, left+textw, top+texth), 30, 30, paint);
                        paint.setColor(Color.BLACK);
                        paint.setTextSize(texth);
                        canvas.drawText(text, left, top+texth-5, paint);
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
    }

    private class RecogListener implements RecognitionListener {
        private MainActivity caller;

        RecogListener(MainActivity a) {
            caller = a;
        }

        // 音声認識準備完了
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.v(TAG,"ready for speech");
        }

        // 音声入力開始
        @Override
        public void onBeginningOfSpeech() {
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
            //Log.v(TAG,"recieve : " + rmsdB + "dB");
        }

        // 音声入力終了
        @Override
        public void onEndOfSpeech() {
            Log.v(TAG,"end of speech");
            caller.handler.postDelayed(caller.readyRecognizeSpeech, 500);
        }

        // ネットワークエラー又は、音声認識エラー
        @Override
        public void onError(int error) {
            Log.v(TAG,"on error");
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    // 音声データ保存失敗
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    // Android端末内のエラー(その他)
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    // 権限無し
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    // ネットワークエラー(その他)
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    // ネットワークタイムアウトエラー
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    // 音声認識結果無し
                    caller.handler.postDelayed(caller.readyRecognizeSpeech,1000);
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    // RecognitionServiceへ要求出せず
                    caller.handler.postDelayed(caller.readyRecognizeSpeech,1000);
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    // Server側からエラー通知
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    // 音声入力無し
                    caller.handler.postDelayed(caller.readyRecognizeSpeech,1000);
                    break;
                default:
            }
        }

        // イベント発生時に呼び出される
        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.v(TAG,"on event");
        }

        // 部分的な認識結果が得られる場合に呼び出される
        @Override
        public void onPartialResults(Bundle partialResults) {
            Log.v(TAG,"on results");
        }

        // 認識結果
        @Override
        public void onResults(Bundle data) {
            Log.v(TAG, "on results");

            ArrayList<String> results = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            //  翻訳の実装実験
            String baseUrl = "https://www.googleapis.com/language/translate/v2?key=AIzaSyBa-gekC5Gu2uPuxOA7y32Gm8MSeftttXo";
            String srcLang = "&source=ja";
            String targetLang = "&target=en";
            String transChar = "&q=" + results.get(0);

            String postStr = baseUrl + srcLang + targetLang + transChar;

//            TextView t = (TextView)caller.findViewById(R.id.result);

            try {
                new HttpPostTask(overlayListener).execute(new URL(postStr));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }



            //　文字列の先頭（確度が高いやつ）を抜き出しで表示
            String ballonText = results.get(0);
            Log.v(TAG, "先頭文字列：" + ballonText);


//            // ある程度場所を散らす
//            Random r = new Random();
//            int top = r.nextInt(500) + 700;
//            int left = r.nextInt(500) + 100;
//
//
//            overlayListener.drawFace(left, top, Color.GREEN, ballonText);

            boolean end = false;
            for (String s : results) {
                if (s.equals("終わり"))
                    end = true;
                if (s.equals("おわり"))
                    end = true;
                if (s.equals("キャンセル"))
                    end = true;
            }

            if (!end) {
                caller.startRecognizeSpeech();
            }
        }
    }
}


// http://www.programing-style.com/android/android-api/android-httpurlconnection-post/
class HttpPostTask extends AsyncTask<URL, Void, String> {
    private MainActivity.OverlayListener textView;

    public HttpPostTask(MainActivity.OverlayListener textView) {
        super();
        this.textView = textView;
    }
    @Override
    protected String doInBackground(URL... urls) {

        final URL url = urls[0];
        HttpURLConnection con = null;
        String translatedText = "";
        try {
            con = (HttpURLConnection) url.openConnection();
            con.setDoOutput(true);
            con.setChunkedStreamingMode(0);
            con.connect();

            // POSTデータ送信処理
            OutputStream out = null;
            try {
                out = con.getOutputStream();
                out.write("POST DATA".getBytes("UTF-8"));
                out.flush();
            } catch (IOException e) {
                // POST送信エラー
                e.printStackTrace();
            } finally {
                if (out != null) {
                    out.close();
                }
            }

            final int status = con.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                StringBuffer responseJSON = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    responseJSON.append(inputLine);
                }
                int headOfTranstaredText = responseJSON.lastIndexOf("translatedText");
                String seekHeadString = responseJSON.substring(headOfTranstaredText+18);
                int endOfTranstaredText = seekHeadString.indexOf("\"");
                translatedText = seekHeadString.substring(0, endOfTranstaredText);

                // この時点で、翻訳済みのJSONデータを取得済み!
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
        return translatedText;
    }
    @Override
    protected void onPostExecute(String result)
    {
        // ある程度場所を散らす
        Random r = new Random();
        int top = r.nextInt(500) + 700;
        int left = r.nextInt(500) + 100;

        textView.drawFace(left, top, Color.GREEN, result);
//        textView.drawFace(result);

    }

}