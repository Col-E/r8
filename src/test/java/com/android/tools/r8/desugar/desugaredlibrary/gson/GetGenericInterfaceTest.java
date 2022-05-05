// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.gson;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import dalvik.system.PathClassLoader;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GetGenericInterfaceTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public GetGenericInterfaceTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testGeneric() throws Exception {
    String stdOut =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(GetGenericInterfaceTest.class)
            .addKeepMainRule(Executor.class)
            .compile()
            .run(parameters.getRuntime(), Executor.class)
            .assertSuccess()
            .getStdOut();
    assertValidInterfaces(stdOut);
  }

  private void assertValidInterfaces(String stdOut) {
    // The value of getGenericInterfaces has to be the value of getInterfaces with generic types.
    // Here are two examples:
    //  - class A implements I {}
    //    getInterfaces -> [interface I]
    //    getGenericInterfaces -> [interface I]
    //  - class B<E> implements J<E> {}
    //    getInterfaces -> [interface J]
    //    getGenericInterfaces -> [J<E>]
    // Both arrays have to be of the same size and each class has to be present in the same order.
    String[] lines = stdOut.split("\n");
    for (int i = 0; i < lines.length; i += 4) {
      String className = lines[i];
      String[] interfaces1 = lines[i + 1].split("(, com|, interface|, j)");
      String[] interfaces2 = lines[i + 2].split("(, com|, interface|, j)");
      assertEquals(
          "Invalid number of interfaces in "
              + className
              + "\n "
              + Arrays.toString(interfaces1)
              + "\n "
              + Arrays.toString(interfaces2),
          interfaces1.length,
          interfaces2.length);
      // Ignore the empty list of interface case.
      if (!interfaces1[0].equals("[]")) {
        for (int j = 0; j < interfaces1.length; j++) {
          String interfaceName = interfaces1[j].substring("interface ".length()).trim();
          while (interfaceName.charAt(interfaceName.length() - 1) == ']') {
            interfaceName = interfaceName.substring(0, interfaceName.length() - 2).trim();
          }
          assertTrue(
              "Invalid interface in " + className + "\n " + interfaces1[j] + "\n " + interfaces2[j],
              interfaces2[j].contains(interfaceName));
        }
      }
    }
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  static class Executor {

    // The output of the test is, in stdOut, composed of 4 lines entries:
    // line 1: class name
    // line 2: getInterfaces() for the class
    // line 3: getGenericInterfaces() for the class
    // line 4: empty.
    public static void main(String[] args) {
      mapTest();
      collectionTest();
      collectionMapTest();
      sqlDateTest();
    }

    private static void mapTest() {
      System.out.println(NullableConcurrentHashMapExtendDifferentLetters.class);
      System.out.println(
          Arrays.toString(NullableConcurrentHashMapExtendDifferentLetters.class.getInterfaces()));
      System.out.println(
          Arrays.toString(
              NullableConcurrentHashMapExtendDifferentLetters.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableConcurrentHashMapExtend.class);
      System.out.println(Arrays.toString(NullableConcurrentHashMapExtend.class.getInterfaces()));
      System.out.println(
          Arrays.toString(NullableConcurrentHashMapExtend.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableConcurrentHashMapExtendZ.class);
      System.out.println(Arrays.toString(NullableConcurrentHashMapExtendZ.class.getInterfaces()));
      System.out.println(
          Arrays.toString(NullableConcurrentHashMapExtendZ.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableConcurrentHashMapImplement.class);
      System.out.println(Arrays.toString(NullableConcurrentHashMapImplement.class.getInterfaces()));
      System.out.println(
          Arrays.toString(NullableConcurrentHashMapImplement.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableConcurrentHashMapImplementZ.class);
      System.out.println(
          Arrays.toString(NullableConcurrentHashMapImplementZ.class.getInterfaces()));
      System.out.println(
          Arrays.toString(NullableConcurrentHashMapImplementZ.class.getGenericInterfaces()));
      System.out.println();
    }

    private static void collectionTest() {
      System.out.println(NullableArrayListExtend.class);
      System.out.println(Arrays.toString(NullableArrayListExtend.class.getInterfaces()));
      System.out.println(Arrays.toString(NullableArrayListExtend.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableArrayListExtendZ.class);
      System.out.println(Arrays.toString(NullableArrayListExtendZ.class.getInterfaces()));
      System.out.println(Arrays.toString(NullableArrayListExtendZ.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableArrayListImplement.class);
      System.out.println(Arrays.toString(NullableArrayListImplement.class.getInterfaces()));
      System.out.println(Arrays.toString(NullableArrayListImplement.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(NullableArrayListImplementZ.class);
      System.out.println(Arrays.toString(NullableArrayListImplementZ.class.getInterfaces()));
      System.out.println(Arrays.toString(NullableArrayListImplementZ.class.getGenericInterfaces()));
      System.out.println();
    }

    private static void collectionMapTest() {
      System.out.println(CollectionMapImplements2.class);
      System.out.println(Arrays.toString(CollectionMapImplements2.class.getInterfaces()));
      System.out.println(Arrays.toString(CollectionMapImplements2.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(CollectionMapExtendImplement.class);
      System.out.println(Arrays.toString(CollectionMapExtendImplement.class.getInterfaces()));
      System.out.println(
          Arrays.toString(CollectionMapExtendImplement.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(CollectionMapImplements2Integer1.class);
      System.out.println(Arrays.toString(CollectionMapImplements2Integer1.class.getInterfaces()));
      System.out.println(
          Arrays.toString(CollectionMapImplements2Integer1.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(CollectionMapExtendImplementInteger1.class);
      System.out.println(
          Arrays.toString(CollectionMapExtendImplementInteger1.class.getInterfaces()));
      System.out.println(
          Arrays.toString(CollectionMapExtendImplementInteger1.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(CollectionMapImplements2Integer2.class);
      System.out.println(Arrays.toString(CollectionMapImplements2Integer2.class.getInterfaces()));
      System.out.println(
          Arrays.toString(CollectionMapImplements2Integer2.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(CollectionMapExtendImplementInteger2.class);
      System.out.println(
          Arrays.toString(CollectionMapExtendImplementInteger2.class.getInterfaces()));
      System.out.println(
          Arrays.toString(CollectionMapExtendImplementInteger2.class.getGenericInterfaces()));
      System.out.println();
    }

    private static void sqlDateTest() {
      System.out.println(MySQLDataException.class);
      System.out.println(Arrays.toString(MySQLDataException.class.getInterfaces()));
      System.out.println(Arrays.toString(MySQLDataException.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(MyDate.class);
      System.out.println(Arrays.toString(MyDate.class.getInterfaces()));
      System.out.println(Arrays.toString(MyDate.class.getGenericInterfaces()));
      System.out.println();

      System.out.println(MyDateZ.class);
      System.out.println(Arrays.toString(MyDateZ.class.getInterfaces()));
      System.out.println(Arrays.toString(MyDateZ.class.getGenericInterfaces()));
      System.out.println();
    }
  }

  interface MyInterface<Z> {
    void print(Z z);
  }

  static class NullableConcurrentHashMapExtendDifferentLetters<R, T>
      extends ConcurrentHashMap<R, T> {
    NullableConcurrentHashMapExtendDifferentLetters() {
      super();
    }
  }

  static class NullableConcurrentHashMapExtend<K, V> extends ConcurrentHashMap<K, V> {
    NullableConcurrentHashMapExtend() {
      super();
    }
  }

  static class NullableConcurrentHashMapImplement<K, V> implements ConcurrentMap<K, V> {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      return false;
    }

    @Override
    public boolean containsValue(Object o) {
      return false;
    }

    @Override
    public V get(Object o) {
      return null;
    }

    @Override
    public V put(K k, V v) {
      return null;
    }

    @Override
    public V remove(Object o) {
      return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {}

    @Override
    public void clear() {}

    @Override
    public Set<K> keySet() {
      return null;
    }

    @Override
    public Collection<V> values() {
      return null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return null;
    }

    @Override
    public V putIfAbsent(K k, V v) {
      return null;
    }

    @Override
    public boolean remove(Object o, Object o1) {
      return false;
    }

    @Override
    public boolean replace(K k, V v, V v1) {
      return false;
    }

    @Override
    public V replace(K k, V v) {
      return null;
    }
  }

  static class NullableConcurrentHashMapExtendZ<K, V> extends ConcurrentHashMap<K, V>
      implements MyInterface<K> {
    NullableConcurrentHashMapExtendZ() {
      super();
    }

    @Override
    public void print(K k) {}
  }

  static class NullableConcurrentHashMapImplementZ<K, V>
      implements ConcurrentMap<K, V>, MyInterface<K> {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      return false;
    }

    @Override
    public boolean containsValue(Object o) {
      return false;
    }

    @Override
    public V get(Object o) {
      return null;
    }

    @Override
    public V put(K k, V v) {
      return null;
    }

    @Override
    public V remove(Object o) {
      return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {}

    @Override
    public void clear() {}

    @Override
    public Set<K> keySet() {
      return null;
    }

    @Override
    public Collection<V> values() {
      return null;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return null;
    }

    @Override
    public V putIfAbsent(K k, V v) {
      return null;
    }

    @Override
    public boolean remove(Object o, Object o1) {
      return false;
    }

    @Override
    public boolean replace(K k, V v, V v1) {
      return false;
    }

    @Override
    public V replace(K k, V v) {
      return null;
    }

    @Override
    public void print(K k) {}
  }

  static class NullableArrayListExtend<E> extends ArrayList<E> {
    NullableArrayListExtend() {
      super();
    }
  }

  static class NullableArrayListImplement<E> implements List<E> {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return false;
    }

    @Override
    public Iterator<E> iterator() {
      return null;
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] ts) {
      return null;
    }

    @Override
    public boolean add(E e) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
      return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
      return false;
    }

    @Override
    public boolean addAll(int i, Collection<? extends E> collection) {
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public E get(int i) {
      return null;
    }

    @Override
    public E set(int i, E e) {
      return null;
    }

    @Override
    public void add(int i, E e) {}

    @Override
    public E remove(int i) {
      return null;
    }

    @Override
    public int indexOf(Object o) {
      return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
      return 0;
    }

    @Override
    public ListIterator<E> listIterator() {
      return null;
    }

    @Override
    public ListIterator<E> listIterator(int i) {
      return null;
    }

    @Override
    public List<E> subList(int i, int i1) {
      return null;
    }
  }

  static class NullableArrayListExtendZ<E> extends ArrayList<E> implements MyInterface<E> {
    NullableArrayListExtendZ() {
      super();
    }

    @Override
    public void print(E e) {}
  }

  static class NullableArrayListImplementZ<E> implements List<E>, MyInterface<E> {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return false;
    }

    @Override
    public Iterator<E> iterator() {
      return null;
    }

    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] ts) {
      return null;
    }

    @Override
    public boolean add(E e) {
      return false;
    }

    @Override
    public boolean remove(Object o) {
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
      return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
      return false;
    }

    @Override
    public boolean addAll(int i, Collection<? extends E> collection) {
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
      return false;
    }

    @Override
    public void clear() {}

    @Override
    public E get(int i) {
      return null;
    }

    @Override
    public E set(int i, E e) {
      return null;
    }

    @Override
    public void add(int i, E e) {}

    @Override
    public E remove(int i) {
      return null;
    }

    @Override
    public int indexOf(Object o) {
      return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
      return 0;
    }

    @Override
    public ListIterator<E> listIterator() {
      return null;
    }

    @Override
    public ListIterator<E> listIterator(int i) {
      return null;
    }

    @Override
    public List<E> subList(int i, int i1) {
      return null;
    }

    @Override
    public void print(E e) {}
  }

  static class CollectionMapImplements2<R, C> implements Iterable<R>, Map<R, C> {
    @Override
    public Iterator<R> iterator() {
      return null;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      return false;
    }

    @Override
    public boolean containsValue(Object o) {
      return false;
    }

    @Override
    public C get(Object o) {
      return null;
    }

    @Override
    public C put(R r, C c) {
      return null;
    }

    @Override
    public C remove(Object o) {
      return null;
    }

    @Override
    public void putAll(Map<? extends R, ? extends C> map) {}

    @Override
    public void clear() {}

    @Override
    public Set<R> keySet() {
      return null;
    }

    @Override
    public Collection<C> values() {
      return null;
    }

    @Override
    public Set<Entry<R, C>> entrySet() {
      return null;
    }
  }

  static class CollectionMapExtendImplement<R, C> extends HashMap<R, C> implements Iterable<R> {
    @Override
    public Iterator<R> iterator() {
      return null;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      return false;
    }

    @Override
    public boolean containsValue(Object o) {
      return false;
    }

    @Override
    public C get(Object o) {
      return null;
    }

    @Override
    public C put(R r, C c) {
      return null;
    }

    @Override
    public C remove(Object o) {
      return null;
    }

    @Override
    public void putAll(Map<? extends R, ? extends C> map) {}

    @Override
    public void clear() {}

    @Override
    public Set<R> keySet() {
      return null;
    }

    @Override
    public Collection<C> values() {
      return null;
    }

    @Override
    public Set<Entry<R, C>> entrySet() {
      return null;
    }
  }

  static class CollectionMapImplements2Integer1<C>
      implements Iterable<PathClassLoader>, Map<PathClassLoader, C> {
    @Override
    public Iterator<PathClassLoader> iterator() {
      return null;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      return false;
    }

    @Override
    public boolean containsValue(Object o) {
      return false;
    }

    @Override
    public C get(Object o) {
      return null;
    }

    @Override
    public C put(PathClassLoader unsafe, C c) {
      return null;
    }

    @Override
    public C remove(Object o) {
      return null;
    }

    @Override
    public void putAll(Map<? extends PathClassLoader, ? extends C> map) {}

    @Override
    public void clear() {}

    @Override
    public Set<PathClassLoader> keySet() {
      return null;
    }

    @Override
    public Collection<C> values() {
      return null;
    }

    @Override
    public Set<Entry<PathClassLoader, C>> entrySet() {
      return null;
    }
  }

  static class CollectionMapExtendImplementInteger1<C> extends HashMap<PathClassLoader, C>
      implements Iterable<PathClassLoader> {
    @Override
    public Iterator<PathClassLoader> iterator() {
      return null;
    }
  }

  static class CollectionMapImplements2Integer2<R> implements Iterable<R>, Map<R, PathClassLoader> {
    @Override
    public Iterator<R> iterator() {
      return null;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object o) {
      return false;
    }

    @Override
    public boolean containsValue(Object o) {
      return false;
    }

    @Override
    public PathClassLoader get(Object o) {
      return null;
    }

    @Override
    public PathClassLoader put(R r, PathClassLoader unsafe) {
      return null;
    }

    @Override
    public PathClassLoader remove(Object o) {
      return null;
    }

    @Override
    public void putAll(Map<? extends R, ? extends PathClassLoader> map) {}

    @Override
    public void clear() {}

    @Override
    public Set<R> keySet() {
      return null;
    }

    @Override
    public Collection<PathClassLoader> values() {
      return null;
    }

    @Override
    public Set<Entry<R, PathClassLoader>> entrySet() {
      return null;
    }
  }

  static class CollectionMapExtendImplementInteger2<R> extends HashMap<R, PathClassLoader>
      implements Iterable<R> {
    @Override
    public Iterator<R> iterator() {
      return null;
    }
  }

  // SQLDataException implements Iterable<Throwable>.
  static class MySQLDataException extends SQLDataException {}

  // java.util.Date for the extra dispatch case.
  static class MyDate extends Date {}

  static class MyDateZ<Z> extends Date implements MyInterface<Z> {
    @Override
    public void print(Z z) {}
  }
}
