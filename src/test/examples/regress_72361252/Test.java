// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package regress_72361252;

/**
 * AOSP JFuzz Tester.
 * Automatically generated program.
 * jfuzz -s 3131542579 -d 1 -l 8 -i 2 -n 3 (version 1.5)
 */

import java.util.Arrays;

public class Test {

  private interface X {
    int x();
  }

  private class A {
    public int a() {
      return (Math.subtractExact(mI, (++mI)));
    }
  }

  private class B extends A implements X {
    public int a() {
      return super.a() + (Integer.MAX_VALUE);
    }
    public int x() {
      return (mI & mI);
    }
  }

  private static class C implements X {
    public static int s() {
      return 485062193;
    }
    public int c() {
      return -1161609244;
    }
    public int x() {
      return -210091649;
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

  private float[][][][][][][][][] mArray = new float[1][1][1][1][1][1][1][1][1];

  private Test() {
    float a = 1786185107.0f;
    for (int i0 = 0; i0 < 1; i0++) {
      for (int i1 = 0; i1 < 1; i1++) {
        for (int i2 = 0; i2 < 1; i2++) {
          for (int i3 = 0; i3 < 1; i3++) {
            for (int i4 = 0; i4 < 1; i4++) {
              for (int i5 = 0; i5 < 1; i5++) {
                for (int i6 = 0; i6 < 1; i6++) {
                  for (int i7 = 0; i7 < 1; i7++) {
                    for (int i8 = 0; i8 < 1; i8++) {
                      mArray[i0][i1][i2][i3][i4][i5][i6][i7][i8] = a;
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

  private double testMethod() {
    mJ /= ( mI);
    mD *= (--mD);
    {
      long lJ0 = ((Math.incrementExact(mJ)) / ((long) (mF - -356872921.0f)));
      for (int i0 = 0; i0 < 1; i0++) {
        for (int i1 = 0; i1 < 1; i1++) {
          mF += (mC.s());
          lJ0 += ((long)(int)(long) -1924533530L);
          mD = ((double) new Double((Double.POSITIVE_INFINITY)));
          mD = ((double) (49956622.0f / 1285981813.0f));
          continue;
        }
        for (int i1 = 0; i1 < 1; i1++) {
          if ((mZ)) {
            mI >>= (- (mI & -1283058927));
            {
              int i2 = -1;
              while (++i2 < 1) {
                try {
                  mD *= ((double) (++mArray[i1][0][i0][0][0][i0][i0][0][0]));
                  mD -= ((this instanceof Test) ? 110559916.0 : mD);
                  lJ0 -= (- (+ 417176163L));
                  lJ0 /= (+ -491731430L);
                  mI /= (-651651736 * ((int)(byte)(int) mI));
                } finally {
                  {
                    float[][][][][][][][][] tmp = {
                      {
                        {
                          {
                            {
                              {
                                {
                                  {
                                    { (mF * ((Math.signum(498770569.0f)) / mArray[i1][0][0][i1][i2][0][0][0][0])), },
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
              }
            }
            try {
              mZ |= ((false ? false : true) | mZ);
            } catch (IllegalStateException ex1_0) {
              lJ0 >>= (Long.MAX_VALUE);
            } catch (NullPointerException ex1_1) {
              mJ = (Long.MAX_VALUE);
              {
                int i2 = 0;
                do {
                  mArray[0][0][0][i0][0][0][i2][0][0] -= ((float) new Float((mArray[0][mArray.length - 1][0][i2][0][i0][0][i0][i1] / (++mF))));
                } while (++i2 < 1);
              }
            }
            switch (i0) {
              case 0: {
                mArray[mArray.length - 1][0][0][0][0][0][0][i0][i1] *= ((2005931898.0f >= -170622901.0f) ? 1714576973.0f : (+ ((true ? mF : (++mF)) / 1116904717.0f)));
                break;
              }
              default: {
                try {
                  mD /= (mD++);
                } catch (IllegalStateException ex1_0) {
                  nop();
                } catch (NullPointerException ex1_1) {
                  mJ += (Long.numberOfLeadingZeros(686016417L));
                  mJ %= (++lJ0);
                } catch (IllegalArgumentException ex1_2) {
                  lJ0 |= (--mJ);
                } catch (ArrayIndexOutOfBoundsException ex1_3) {
                  nop();
                } finally {
                  {
                    float[][][][][][][][][] tmp = {
                      {
                        {
                          {
                            {
                              {
                                {
                                  {
                                    { (mF - mF), },
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
                  try {
                    mD /= (Math.nextUp(mD));
                    {
                      int i2 = 0;
                      do {
                        lJ0 %= ((mZ) ? mJ : (- -1060440504L));
                      } while (++i2 < 1);
                    }
                    mArray[mArray.length - 1][0][i0][0][mArray.length - 1][0][i0][0][0] *= ((float) (~ (mI++)));
                  } finally {
                    try {
                      mJ *= (--lJ0);
                    } catch (IllegalStateException ex3_0) {
                      for (int i2 = 1 - 1; i2 >= 0; i2--) {
                        mI %= (~ (~ 2111805872));
                      }
                    } finally {
                      lJ0 = ((Long.MAX_VALUE) + ((1268602383L >>> -366323683L) / 2001325153L));
                    }
                  }
                }
                break;
              }
            }
          } else {
            mF = (++mF);
          }
          if ((((mZ) & true) & true)) {
            try {
              lJ0 |= (+ mJ);
            } catch (IllegalStateException ex1_0) {
              lJ0 >>>= (- 34814636L);
            } catch (NullPointerException ex1_1) {
              if (((boolean) new Boolean((1137859375L < -1146618420L)))) {
                {
                  float[][][][][][][][][] tmp = {
                    {
                      {
                        {
                          {
                            {
                              {
                                {
                                  { ((float) new Float(mF)), },
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
              } else {
                mF -= ((float) new Float(mF));
              }
            } finally {
              {
                float[][][][][][][][][] tmp = {
                  {
                    {
                      {
                        {
                          {
                            {
                              {
                                { (mA.a()), },
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
          } else {
            switch (i0) {
              case 0: {
                lJ0 -= (- mJ);
                break;
              }
              default: {
                mD += (((double) new Double(mD)) * ((double) new Double(mD)));
                lJ0 <<= ((! (true & mZ)) ? -482511807L : lJ0);
                break;
              }
            }
          }
        }
      }
    }
    return ((-663287294.0 - 970965601.0) + mD);
  }

  public static void nop() {}

  public static void main(String[] args) {
    Test t = new Test();
    double r = -437780077.0;
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
