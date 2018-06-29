// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b77496850;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class B77496850 extends TestBase {

  static final String LOGTAG = "";

  // Mock class for the code in PathParser below.
  public static class Path {
    void rLineTo(int x, int y) {
    }
    void cubicTo(float a, float b, float c, float d, float e, float f) {
    }
  }

  // Mock class for the code in PathParser below.
  public static class Log {
    static void w(String x, String y) {
    }
  }

  // Code copied from Android support library:
  // https://android.googlesource.com/platform/frameworks/support/+/9791ac540f94c318f6602123d7000bfc55909b81/compat/src/main/java/android/support/v4/graphics/PathParser.java
  public static class PathParser {

    private static void drawArc(Path p,
        float x0,
        float y0,
        float x1,
        float y1,
        float a,
        float b,
        float theta,
        boolean isMoreThanHalf,
        boolean isPositiveArc) {
      /* Convert rotation angle from degrees to radians */
      double thetaD = Math.toRadians(theta);
      /* Pre-compute rotation matrix entries */
      double cosTheta = Math.cos(thetaD);
      double sinTheta = Math.sin(thetaD);
      /* Transform (x0, y0) and (x1, y1) into unit space */
      /* using (inverse) rotation, followed by (inverse) scale */
      double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
      double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
      double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
      double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;
      /* Compute differences and averages */
      double dx = x0p - x1p;
      double dy = y0p - y1p;
      double xm = (x0p + x1p) / 2;
      double ym = (y0p + y1p) / 2;
      /* Solve for intersecting unit circles */
      double dsq = dx * dx + dy * dy;
      if (dsq == 0.0) {
        Log.w(LOGTAG, " Points are coincident");
        return; /* Points are coincident */
      }
      double disc = 1.0 / dsq - 1.0 / 4.0;
      if (disc < 0.0) {
        Log.w(LOGTAG, "Points are too far apart " + dsq);
        float adjust = (float) (Math.sqrt(dsq) / 1.99999);
        drawArc(p, x0, y0, x1, y1, a * adjust,
            b * adjust, theta, isMoreThanHalf, isPositiveArc);
        return; /* Points are too far apart */
      }
      double s = Math.sqrt(disc);
      double sdx = s * dx;
      double sdy = s * dy;
      double cx;
      double cy;
      if (isMoreThanHalf == isPositiveArc) {
        cx = xm - sdy;
        cy = ym + sdx;
      } else {
        cx = xm + sdy;
        cy = ym - sdx;
      }
      double eta0 = Math.atan2((y0p - cy), (x0p - cx));
      double eta1 = Math.atan2((y1p - cy), (x1p - cx));
      double sweep = (eta1 - eta0);
      if (isPositiveArc != (sweep >= 0)) {
        if (sweep > 0) {
          sweep -= 2 * Math.PI;
        } else {
          sweep += 2 * Math.PI;
        }
      }
      cx *= a;
      cy *= b;
      double tcx = cx;
      cx = cx * cosTheta - cy * sinTheta;
      cy = tcx * sinTheta + cy * cosTheta;
      arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
    }

    /**
     * Converts an arc to cubic Bezier segments and records them in p.
     *
     * @param p     The target for the cubic Bezier segments
     * @param cx    The x coordinate center of the ellipse
     * @param cy    The y coordinate center of the ellipse
     * @param a     The radius of the ellipse in the horizontal direction
     * @param b     The radius of the ellipse in the vertical direction
     * @param e1x   E(eta1) x coordinate of the starting point of the arc
     * @param e1y   E(eta2) y coordinate of the starting point of the arc
     * @param theta The angle that the ellipse bounding rectangle makes with horizontal plane
     * @param start The start angle of the arc on the ellipse
     * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
     */
    private static void arcToBezier(Path p,
        double cx,
        double cy,
        double a,
        double b,
        double e1x,
        double e1y,
        double theta,
        double start,
        double sweep) {
      // Taken from equations at: http://spaceroots.org/documents/ellipse/node8.html
      // and http://www.spaceroots.org/documents/ellipse/node22.html
      // Maximum of 45 degrees per cubic Bezier segment
      int numSegments = (int) Math.ceil(Math.abs(sweep * 4 / Math.PI));
      double eta1 = start;
      double cosTheta = Math.cos(theta);
      double sinTheta = Math.sin(theta);
      double cosEta1 = Math.cos(eta1);
      double sinEta1 = Math.sin(eta1);
      double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
      double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);
      double anglePerSegment = sweep / numSegments;
      for (int i = 0; i < numSegments; i++) {
        double eta2 = eta1 + anglePerSegment;
        double sinEta2 = Math.sin(eta2);
        double cosEta2 = Math.cos(eta2);
        double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
        double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
        double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
        double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
        double tanDiff2 = Math.tan((eta2 - eta1) / 2);
        double alpha =
            Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
        double q1x = e1x + alpha * ep1x;
        double q1y = e1y + alpha * ep1y;
        double q2x = e2x - alpha * ep2x;
        double q2y = e2y - alpha * ep2y;
        // Adding this no-op call to workaround a proguard related issue.
        p.rLineTo(0, 0);
        p.cubicTo((float) q1x,
            (float) q1y,
            (float) q2x,
            (float) q2y,
            (float) e2x,
            (float) e2y);
        eta1 = eta2;
        e1x = e2x;
        e1y = e2y;
        ep1x = ep2x;
        ep1y = ep2y;
      }
    }
  }

  // Same code as PathParser above, but with exception handlers in the two methods.
  public static class PathParserWithExceptionHandler {

    private static void drawArc(Path p,
        float x0,
        float y0,
        float x1,
        float y1,
        float a,
        float b,
        float theta,
        boolean isMoreThanHalf,
        boolean isPositiveArc) {
      try {
        /* Convert rotation angle from degrees to radians */
        double thetaD = Math.toRadians(theta);
        /* Pre-compute rotation matrix entries */
        double cosTheta = Math.cos(thetaD);
        double sinTheta = Math.sin(thetaD);
        /* Transform (x0, y0) and (x1, y1) into unit space */
        /* using (inverse) rotation, followed by (inverse) scale */
        double x0p = (x0 * cosTheta + y0 * sinTheta) / a;
        double y0p = (-x0 * sinTheta + y0 * cosTheta) / b;
        double x1p = (x1 * cosTheta + y1 * sinTheta) / a;
        double y1p = (-x1 * sinTheta + y1 * cosTheta) / b;
        /* Compute differences and averages */
        double dx = x0p - x1p;
        double dy = y0p - y1p;
        double xm = (x0p + x1p) / 2;
        double ym = (y0p + y1p) / 2;
        /* Solve for intersecting unit circles */
        double dsq = dx * dx + dy * dy;
        if (dsq == 0.0) {
          Log.w(LOGTAG, " Points are coincident");
          return; /* Points are coincident */
        }
        double disc = 1.0 / dsq - 1.0 / 4.0;
        if (disc < 0.0) {
          Log.w(LOGTAG, "Points are too far apart " + dsq);
          float adjust = (float) (Math.sqrt(dsq) / 1.99999);
          drawArc(p, x0, y0, x1, y1, a * adjust,
              b * adjust, theta, isMoreThanHalf, isPositiveArc);
          return; /* Points are too far apart */
        }
        double s = Math.sqrt(disc);
        double sdx = s * dx;
        double sdy = s * dy;
        double cx;
        double cy;
        if (isMoreThanHalf == isPositiveArc) {
          cx = xm - sdy;
          cy = ym + sdx;
        } else {
          cx = xm + sdy;
          cy = ym - sdx;
        }
        double eta0 = Math.atan2((y0p - cy), (x0p - cx));
        double eta1 = Math.atan2((y1p - cy), (x1p - cx));
        double sweep = (eta1 - eta0);
        if (isPositiveArc != (sweep >= 0)) {
          if (sweep > 0) {
            sweep -= 2 * Math.PI;
          } else {
            sweep += 2 * Math.PI;
          }
        }
        cx *= a;
        cy *= b;
        double tcx = cx;
        cx = cx * cosTheta - cy * sinTheta;
        cy = tcx * sinTheta + cy * cosTheta;
        arcToBezier(p, cx, cy, a, b, x0, y0, thetaD, eta0, sweep);
      } catch (Throwable t) {
        // Ignore.
      }
    }

    /**
     * Converts an arc to cubic Bezier segments and records them in p.
     *
     * @param p     The target for the cubic Bezier segments
     * @param cx    The x coordinate center of the ellipse
     * @param cy    The y coordinate center of the ellipse
     * @param a     The radius of the ellipse in the horizontal direction
     * @param b     The radius of the ellipse in the vertical direction
     * @param e1x   E(eta1) x coordinate of the starting point of the arc
     * @param e1y   E(eta2) y coordinate of the starting point of the arc
     * @param theta The angle that the ellipse bounding rectangle makes with horizontal plane
     * @param start The start angle of the arc on the ellipse
     * @param sweep The angle (positive or negative) of the sweep of the arc on the ellipse
     */
    private static void arcToBezier(Path p,
        double cx,
        double cy,
        double a,
        double b,
        double e1x,
        double e1y,
        double theta,
        double start,
        double sweep) {
      try {
        // Taken from equations at: http://spaceroots.org/documents/ellipse/node8.html
        // and http://www.spaceroots.org/documents/ellipse/node22.html
        // Maximum of 45 degrees per cubic Bezier segment
        int numSegments = (int) Math.ceil(Math.abs(sweep * 4 / Math.PI));
        double eta1 = start;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);
        double cosEta1 = Math.cos(eta1);
        double sinEta1 = Math.sin(eta1);
        double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
        double ep1y = (-a * sinTheta * sinEta1) + (b * cosTheta * cosEta1);
        double anglePerSegment = sweep / numSegments;
        for (int i = 0; i < numSegments; i++) {
          double eta2 = eta1 + anglePerSegment;
          double sinEta2 = Math.sin(eta2);
          double cosEta2 = Math.cos(eta2);
          double e2x = cx + (a * cosTheta * cosEta2) - (b * sinTheta * sinEta2);
          double e2y = cy + (a * sinTheta * cosEta2) + (b * cosTheta * sinEta2);
          double ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2;
          double ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2;
          double tanDiff2 = Math.tan((eta2 - eta1) / 2);
          double alpha =
              Math.sin(eta2 - eta1) * (Math.sqrt(4 + (3 * tanDiff2 * tanDiff2)) - 1) / 3;
          double q1x = e1x + alpha * ep1x;
          double q1y = e1y + alpha * ep1y;
          double q2x = e2x - alpha * ep2x;
          double q2y = e2y - alpha * ep2y;
          // Adding this no-op call to workaround a proguard related issue.
          p.rLineTo(0, 0);
          p.cubicTo((float) q1x,
              (float) q1y,
              (float) q2x,
              (float) q2y,
              (float) e2x,
              (float) e2y);
          eta1 = eta2;
          e1x = e2x;
          e1y = e2y;
          ep1x = ep2x;
          ep1y = ep2y;
        }
      } catch (Throwable t) {
        // Ignore.
      }
    }

  }

  // Reproduction from b/77496850.
  public static class Reproduction {
    public int test() {
      int count = 0;
      for (int i = 0; i < 1000; i++){
        count += arcToBezier(1.0, 1.0, 2.0);
      }
      return count;
    }

    private static int arcToBezier(double a, double b, double sweep) {
      int count = 0;

      int numSegments = (int) sweep;

      double cosTheta = 0.5;
      double sinTheta = 0.5;
      double cosEta1 = 0.5;
      double sinEta1 = 0.5;
      double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
      double anglePerSegment = sweep / numSegments;

      for (int i = 0; i < numSegments; i++) {
        count++;
      }
      if (numSegments != count) {
        return 1;
      }
      return 0;
    }

    public static void main(String[] args) {
      for (int i = 0; i < 100; i++) {
        System.out.println(new Reproduction().test());
      }
    }
  }

  // Reproduction from b/77496850 with exception handler.
  public static class ReproductionWithExceptionHandler {
    public int test() {
      int count = 0;
      for (int i = 0; i < 1000; i++){
        count += arcToBezier(1.0, 1.0, 2.0);
      }
      return count;
    }

    private static int arcToBezier(double a, double b, double sweep) {
      try {
        int count = 0;

        int numSegments = (int) sweep;

        double cosTheta = 0.5;
        double sinTheta = 0.5;
        double cosEta1 = 0.5;
        double sinEta1 = 0.5;
        double ep1x = (-a * cosTheta * sinEta1) - (b * sinTheta * cosEta1);
        double anglePerSegment = sweep / numSegments;

        for (int i = 0; i < numSegments; i++) {
          count++;
        }
        if (numSegments != count) {
          return 1;
        }
        return 0;
      } catch (Throwable t) {
        return 1;
      }
    }

    public static void main(String[] args) {
      for (int i = 0; i < 100; i++) {
        System.out.println(new Reproduction().test());
      }
    }
  }

  private int countInvokeDoubleIsNan(DexItemFactory factory, DexCode code) {
    int count = 0;
    DexMethod doubleIsNaN = factory.createMethod(
        factory.createString("Ljava/lang/Double;"),
        factory.createString("isNaN"),
        factory.booleanDescriptor,
        new DexString[]{factory.doubleDescriptor});
    for (int i = 0; i < code.instructions.length; i++) {
      if (code.instructions[i] instanceof InvokeStatic) {
        InvokeStatic invoke = (InvokeStatic) code.instructions[i];
        if (invoke.getMethod() == doubleIsNaN) {
          count++;
        }
      }
    }
    return count;
  }

  private void checkPathParserMethods(AndroidApp app, Class testClass, int a, int b)
      throws Exception {
    DexInspector inspector = new DexInspector(app);
    DexItemFactory factory = inspector.getFactory();
    ClassSubject clazz = inspector.clazz(testClass);
    MethodSubject drawArc = clazz.method(
        "void",
        "drawArc",
        ImmutableList.of(
            getClass().getCanonicalName() + "$Path",
            "float", "float", "float", "float", "float", "float", "float", "boolean", "boolean"));
    MethodSubject arcToBezier = clazz.method(
        "void",
        "arcToBezier",
        ImmutableList.of(
            getClass().getCanonicalName() + "$Path",
            "double", "double", "double", "double", "double", "double",
            "double", "double", "double"));
    assertEquals(a, countInvokeDoubleIsNan(factory, drawArc.getMethod().getCode().asDexCode()));
    assertEquals(b, countInvokeDoubleIsNan(factory, arcToBezier.getMethod().getCode().asDexCode()));
  }

  private void runTestPathParser(
      Tool compiler, Class testClass, AndroidApiLevel apiLevel, int a, int b)
      throws Exception {
    AndroidApp app = readClasses(Path.class, Log.class, testClass);
    if (compiler == Tool.D8) {
      app = compileWithD8(app, o -> o.minApiLevel = apiLevel.getLevel());
    } else {
      assert compiler == Tool.R8;
      app = compileWithR8(app, "-keep class * { *; }", o -> o.minApiLevel = apiLevel.getLevel());
    }
    checkPathParserMethods(app, testClass, a, b);
  }

  @Test
  public void testSupportLibraryPathParser() throws Exception{
    for (Tool tool : Tool.values()) {
      runTestPathParser(tool, PathParser.class, AndroidApiLevel.K, 14, 1);
      runTestPathParser(tool, PathParser.class, AndroidApiLevel.L, 0, 0);
      runTestPathParser(tool, PathParserWithExceptionHandler.class, AndroidApiLevel.K, 14, 1);
      runTestPathParser(tool, PathParserWithExceptionHandler.class, AndroidApiLevel.L, 0, 0);
    }
  }

  private void runTestReproduction(
      Tool compiler, Class testClass, AndroidApiLevel apiLevel, int expectedInvokeDoubleIsNanCount)
      throws Exception {
    AndroidApp app = readClasses(testClass);
    if (compiler == Tool.D8) {
      app = compileWithD8(app, o -> o.minApiLevel = apiLevel.getLevel());
    } else {
      assert compiler == Tool.R8;
      app = compileWithR8(app, "-keep class * { *; }", o -> o.minApiLevel = apiLevel.getLevel());
    }
    DexInspector inspector = new DexInspector(app);
    DexItemFactory factory = inspector.getFactory();
    ClassSubject clazz = inspector.clazz(testClass);
    MethodSubject arcToBezier = clazz.method(
        "int", "arcToBezier", ImmutableList.of("double", "double", "double"));
    assertEquals(
      expectedInvokeDoubleIsNanCount,
      countInvokeDoubleIsNan(factory, arcToBezier.getMethod().getCode().asDexCode()));
  }

  @Test
  public void testReproduction() throws Exception{
    for (Tool tool : Tool.values()) {
      runTestReproduction(tool, Reproduction.class, AndroidApiLevel.K, tool == Tool.D8 ? 1 : 0);
      runTestReproduction(tool, Reproduction.class, AndroidApiLevel.L, 0);
      runTestReproduction(
          tool, ReproductionWithExceptionHandler.class, AndroidApiLevel.K, tool == Tool.D8 ? 1 : 0);
      runTestReproduction(tool, ReproductionWithExceptionHandler.class, AndroidApiLevel.L, 0);
    }
  }
}
