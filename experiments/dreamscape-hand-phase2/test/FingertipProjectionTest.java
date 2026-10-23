package com.dreamscape.hand.math;
public final class FingertipProjectionTest {
  static void near(float a,double b){if(Math.abs(a-b)>1e-4)throw new AssertionError(a+" != "+b);}
  static void check(float[] a,double x,double y,double z){near(a[0],x);near(a[1],y);near(a[2],z);}
  public static void main(String[] args){
    check(FingertipProjection.cameraPoint(320,240,1,500,500,320,240),0,0,-1);
    check(FingertipProjection.cameraPoint(420,340,2,500,500,320,240),.4,-.4,-2);
    float[] p=FingertipProjection.cameraPoint(320,240,1,500,500,320,240);
    check(FingertipProjection.worldPoint(p,new float[]{0,0,0,1},new float[]{2,3,4}),2,3,3);
    float h=(float)Math.sin(Math.PI/4);
    check(FingertipProjection.worldPoint(p,new float[]{0,h,0,h},new float[]{0,0,0}),-1,0,0);
    boolean rejected=false;
    try{FingertipProjection.cameraPoint(0,0,-1,500,500,320,240);}catch(IllegalArgumentException e){rejected=true;}
    if(!rejected)throw new AssertionError("negative depth accepted");
    System.out.println("PASS: 5 projection cases");
  }
}
