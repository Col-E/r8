// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package desugaringwithandroidjar25;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

public class DefaultMethodInAndroidJar25 {
  public static void main(String[] args) throws Exception {
    ClassWithDefaultPlatformMethods.test();
  }
}

class ClassWithDefaultPlatformMethods implements Collection<String> {
  private final ArrayList<String> list =
      new ArrayList<String>() {{
        add("First");
        add("Second");
      }};

  static void test() {
    ClassWithDefaultPlatformMethods instance = new ClassWithDefaultPlatformMethods();
    instance.forEach(x -> System.out.println("BEFORE: " + x));
    instance.removeIf(x -> true);
    instance.forEach(x -> System.out.println("AFTER: " + x));
  }

  @Override
  public int size() {
    throw new AssertionError();
  }

  @Override
  public boolean isEmpty() {
    throw new AssertionError();
  }

  @Override
  public boolean contains(Object o) {
    throw new AssertionError();
  }

  @Override
  public Iterator<String> iterator() {
    return list.iterator();
  }

  @Override
  public Object[] toArray() {
    throw new AssertionError();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    throw new AssertionError();
  }

  @Override
  public boolean add(String s) {
    throw new AssertionError();
  }

  @Override
  public boolean remove(Object o) {
    return list.remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new AssertionError();
  }

  @Override
  public boolean addAll(Collection<? extends String> c) {
    throw new AssertionError();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new AssertionError();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new AssertionError();
  }

  @Override
  public void clear() {
    throw new AssertionError();
  }

  @Override
  public boolean removeIf(Predicate<? super String> filter) {
    return Collection.super.removeIf(filter);
  }
}
