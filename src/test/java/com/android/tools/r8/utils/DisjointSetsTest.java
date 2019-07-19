// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class DisjointSetsTest {

  private DisjointSets<Integer> initTestSet(int size) {
    DisjointSets<Integer> ds = new DisjointSets<>();
    for (int i = 0; i < size; i++) {
      ds.makeSet(i);
    }
    return ds;
  }

  public void oddEvenJoin(int how, DisjointSets<Integer> ds, int size) {
    if (how == 0 || how == 1) {
      for (int i = 2; i < size; i++) {
        if (i % 2 == 0) {
          if (how == 0) {
            ds.union(ds.findSet(0), ds.findSet(i));
          } else {
            ds.union(ds.findSet(i), ds.findSet(0));
          }
        } else {
          if (how == 0) {
            ds.union(ds.findSet(1), ds.findSet(i));
          } else {
            ds.union(ds.findSet(i), ds.findSet(1));
          }
        }
      }
    } else {
      assert how == 2 || how == 3;
      for (int i = size - 1; i >= 2; i--) {
        if (i % 2 == 0) {
          if (how == 2) {
            ds.union(ds.findSet(0), ds.findSet(i));
          } else {
            ds.union(ds.findSet(i), ds.findSet(0));
          }
        } else {
          if (how == 2) {
            ds.union(ds.findSet(1), ds.findSet(i));
          } else {
            ds.union(ds.findSet(i), ds.findSet(1));
          }
        }
      }
    }
  }

  public void runOddEvenTest(int how, int size) {
    DisjointSets<Integer> ds = initTestSet(size);

    assertEquals(size, ds.collectSets().size());

    oddEvenJoin(how, ds, size);

    Map<Integer, Set<Integer>> sets = ds.collectSets();
    assertEquals(2, sets.size());
    int elements = 0;
    for (Integer representative : sets.keySet()) {
      int oddOrEven = representative % 2;
      Set<Integer> set = sets.get(representative);
      set.forEach(s -> assertEquals(oddOrEven, s % 2));
      elements += set.size();
    }
    assertEquals(size, elements);

    for (int i = 2; i < size; i++) {
      Set<Integer> set = ds.collectSet(i);
      if (i % 2 == 0) {
        set.forEach(s -> assertEquals(0, s % 2));
        assertEquals(size / 2 + size % 2, set.size());
      } else {
        set.forEach(s -> assertEquals(1, s % 2));
        assertEquals(size / 2, set.size());
      }
    }
    assertEquals(size, ds.collectSet(0).size() + ds.collectSet(1).size());

    int count = 0;
    for (int i = 0; i < size; i++) {
      count += ds.isRepresentativeOrNotPresent(i) ? 1 : 0;
    }
    assertEquals(2, count);
    for (int i = -2; i < 0; i++) {
      count += ds.isRepresentativeOrNotPresent(i) ? 1 : 0;
    }
    for (int i = size; i < size + 2; i++) {
      count += ds.isRepresentativeOrNotPresent(i) ? 1 : 0;
    }
    assertEquals(6, count);

    ds.union(ds.findSet(size - 2), ds.findSet(size - 1));
    assertEquals(1, ds.collectSets().size());
  }

  public void runOddEvenTest(int size) {
    for (int how = 0; how < 4; how++) {
      runOddEvenTest(how, size);
    }
  }

  @Test
  public void testOddEven() {
    runOddEvenTest(2);
    runOddEvenTest(3);
    runOddEvenTest(4);
    runOddEvenTest(10);
    runOddEvenTest(100);
    runOddEvenTest(1000);
  }

  public void runUnionAllTest(int size) {
    DisjointSets<Integer> ds = initTestSet(size);
    for (int i = 1; i < size; i++) {
      ds.union(ds.findSet(i - 1), ds.findSet(i));
      assertEquals(size - i, ds.collectSets().size());
    }
  }

  @Test
  public void unionAllTest() {
    runUnionAllTest(2);
    runUnionAllTest(3);
    runUnionAllTest(4);
    runUnionAllTest(10);
    runUnionAllTest(100);
    runUnionAllTest(1000);
  }
}
