package com.dreamscape.arprobe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.view.MotionEvent;
import java.util.ArrayList;
import com.google.ar.core.Coordinates2d;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
    private int texture = 0, frames = 0, shader = 0, posAttr = 0, uvAttr = 0, texUniform = 0;
    private volatile int viewWidth = 1, viewHeight = 1;
    private boolean geometryInitialized = false;
    private volatile boolean penDown = false;
    private boolean previousPenDown = false;
    private final ArrayList<ArrayList<float[]>> strokes = new ArrayList<>();
    private ArrayList<float[]> currentStroke = null;
    private int lineProgram, lineAttr, lineMvp, lineColor;
    private final float[] projection = new float[16], viewMatrix = new float[16], mvp = new float[16];
    private final FloatBuffer vertices = floatBuffer(new float[]{-1,-1, 1,-1, -1,1, 1,1});
    private final FloatBuffer texcoords = floatBuffer(new float[8]);
    private static FloatBuffer floatBuffer(float[] a) { FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a);b.position(0);return b; }
    private static int compile(int type,String code){int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,code);GLES20.glCompileShader(id);return id;}
    private void drawCamera(){
        GLES20.glUseProgram(shader);
        vertices.position(0);texcoords.position(0);
        GLES20.glEnableVertexAttribArray(posAttr);GLES20.glVertexAttribPointer(posAttr,2,GLES20.GL_FLOAT,false,0,vertices);
        GLES20.glEnableVertexAttribArray(uvAttr);GLES20.glVertexAttribPointer(uvAttr,2,GLES20.GL_FLOAT,false,0,texcoords);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glUniform1i(texUniform,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(posAttr);GLES20.glDisableVertexAttribArray(uvAttr);
    }

    private void say(String s) { message = s; runOnUiThread(() -> status.setText(message)); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 28, 20, 20);
        TextView title = new TextView(this);
        title.setText("🌌 Dreamscape V12 · 空间画笔 v0.3.0\n按住画线 · 松开停笔 · 移动手机观察");
        title.setTextSize(18);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(16); status.setText(message);
        root.addView(status);
        Button start = new Button(this); start.setText("检查并启动 ARCore");
        root.addView(start);
        start.setOnClickListener(v -> begin());
        LinearLayout toolbar = new LinearLayout(this);
        Button draw = new Button(this); draw.setText("按住绘画");
        draw.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){penDown=true;return true;}
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){penDown=false;return true;}
            return true;
        });
        Button undo=new Button(this);undo.setText("撤销");
        undo.setOnClickListener(v->surface.queueEvent(()->{ if(!strokes.isEmpty())strokes.remove(strokes.size()-1);currentStroke=null;previousPenDown=false; }));
        Button clear=new Button(this);clear.setText("清空");
        clear.setOnClickListener(v->surface.queueEvent(()->{strokes.clear();currentStroke=null;previousPenDown=false;}));
        toolbar.addView(draw,new LinearLayout.LayoutParams(0,-2,2));
        toolbar.addView(undo,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(clear,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(toolbar);
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setRenderer(this);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        say("先启动 ARCore；TRACKING 后按住按钮移动手机绘画。笔尖暂以相机前上方偏移估计，尚未做顶部实体标定。");
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
        geometryInitialized=false;
        int[] ids = new int[1]; GLES20.glGenTextures(1,ids,0);texture=ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        String vs="attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){vUv=aUv;gl_Position=vec4(aPos,0.,1.);}";
        String fs="#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vUv; uniform samplerExternalOES uCamera; void main(){gl_FragColor=texture2D(uCamera,vUv);}";
        shader=GLES20.glCreateProgram();GLES20.glAttachShader(shader,compile(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(shader,compile(GLES20.GL_FRAGMENT_SHADER,fs));GLES20.glLinkProgram(shader);
        posAttr=GLES20.glGetAttribLocation(shader,"aPos");uvAttr=GLES20.glGetAttribLocation(shader,"aUv");texUniform=GLES20.glGetUniformLocation(shader,"uCamera");
        String lvs="uniform mat4 uMVP; attribute vec3 aPoint; void main(){gl_Position=uMVP*vec4(aPoint,1.0);gl_PointSize=10.0;}";
        String lfs="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";
        lineProgram=GLES20.glCreateProgram();GLES20.glAttachShader(lineProgram,compile(GLES20.GL_VERTEX_SHADER,lvs));GLES20.glAttachShader(lineProgram,compile(GLES20.GL_FRAGMENT_SHADER,lfs));GLES20.glLinkProgram(lineProgram);
        lineAttr=GLES20.glGetAttribLocation(lineProgram,"aPoint");lineMvp=GLES20.glGetUniformLocation(lineProgram,"uMVP");lineColor=GLES20.glGetUniformLocation(lineProgram,"uColor");
    }
    @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int w, int h) { viewWidth=w;viewHeight=h;geometryInitialized=false;GLES20.glViewport(0,0,w,h); }
    @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
        GLES20.glClearColor(0.04f,0.07f,0.13f,1); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (!active || session == null || texture == 0) return;
        try {
            session.setDisplayGeometry(getWindowManager().getDefaultDisplay().getRotation(),viewWidth,viewHeight);
            session.setCameraTextureName(texture);
            Frame f = session.update();
            if(!geometryInitialized || f.hasDisplayGeometryChanged()) {
                vertices.position(0);
                texcoords.position(0);
                f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,texcoords);
                geometryInitialized=true;
            }
            if (f.getTimestamp() != 0) drawCamera();
            Camera c = f.getCamera();
            Pose p = c.getPose();
            boolean tracking=c.getTrackingState()==TrackingState.TRACKING;
            if(penDown && tracking) {
                // ARCore camera pose is world-from-camera. This is a provisional,
                // configurable virtual tip, NOT a calibrated physical phone top.
                Pose tip=p.compose(Pose.makeTranslation(0f,0.06f,-0.18f));
                float[] xyz=tip.getTranslation();
                if(!previousPenDown || currentStroke==null) {
                    currentStroke=new ArrayList<>();strokes.add(currentStroke);
                }
                if(currentStroke.isEmpty() || distance(currentStroke.get(currentStroke.size()-1),xyz)>0.008f) currentStroke.add(xyz);
            }else {currentStroke=null;}
            previousPenDown=penDown && tracking;
            if(tracking) {
                c.getProjectionMatrix(projection,0,0.01f,100f);
                c.getViewMatrix(viewMatrix,0);
                Matrix.multiplyMM(mvp,0,projection,0,viewMatrix,0);
                renderStrokes();
            }
            frames++;
            if (frames % 12 == 0) {
                String s = String.format(Locale.US, "帧数：%d\n追踪状态：%s\nX: %.3f m\nY: %.3f m\nZ: %.3f m\n方向四元数: %.3f, %.3f, %.3f, %.3f",
                    frames, c.getTrackingState()+" / "+c.getTrackingFailureReason(), p.tx(), p.ty(), p.tz(), p.qx(), p.qy(), p.qz(), p.qw());
                say(s);
            }
        } catch (Exception e) { active = false; say("追踪失败：" + e.getClass().getSimpleName() + "\n" + e.getMessage()); }
    }
    private static float distance(float[] a,float[] b){float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return (float)Math.sqrt(x*x+y*y+z*z);}
    private void renderStrokes(){
        GLES20.glUseProgram(lineProgram);
        GLES20.glUniformMatrix4fv(lineMvp,1,false,mvp,0);
        GLES20.glUniform4f(lineColor,0.18f,0.88f,1.0f,1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnableVertexAttribArray(lineAttr);
        for(ArrayList<float[]> stroke:strokes){
            if(stroke.isEmpty())continue;
            float[] points=new float[stroke.size()*3];
            for(int i=0;i<stroke.size();i++){float[] p=stroke.get(i);System.arraycopy(p,0,points,i*3,3);}
            FloatBuffer buffer=floatBuffer(points);
            GLES20.glVertexAttribPointer(lineAttr,3,GLES20.GL_FLOAT,false,0,buffer);
            GLES20.glDrawArrays(stroke.size()>1?GLES20.GL_LINE_STRIP:GLES20.GL_POINTS,0,stroke.size());
        }
        GLES20.glDisableVertexAttribArray(lineAttr);
    }
    @Override protected void onPause() {
        active = false;penDown=false;
        if (session != null) session.pause();
        surface.onPause(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (session != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin();
    }
    @Override protected void onDestroy() { active = false; if(session!=null){session.close();session=null;} super.onDestroy(); }
}
