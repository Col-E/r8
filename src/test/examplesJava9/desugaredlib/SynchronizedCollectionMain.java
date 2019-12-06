// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SynchronizedCollectionMain {

  public static void main(String[] args) {
    // Test 3 maps.
    // Test keySet entrySet values in syncMap
    // test navigableKeySet in syncNavigMap
    // Test rewriting of removeIf, replaceAll, sort.
    // Retarget removeIf.
    ArrayList<Integer> list = new ArrayList<>();
    list.add(1);
    list.add(2);
    Collection<Integer> syncCol = Collections.synchronizedCollection(list);
    syncCol.removeIf(x -> x == 2);
    System.out.println(syncCol);

    // Retarget sort, replaceAll.
    ArrayList<Integer> list2 = new ArrayList<>();
    list2.add(2);
    list2.add(1);
    List<Integer> syncList = Collections.synchronizedList(list2);
    syncList.replaceAll(x -> x + 1);
    System.out.println(syncList.size());
    syncList.sort(Comparator.naturalOrder());
    System.out.println(syncList);

    // Retarget synchronizedMap.
    Map<Integer, Double> map = new IdentityHashMap<>();
    map.put(1, 1.1);
    map.put(2, 2.2);
    Map<Integer, Double> synchronizedMap = Collections.synchronizedMap(map);
    // Reflective instantiation.
    System.out.println(synchronizedMap.keySet().contains(1));
    System.out.println(synchronizedMap.entrySet().size());
    System.out.println(synchronizedMap.values().size());

    // Retarget synchronizedSortedMap
    SortedMap<Integer, Double> sortedMap = new ConcurrentSkipListMap<>();
    sortedMap.put(1, 1.1);
    sortedMap.put(2, 2.2);
    SortedMap<Integer, Double> synchronizedSortedMap = Collections.synchronizedSortedMap(sortedMap);
    System.out.println(synchronizedSortedMap.size());

    testSynchronization();
  }

  public static void testSynchronization() {
    int LIST_SIZE = 10000;
    // Different thread mutate the same collection. Without synchronization,
    // some of the integers should be concurrently incremented leading to an invalid result.
    for (int numThreads : new int[] {4, 5, 8, 9, 15, 16}) {
      ArrayList<Integer> list = new ArrayList<>();
      for (int i = 0; i < LIST_SIZE; i++) {
        list.add(i);
      }
      List<Integer> syncList = Collections.synchronizedList(list);
      try {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
          executor.submit(() -> syncList.replaceAll(x -> x + 1));
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      for (int i = 0; i < LIST_SIZE; i++) {
        assert i + numThreads == list.get(i);
      }
    }
  }
}
