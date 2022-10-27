// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.file;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.Consumer;

/** For all wrappers, the spliterator is based on the iterator so no need to wrap it. */
public class PathApiFlips {

  public static Iterator<?> flipIteratorPath(Iterator<?> iterator) {
    return new IteratorPathWrapper<>(iterator);
  }

  public static Iterable<?> flipIterablePath(Iterable<?> iterable) {
    return new IterablePathWrapper<>(iterable);
  }

  public static java.nio.file.DirectoryStream<?> flipDirectoryStreamPath(
      java.nio.file.DirectoryStream<?> directoryStream) {
    return new DirectoryStreamPathWrapper<>(directoryStream);
  }

  public static java.nio.file.DirectoryStream.Filter<?> flipDirectoryStreamFilterPath(
      java.nio.file.DirectoryStream.Filter<?> filter) {
    return new DirectoryStreamFilterWrapper<>(filter);
  }

  /**
   * The generic types inherit from java.lang.Object even if in practice it seems they are
   * exclusively used with Path. To be conservative, we return the parameter if it's not a path so
   * the code works for non Path objects.
   */
  @SuppressWarnings("unchecked")
  public static <T> T convertPath(T maybePath) {
    if (maybePath == null) {
      return null;
    }
    if (maybePath instanceof java.nio.file.Path) {
      return (T) j$.nio.file.Path.wrap_convert((java.nio.file.Path) maybePath);
    }
    if (maybePath instanceof j$.nio.file.Path) {
      return (T) j$.nio.file.Path.wrap_convert((j$.nio.file.Path) maybePath);
    }
    return maybePath;
  }

  public static class DirectoryStreamFilterWrapper<T>
      implements java.nio.file.DirectoryStream.Filter<T> {

    private final java.nio.file.DirectoryStream.Filter<T> filter;

    public DirectoryStreamFilterWrapper(java.nio.file.DirectoryStream.Filter<T> filter) {
      this.filter = filter;
    }

    @Override
    public boolean accept(T t) throws IOException {
      return filter.accept(convertPath(t));
    }
  }

  public static class IterablePathWrapper<T> implements Iterable<T> {

    private final Iterable<T> iterable;

    public IterablePathWrapper(Iterable<T> iterable) {
      this.iterable = iterable;
    }

    @Override
    public Iterator<T> iterator() {
      return new IteratorPathWrapper<>(iterable.iterator());
    }

    @Override
    public void forEach(Consumer<? super T> action) {
      iterable.forEach(path -> action.accept(convertPath(path)));
    }
  }

  public static class IteratorPathWrapper<T> implements Iterator<T> {

    private final Iterator<T> iterator;

    public IteratorPathWrapper(Iterator<T> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public T next() {
      return convertPath(iterator.next());
    }

    @Override
    public void remove() {
      iterator.remove();
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
      iterator.forEachRemaining(path -> action.accept(convertPath(path)));
    }
  }

  public static class DirectoryStreamPathWrapper<T> implements DirectoryStream<T> {

    private final DirectoryStream<T> directoryStream;

    public DirectoryStreamPathWrapper(DirectoryStream<T> directoryStream) {
      this.directoryStream = directoryStream;
    }

    @Override
    public Iterator<T> iterator() {
      return new IteratorPathWrapper<>(directoryStream.iterator());
    }

    @Override
    public void forEach(Consumer<? super T> action) {
      directoryStream.forEach(path -> action.accept(convertPath(path)));
    }

    @Override
    public void close() throws IOException {
      directoryStream.close();
    }
  }
}
