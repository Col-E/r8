// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.IntStream;

public class ProgramRewritingTestClass {

  // Each print to the console is immediately followed by the expected result so the tests
  // can assert the results by checking the lines 2 by 2.
  public static void main(String[] args) {
    Set<Object> set = new HashSet<>();
    List<Object> list = new ArrayList<>();
    ArrayList<Object> aList = new ArrayList<>();
    Queue<Object> queue = new LinkedList<>();
    LinkedHashSet<Object> lhs = new LinkedHashSet<>();
    // They both should be rewritten to invokeStatic to the dispatch class.
    System.out.println(set.spliterator().getClass().getName());
    System.out.println("j$.util.Spliterators$IteratorSpliterator");
    System.out.println(list.spliterator().getClass().getName());
    System.out.println("j$.util.Spliterators$IteratorSpliterator");
    // Following should be rewritten to invokeStatic to Collection dispatch class.
    System.out.println(set.stream().getClass().getName());
    System.out.println("j$.util.stream.ReferencePipeline$Head");
    // Following should not be rewritten.
    System.out.println(set.iterator().getClass().getName());
    System.out.println("java.util.HashMap$KeyIterator");
    // Following should be rewritten to invokeStatic to Collection dispatch class.
    System.out.println(queue.stream().getClass().getName());
    System.out.println("j$.util.stream.ReferencePipeline$Head");
    // Following should be rewritten as retarget core lib member.
    System.out.println(lhs.spliterator().getClass().getName());
    System.out.println("j$.util.Spliterators$IteratorSpliterator");
    // Remove follows the don't rewrite rule.
    list.add(new Object());
    Iterator iterator = list.iterator();
    iterator.next();
    iterator.remove();
    // Static methods (same name, different signatures).
    System.out.println(Arrays.spliterator(new Object[]{new Object()}).getClass().getName());
    System.out.println("j$.util.Spliterators$ArraySpliterator");
    System.out.println(Arrays.spliterator(new Object[]{new Object()}, 0, 0).getClass().getName());
    System.out.println("j$.util.Spliterators$ArraySpliterator");
    System.out.println(Arrays.stream(new Object[]{new Object()}).getClass().getName());
    System.out.println("j$.util.stream.ReferencePipeline$Head");
    System.out.println(Arrays.stream(new Object[]{new Object()}, 0, 0).getClass().getName());
    System.out.println("j$.util.stream.ReferencePipeline$Head");
    // Following should be rewritten to invokeStatic to dispatch class.
    System.out.println(list.stream().getClass().getName());
    System.out.println("j$.util.stream.ReferencePipeline$Head");
    // Following should call companion method (desugared library class).
    System.out.println(IntStream.range(0, 5).getClass().getName());
    System.out.println("j$.util.stream.IntPipeline$Head");
    // Following should call List dispatch (sort), rewritten from invoke interface.
    // Comparator.comparingInt should call companion method (desugared library class).
    Collections.addAll(list, new Object(), new Object());
    list.sort(Comparator.comparingInt(Object::hashCode));
    // Following  should call List dispatch (sort), rewritten from invoke virtual.
    // Comparator.comparingInt should call companion method (desugared library class).
    Collections.addAll(aList, new Object(), new Object());
    aList.sort(Comparator.comparingInt(Object::hashCode));
    // Following should be rewritten to invokeStatic to Collection dispatch class.
    System.out.println(list.stream().getClass().getName());
    System.out.println("j$.util.stream.ReferencePipeline$Head");
    // Following should call companion method (desugared library class) [Java 9].
    // System.out.println(Stream.iterate(0,x->x<10,x->x+1).getClass().getName());
  }
}
