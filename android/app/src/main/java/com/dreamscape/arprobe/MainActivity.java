package com.dreamscape.arprobe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import java.util.Locale;

public final class MainActivity extends Activity implements GLSurfaceView.Renderer {
    private GLSurfaceView surface;
    private TextView status;
    private Session session;
    private volatile String message = "未启动";
    private volatile boolean active = false;
    private boolean installRequested = false;
    private int texture = 0, frames = 0;

    private void say(String s) { message = s; runOnUiThread(() -> status.setText(message)); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 28, 20, 20);
        TextView title = new TextView(this);
        title.setText("🌌 Dreamscape V12 · 原生 ARCore 诊断\n仅用于确认真实六自由度追踪（不显示相机背景）");
        title.setTextSize(18);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(16); status.setText(message);
        root.addView(status);
        Button start = new Button(this); start.setText("检查并启动 ARCore");
        root.addView(start);
        start.setOnClickListener(v -> begin());
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setRenderer(this);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        say("点击启动；允许相机权限后观察 TRACKING 与 XYZ 是否变化。");
    }
    private void begin() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 501); return;
        }
        try {
            ArCoreApk.Availability a = ArCoreApk.getInstance().checkAvailability(this);
            say("ARCore 可用性：" + a);
            if (a.isTransient()) { say("ARCore 状态仍在检测，请稍后再次点击"); return; }
            if (!a.isSupported()) { say("ARCore 报告不支持：" + a); return; }
            ArCoreApk.InstallStatus install = ArCoreApk.getInstance().requestInstall(this, !installRequested);
            if (install == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true; say("系统正在请求安装/更新 AR 服务，完成后返回应用再试"); return;
            }
            if (session == null) session = new Session(this);
            active = true;
            surface.onResume();
            session.resume();
            say("会话已创建；尝试获取 TRACKING 帧…");
        } catch (Exception e) {
            active = false;
            say("启动失败：" + e.getClass().getSimpleName() + "\n" + e.getMessage());
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 501) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) begin();
            else say("未获得相机权限，无法启动 ARCore");
        }
    }
    @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig cfg) {
        int[] ids = new int[1]; GLES20.glGenTextures(1, ids, 0); texture = ids[0];
    }
    @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int w, int h) { GLES20.glViewport(0,0,w,h); }
    @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
        GLES20.glClearColor(0.04f,0.07f,0.13f,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (!active || session == null || texture == 0) return;
        try {
            session.setCameraTextureName(texture);
            Frame f = session.update();
            Camera c = f.getCamera();
            Pose p = c.getPose();
            frames++;
            if (frames % 12 == 0) {
                String s = String.format(Locale.US, "帧数：%d\n追踪状态：%s\nX: %.3f m\nY: %.3f m\nZ: %.3f m\n方向四元数: %.3f, %.3f, %.3f, %.3f",
                    frames, c.getTrackingState(), p.tx(), p.ty(), p.tz(), p.qx(), p.qy(), p.qz(), p.qw());
                say(s);
            }
        } catch (Exception e) { active = false; say("追踪失败：" + e.getClass().getSimpleName() + "\n" + e.getMessage()); }
    }
    @Override protected void onPause() {
        active = false;
        if (session != null) session.pause();
        surface.onPause(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (session != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin();
    }
    @Override protected void onDestroy() { active = false; if(session!=null){session.close();session=null;} super.onDestroy(); }
}
