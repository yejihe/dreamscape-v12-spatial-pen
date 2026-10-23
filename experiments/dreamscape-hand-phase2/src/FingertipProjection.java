package com.dreamscape.hand.math;

/** Unmirrored calibrated pixels + known optical depth -> ARCore camera/world metres.
 *  Does NOT estimate depth or transform rotated/mirrored landmark coordinates. */
public final class FingertipProjection {
  private FingertipProjection() {}
  public static float[] cameraPoint(float u,float v,float depth,float fx,float fy,float cx,float cy) {
    if(!Float.isFinite(u)||!Float.isFinite(v)||!Float.isFinite(depth)||
       !Float.isFinite(fx)||!Float.isFinite(fy)||!Float.isFinite(cx)||!Float.isFinite(cy)||
       depth<=0||fx<=0||fy<=0) throw new IllegalArgumentException("invalid pixel/intrinsics/depth");
    return new float[]{(u-cx)/fx*depth,-(v-cy)/fy*depth,-depth};
  }
  /** q=[x,y,z,w], t=[x,y,z] of camera pose in ARCore world coordinates. */
  public static float[] worldPoint(float[] p,float[] q,float[] t) {
    if(p==null||p.length!=3||q==null||q.length!=4||t==null||t.length!=3)
      throw new IllegalArgumentException("Expected point[3], quaternion[4], translation[3]");
    for(float v:p)if(!Float.isFinite(v))throw new IllegalArgumentException("invalid point");
    for(float v:q)if(!Float.isFinite(v))throw new IllegalArgumentException("invalid quaternion");
    for(float v:t)if(!Float.isFinite(v))throw new IllegalArgumentException("invalid translation");
    double norm=Math.sqrt((double)q[0]*q[0]+(double)q[1]*q[1]+(double)q[2]*q[2]+(double)q[3]*q[3]);
    if(norm<1e-8)throw new IllegalArgumentException("zero quaternion");
    double x=q[0]/norm,y=q[1]/norm,z=q[2]/norm,w=q[3]/norm;
    double px=p[0],py=p[1],pz=p[2];
    double ax=2*(y*pz-z*py),ay=2*(z*px-x*pz),az=2*(x*py-y*px);
    return new float[]{(float)(px+w*ax+y*az-z*ay+t[0]),
      (float)(py+w*ay+z*ax-x*az+t[1]),
      (float)(pz+w*az+x*ay-y*ax+t[2])};
  }
}
