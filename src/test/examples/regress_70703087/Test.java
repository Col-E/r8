// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package regress_70703087;

/**
 * AOSP JFuzz Tester.
 * Automatically generated program.
 * jfuzz -s 67746824 -d 1 -l 8 -i 2 -n 3 (version 1.4)
 */

import java.util.Arrays;

public class Test {

  private interface X {
    int x();
  }

  private class A {
    public int a() {
      return ((-1855525611 | 1607049612) + 1045227917);
    }
  }

  private class B extends A implements X {
    public int a() {
      return super.a() + ((--mI) >>> mI);
    }
    public int x() {
      return (Math.negateExact((mI--)));
    }
  }

  private static class C implements X {
    public static int s() {
      return 772321235;
    }
    public int c() {
      return 1742917409;
    }
    public int x() {
      return -1360135619;
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

  private double[] mArray = new double[700];

  private Test() {
    double a = 1796354926.0;
    for (int i0 = 0; i0 < 700; i0++) {
      mArray[i0] = a;
      a++;
    }
  }

  private float testMethod() {
    {
      int i0 = -1;
      while (++i0 < 700) {
        mJ -= (mB.a());
        mArray[i0] = (mB.x());
        if ((-1926417383 < (((false || true) || true) ? 1210112326 : ((int) new Integer((Integer.MIN_VALUE)))))) {
          mI += (((int) new Integer((mI++))) % (mZ ? mI : -1938324616));
          for (int i1 = 0; i1 < 700; i1++) {
            mZ = ((boolean) new Boolean(mZ));
            mF += (Math.nextUp((++mF)));
            mI *= (Integer.MAX_VALUE);
            {
              int lI0 = (- mI);
              {
                int i2 = -1;
                while (++i2 < 700) {
                  mF -= (++mF);
                  switch (i0) {
                    case 427: {
                      switch (i1) {
                        case 684: {
                          {
                            int i3 = -1;
                            while (++i3 < mArray.length) {
                              continue;
                            }
                          }
                          break;
                        }
                        default: {
                          for (int i3 = 0; i3 < 700; i3++) {
                            mZ = (false | (true || (mZ)));
                            mJ >>= (mJ++);
                            mZ ^= ((boolean) new Boolean(mZ));
                          }
                          break;
                        }
                      }
                      mJ *= ((true || (mZ)) ? 612985119L : 2049818256L);
                      break;
                    }
                    default: {
                      mF += (Math.nextDown((++mF)));
                      break;
                    }
                  }
                }
              }
              if ((mZ)) {
                lI0 >>>= ((lI0 << 91989301) ^ -1039049984);
              } else {
                lI0 >>>= (false ? (Integer.MAX_VALUE) : ((int) mD));
              }
              switch (i0) {
                case 197: {
                  if ((false ^ true)) {
                    for (int i2 = 0; i2 < 700; i2++) {
                      mJ += (-676408943L << mJ);
                    }
                    mI /= ((1831995326 / mI) >>> -108611513);
                  } else {
                    mJ <<= (Math.multiplyExact(((~ mJ) - 1612907666L), -42299883L));
                    mI += (921426411 >>> (((boolean) new Boolean(mZ)) ? mI : -718350442));
                  }
                  break;
                }
                default: {
                  mZ ^= (! ((boolean) new Boolean((! false))));
                  break;
                }
              }
            }
            {
              int i2 = -1;              while (++i2 < 700) {
                mZ = (Boolean.logicalAnd(false, (mZ ^ (mZ ^ (mZ)))));
              }
            }
          }
        } else {
          {
            int lI0 = ((int) new Integer(-242659552));
            if ((1516210251.0 >= -69512179.0)) {
              lI0 = (mI--);
            } else {
              mD -= ((mArray[163]--) * ((mB.a()) / -1609350152.0));
            }
          }
        }
      }
    }
    return (mF - -1677280306.0f);
  }

  public static void main(String[] args) {
    Test t = new Test();
    float r = 1753285454.0f;
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
