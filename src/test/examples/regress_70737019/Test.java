// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package regress_70737019;

/**
 * AOSP JFuzz Tester.
 * Automatically generated program.
 * jfuzz -s 3977136521 -d 1 -l 8 -i 2 -n 3 (version 1.5)
 */

import java.util.Arrays;

public class Test {

  private interface X {
    int x();
  }

  private class A {
    public int a() {
      return (- mI);
    }
  }

  private class B extends A implements X {
    public int a() {
      return super.a() + (-1812493140 % (false ? mI : ((int) 892127008.0)));
    }
    public int x() {
      return (-886126407 / -338904521);
    }
  }

  private static class C implements X {
    public static int s() {
      return -1340353735;
    }
    public int c() {
      return 800960755;
    }
    public int x() {
      return 626365654;
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

  private float[][][][][][][][][][] mArray = new float[1][1][1][1][1][1][1][1][1][1];

  private Test() {
    float a = 107750002.0f;
    for (int i0 = 0; i0 < 1; i0++) {
      for (int i1 = 0; i1 < 1; i1++) {
        for (int i2 = 0; i2 < 1; i2++) {
          for (int i3 = 0; i3 < 1; i3++) {
            for (int i4 = 0; i4 < 1; i4++) {
              for (int i5 = 0; i5 < 1; i5++) {
                for (int i6 = 0; i6 < 1; i6++) {
                  for (int i7 = 0; i7 < 1; i7++) {
                    for (int i8 = 0; i8 < 1; i8++) {
                      for (int i9 = 0; i9 < 1; i9++) {
                        mArray[i0][i1][i2][i3][i4][i5][i6][i7][i8][i9] = a;
                        a++;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private float testMethod() {
    {
      float lF0 = (+ mF);
      for (int i0 = 0; i0 < 1; i0++) {
        try {
          mZ &= (((((boolean) new Boolean(false)) & mZ) || (mZ)) ? false : (Boolean.logicalAnd(true, (! mZ))));
          mD += (mD++);
          switch (i0) {
            case 0: {
              mI &= ((int) new Integer((+ 730703786)));
              mD *= (- -1273988875.0);
              mF /= ((Float.NaN) - (Float.MIN_VALUE));
              break;
            }
            default: {
              if ((mD == -1148380176.0)) {
                mI += (mB.x());
                mJ ^= ((long) ((double) new Double(mD)));
              } else {
                {
                  float[][][][][][][][][][] tmp = {
                      {
                          {
                              {
                                  {
                                      {
                                          {
                                              {
                                                  {
                                                      { ((float) (+ (mC.x()))), },
                                                  },
                                              },
                                          },
                                      },
                                  },
                              },
                          },
                      },
                  };
                  mArray = tmp;
                }
              }
              mZ &= ((mI != 820389954) ? mZ : (this instanceof Test));
              break;
            }
          }
        } catch (IllegalStateException ex1_0) {
          mD *= ((double) new Double(26690240.0));
          mJ = (~ mJ);
          mD -= (((Double.POSITIVE_INFINITY) - -1767219627.0) * (--mD));
          mZ |= (Float.isFinite(mF));
        } catch (NullPointerException ex1_1) {
          mZ ^= (! mZ);
        } catch (IllegalArgumentException ex1_2) {
          {
            float[][][][][][][][][][] tmp = {
                {
                    {
                        {
                            {
                                {
                                    {
                                        {
                                            {
                                                { ((mB.a()) * ((float) new Float(-566421292.0f))), },
                                            },
                                        },
                                    },
                                },
                            },
                        },
                    },
                },
            };
            mArray = tmp;
          }
          mI %= (~ 1326172655);
          switch (i0) {
            case 0: {
              mZ |= (true | ((Boolean.logicalAnd(mZ, (this instanceof Test))) && mZ));
              if ((((boolean) new Boolean(true)) || (((boolean) new Boolean((false | (! mZ)))) | (! true)))) {
                mI /= ((int)(byte)(int) (((mI / mI) >> 265234301) | 188234363));
                {
                  int i1 = 0;
                  do {
                    mF += ((float) (Math.floorMod(mI, (Integer.MIN_VALUE))));
                    mJ >>= ((long) new Long(mJ));
                    mD = (mC.s());
                  } while (++i1 < 1);
                }
              } else {
                for (int i1 = 0; i1 < 1; i1++) {
                  switch (i0) {
                    case 0: {
                      mZ |= ((mZ ^ false) && (mZ ^ (mZ)));
                      break;
                    }
                    default: {
                      mJ &= (--mJ);
                      break;
                    }
                  }
                }
              }
              mZ |= (this instanceof Test);
              break;
            }
            default: {
              mJ %= (1070884400L ^ (mBX.x()));
              break;
            }
          }
        } finally {
          {
            boolean lZ0 = (mZ | true);
            lZ0 = (mZ);
          }
        }
      }
    }
    for (int i0 = 0; i0 < 1; i0++) {
      mZ = (Float.isInfinite((mF - (mF++))));
      try {
        nop();
      } catch (IllegalStateException ex1_0) {
        mD += (--mD);
        break;
      } catch (NullPointerException ex1_1) {
        mD = (((double)(float)(double) -712276876.0) * (- mD));
        mJ /= (Long.MIN_VALUE);
      } finally {
        {
          int lI0 = (mI++);
          mZ &= ((true | mZ) && mZ);
        }
      }
    }
    return (mF--);
  }

  public static void nop() {}

  public static void main(String[] args) {
    Test t = new Test();
    float r = 1457261414.0f;
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
    System.out.println("mArray = " + Arrays.deepToString(t.mArray));
  }
}
