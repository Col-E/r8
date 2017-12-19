// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package regress_70736958;

/**
 * AOSP JFuzz Tester.
 * Automatically generated program.
 * jfuzz -s 3792697346 -d 1 -l 8 -i 2 -n 3 (version 1.5)
 */

import java.util.Arrays;

public class Test {

  private interface X {
    int x();
  }

  private class A {
    public int a() {
      return ((((-777349935.0f * mF) <= -1505587386.0f) ^ (! (Float.isNaN((558732603.0f / mF))))) ? 128574411 : 82746151);
    }
  }

  private class B extends A implements X {
    public int a() {
      return super.a() + (mI - 1243874548);
    }
    public int x() {
      return (-712442496 >>> (++mI));
    }
  }

  private static class C implements X {
    public static int s() {
      return -961107044;
    }
    public int c() {
      return -1615767222;
    }
    public int x() {
      return -29536188;
    }
  }

  private A mA  = new B();
  private B mB  = new B();
  private X mBX = new B();
  private C mC  = new C();
  private X mCX = new C();

  private boolean mZ = false;
  private int     mI = 0;
  private long    mJ = 0;
  private float   mF = 0;
  private double  mD = 0;

  private float[] mArray = new float[504];

  private Test() {
    float a = 1610188871.0f;
    for (int i0 = 0; i0 < 504; i0++) {
      mArray[i0] = a;
      a++;
    }
  }

  private boolean testMethod() {
    mZ &= (-1969208943.0 >= (+ 498665650.0));
    mZ = (mZ);
    mJ = ((mBX.x()) * 522781990L);
    switch (360) {
      case 180: {
        mJ &= (mJ++);
        mZ &= (this instanceof Test);
        {
          boolean lZ0 = (((false & mZ) | true) & false);
          switch (96) {
            case 286: {
              {
                double lD0 = (Double.MAX_VALUE);
                for (int i0 = 0; i0 < 504; i0++) {
                  mZ &= (false ? (false && lZ0) : (((boolean) new Boolean(false)) | false));
                  {
                    float lF0 = ((mF - ((float) mI)) - 262618740.0f);
                    try {
                      if ((((boolean) new Boolean((false && mZ))) || false)) {
                        nop();
                      } else {
                        lZ0 = (Double.isNaN(lD0));
                      }
                      mZ = (this instanceof Test);
                      mJ /= (mJ | 513583530L);
                    } catch (IllegalStateException ex1_0) {
                      switch (354) {
                        case 321: {
                          mJ >>= ((2056946905L * -613355021L) + mJ);
                          break;
                        }
                        default: {
                          mJ %= (Long.MAX_VALUE);
                          break;
                        }
                      }
                    }
                  }
                  mZ |= ((boolean) new Boolean(mZ));
                  {
                    int i1 = 0;
                    do {
                      mF *= ((float) (1043949055.0 - (lZ0 ? 1106604806.0 : (++mD))));
                      mJ += (++mJ);
                      {
                        float lF0 = (-370708909.0f * ((float) new Float(mArray[254])));
                        {
                          int i2 = -1;
                          while (++i2 < 168) {
                            mZ ^= (this instanceof Test);
                          }
                        }
                        nop();
                      }
                    } while (++i1 < 504);
                  }
                  switch (41) {
                    case 20: {
                      mJ >>= (((long)(byte)(long) -1193665801L) - (mZ ? mJ : mJ));
                      break;
                    }
                    default: {
                      mF -= (++mF);
                      {
                        int lI0 = (979295258 / mI);
                        {
                          float lF0 = (--mF);
                          mJ &= ((long)(byte)(long) (--mJ));
                        }
                      }
                      break;
                    }
                  }
                }
                mI /= (true ? ((int) new Integer(1642824941)) : ((mC.s()) / -1663260388));
              }
              break;
            }
            default: {
              mD = ((double) new Double(791991927.0));
              break;
            }
          }
        }
        break;
      }
      default: {
        nop();
        break;
      }
    }
    return ((false ? false : ((false & false) | true)) ^ false);
  }

  public static void nop() {}

  public static void main(String[] args) {
    Test t = new Test();
    boolean r = true;
    try {
      r = t.testMethod();
    } catch (Exception e) {
      // Arithmetic, null pointer, index out of bounds, etc.
      System.out.println("An exception was caught.");
    }
    System.out.println("r  = " + r);
    System.out.println("mZ = " + t.mZ);
    System.out.println("mI = " + t.mI);
    System.out.println("mJ = " + t.mJ);
    System.out.println("mF = " + t.mF);
    System.out.println("mD = " + t.mD);
    System.out.println("mArray = " + Arrays.toString(t.mArray));
  }
}

