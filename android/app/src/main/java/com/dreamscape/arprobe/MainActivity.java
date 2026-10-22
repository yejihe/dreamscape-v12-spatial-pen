package com.dreamscape.arprobe;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.SeekBar;
import android.view.View;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Anchor;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import java.util.Locale;

public final class MainActivity extends Activity implements GLSurfaceView.Renderer {
    private GLSurfaceView surface;
    private TextView status;
    private boolean expandedStatus=false;
    private Button startButton;
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
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
    private final ArrayList<Float> radii = new ArrayList<>();
    private volatile float brushRadius=0.010f;
    private volatile float[] liveTip=null;
    private final float[] anchorMatrix=new float[16], anchoredMvp=new float[16];
    private ArrayList<float[]> currentStroke = null;
    private float[] filteredTip=null;
    private int rejectedJumps=0;
    private final ArrayList<Anchor> strokeAnchors = new ArrayList<>();
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

    private void say(String s) {
        message=s;
        runOnUiThread(()->{
            if(status!=null){
                String[] parts=message.split("\\n");
                String track="";
                for(String line:parts) if(line.startsWith("追踪状态：")) {track=line;break;}
                status.setText(expandedStatus || track.isEmpty()?message:track+"  ·  点击展开数据");
            }
        });
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(6),dp(10),dp(6));
        TextView title = new TextView(this);
        title.setText("🌌 Dreamscape V12 · 空间画笔 v0.3.4");
        title.setTextSize(17);
        root.addView(title);
        status = new TextView(this);
        status.setTextSize(14); status.setText(message);status.setMaxLines(10);
        status.setOnClickListener(v->{expandedStatus=!expandedStatus;say(message);});
        root.addView(status);
        Button start = new Button(this);startButton=start; start.setText("启动 ARCore");
        root.addView(start);
        start.setOnClickListener(v -> begin());
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(4),dp(4),dp(4),dp(4));
        Button draw = new Button(this); draw.setText("●  按住绘画");
        draw.setTextSize(23);
        draw.setTypeface(null,Typeface.BOLD);
        draw.setTextColor(Color.WHITE);
        draw.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(26,128,211)));
        draw.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                penDown=true;draw.setText("●  正在绘画…");
                draw.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(239,79,91)));
                draw.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                return true;
            }
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                penDown=false;draw.setText("●  按住绘画");
                draw.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(26,128,211)));
                return true;
            }
            return true;
        });
        TextView brushText=new TextView(this);
brushText.setText("笔刷：20 mm");brushText.setTextSize(15);
SeekBar brushSize=new SeekBar(this);brushSize.setMax(36);brushSize.setProgress(16);
brushSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
public void onProgressChanged(SeekBar b,int p,boolean fromUser){int mm=4+p;brushRadius=mm/2000f;brushText.setText("笔刷："+mm+" mm");}
public void onStartTrackingTouch(SeekBar b){}
public void onStopTrackingTouch(SeekBar b){}
});
toolbar.addView(brushText);toolbar.addView(brushSize,new LinearLayout.LayoutParams(-1,dp(38)));
Button undo=new Button(this);undo.setText("撤销");
        undo.setOnClickListener(v->surface.queueEvent(()->{ if(!strokes.isEmpty()){strokes.remove(strokes.size()-1);radii.remove(radii.size()-1);strokeAnchors.remove(strokeAnchors.size()-1).detach();}currentStroke=null;filteredTip=null;previousPenDown=false; }));
        Button clear=new Button(this);clear.setText("清空");
        clear.setOnClickListener(v->surface.queueEvent(()->{for(Anchor a:strokeAnchors)a.detach();strokeAnchors.clear();strokes.clear();radii.clear();currentStroke=null;filteredTip=null;previousPenDown=false;}));
        toolbar.addView(draw,new LinearLayout.LayoutParams(-1,dp(76)));
        draw.setMinimumHeight(dp(76));
        LinearLayout actions=new LinearLayout(this);
        actions.addView(undo,new LinearLayout.LayoutParams(0,dp(49),1));
        actions.addView(clear,new LinearLayout.LayoutParams(0,58,1));
        toolbar.addView(actions);
        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setRenderer(this);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(toolbar);
        setContentView(root);
        say("启动 ARCore 后，按住底部按钮绘画；点击追踪状态可查看详细数据。");
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
            startButton.setVisibility(View.GONE);
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
        String lvs="uniform mat4 uMVP; attribute vec3 aPoint; void main(){gl_Position=uMVP*vec4(aPoint,1.0);gl_PointSize=25.0;}";
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
                Pose tip=p.compose(Pose.makeTranslation(0f,0f,-0.42f));
                float[] xyz=tip.getTranslation();
                if(!previousPenDown || currentStroke==null) {
                    currentStroke=new ArrayList<>();
                    strokes.add(currentStroke);
                    radii.add(brushRadius);
                    strokeAnchors.add(session.createAnchor(tip));
                    filteredTip=xyz.clone();
                }
                if(filteredTip==null)filteredTip=xyz.clone();
                // One-pole stabilization: suppress hand jitter but preserve deliberate movement.
                for(int i=0;i<3;i++)filteredTip[i]+=0.35f*(xyz[i]-filteredTip[i]);
                Anchor anchor=strokeAnchors.get(strokeAnchors.size()-1);
                float[] local=anchor.getPose().inverse().transformPoint(filteredTip);
                if(currentStroke.isEmpty() || distance(currentStroke.get(currentStroke.size()-1),local)>0.006f) {
                    // Do not bridge sudden tracking jumps with a giant segment.
                    if(currentStroke.isEmpty() || distance(currentStroke.get(currentStroke.size()-1),local)<0.18f)
                        currentStroke.add(local);
                    else {penDown=false;currentStroke=null;}
                }
            }else {currentStroke=null;filteredTip=null;}
            liveTip=tracking?p.compose(Pose.makeTranslation(0f,0f,-0.42f)).getTranslation():null;
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
    private static float[] catmull(float[] p0,float[] p1,float[] p2,float[] p3,float t){
        float[] v=new float[3];float t2=t*t,t3=t2*t;
        for(int i=0;i<3;i++)v[i]=0.5f*((2*p1[i])+(-p0[i]+p2[i])*t+(2*p0[i]-5*p1[i]+4*p2[i]-p3[i])*t2+(-p0[i]+3*p1[i]-3*p2[i]+p3[i])*t3);
        return v;
    }
    private static float[] cross(float[] a,float[] b){
        return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
    }
    private static float[] normalize(float[] v){
        float n=(float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if(n<0.00001f)return new float[]{1,0,0};
        return new float[]{v[0]/n,v[1]/n,v[2]/n};
    }
    private static float[] minus(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private void renderStrokes(){
        GLES20.glUseProgram(lineProgram);
        GLES20.glUniform4f(lineColor,0.18f,0.88f,1.0f,1.0f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnableVertexAttribArray(lineAttr);
        // Catmull-Rom interpolated centerline + continuous 8-sided round tube.
        // Fixed world radius 6 mm; no detached rectangular segments.
        final int sides=8;
        for(int strokeIndex=0;strokeIndex<strokes.size();strokeIndex++){
            if(strokeIndex>=strokeAnchors.size())continue;
            Anchor anchor=strokeAnchors.get(strokeIndex);
            if(anchor.getTrackingState()!=TrackingState.TRACKING)continue;
            anchor.getPose().toMatrix(anchorMatrix,0);
            Matrix.multiplyMM(anchoredMvp,0,mvp,0,anchorMatrix,0);
            GLES20.glUniformMatrix4fv(lineMvp,1,false,anchoredMvp,0);
            ArrayList<float[]> stroke=strokes.get(strokeIndex);
            final float radius=radii.get(strokeIndex);
            if(stroke.size()<2)continue;
            ArrayList<float[]> path=new ArrayList<>();
            for(int k=0;k<stroke.size()-1;k++){
                float[] p0=stroke.get(Math.max(0,k-1)),p1=stroke.get(k);
                float[] p2=stroke.get(k+1),p3=stroke.get(Math.min(stroke.size()-1,k+2));
                int subdivisions=Math.max(2,Math.min(8,(int)(distance(p1,p2)/0.005f)));
                for(int i=0;i<subdivisions;i++)path.add(catmull(p0,p1,p2,p3,(float)i/subdivisions));
            }
            path.add(stroke.get(stroke.size()-1));
            float[] prevSide=null;
            float[][] ringPrev=null;
            for(int j=0;j<path.size();j++){
                float[] tangent=normalize(minus(path.get(Math.min(j+1,path.size()-1)),path.get(Math.max(0,j-1))));
                float[] ref=Math.abs(tangent[1])<0.85f?new float[]{0,1,0}:new float[]{1,0,0};
                float[] side=normalize(cross(tangent,ref));
                if(prevSide!=null && (side[0]*prevSide[0]+side[1]*prevSide[1]+side[2]*prevSide[2])<0)
                    for(int q=0;q<3;q++)side[q]=-side[q];
                float[] up=normalize(cross(tangent,side));
                float[][] ring=new float[sides][3];
                for(int q=0;q<sides;q++){
                    double angle=2*Math.PI*q/sides;
                    for(int h=0;h<3;h++)ring[q][h]=path.get(j)[h]+radius*(float)(Math.cos(angle)*side[h]+Math.sin(angle)*up[h]);
                }
                if(ringPrev!=null){
                    // Build one connected smooth cylinder using triangles per ring pair.
                    float[] triangles=new float[sides*6*3];int offset=0;
                    for(int q=0;q<sides;q++){
                        int next=(q+1)%sides;
                        for(float[] p:new float[][]{ringPrev[q],ringPrev[next],ring[q],ring[q],ringPrev[next],ring[next]}){
                            System.arraycopy(p,0,triangles,offset,3);offset+=3;
                        }
                    }
                    FloatBuffer buffer=floatBuffer(triangles);
                    GLES20.glVertexAttribPointer(lineAttr,3,GLES20.GL_FLOAT,false,0,buffer);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sides*6);
                }
                ringPrev=ring;prevSide=side;
            }
        }
        if(liveTip!=null){
            GLES20.glUniformMatrix4fv(lineMvp,1,false,mvp,0);
            GLES20.glUniform4f(lineColor,1.0f,penDown?0.25f:0.78f,0.15f,1f);
            FloatBuffer dot=floatBuffer(liveTip);
            GLES20.glVertexAttribPointer(lineAttr,3,GLES20.GL_FLOAT,false,0,dot);
            GLES20.glDrawArrays(GLES20.GL_POINTS,0,1);
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
    @Override protected void onDestroy() { active = false; if(session!=null){for(Anchor a:strokeAnchors)a.detach();strokeAnchors.clear();session.close();session=null;} super.onDestroy(); }
}
