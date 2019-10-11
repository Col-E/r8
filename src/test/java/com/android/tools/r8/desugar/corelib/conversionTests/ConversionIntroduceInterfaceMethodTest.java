// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ConversionIntroduceInterfaceMethodTest extends APIConversionTestBase {

  @Test
  public void testNoInterfaceMethods() throws Exception {
    Path customLib = testForD8().addProgramClasses(CustomLibClass.class).compile().writeToZip();
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(
            MyCollectionInterface.class,
            MyCollectionInterfaceAbstract.class,
            MyCollection.class,
            Executor.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibraryWithConversionExtension, AndroidApiLevel.B)
        .addRunClasspathFiles(customLib)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class)
        .assertSuccessWithOutput(
            StringUtils.lines(
                "action called from j$ consumer",
                "forEach called",
                "action called from java consumer",
                "forEach called"));
  }

  static class CustomLibClass {

    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public static void callForeach(Iterable iterable) {
      iterable.forEach(x -> System.out.println("action called from java consumer"));
    }
  }

  static class Executor {

    @SuppressWarnings("RedundantOperationOnEmptyContainer")
    public static void main(String[] args) {
      MyCollection<String> strings = new MyCollection<>();
      // Call foreach with j$ consumer.
      strings.forEach(x -> System.out.println("action called from j$ consumer"));
      // Call foreach with java consumer.
      CustomLibClass.callForeach(strings);
    }
  }

  interface MyCollectionInterface<E> extends Collection<E> {

    // The following method override a method from Iterable and use a desugared type.
    // API conversion is required.
    @Override
    default void forEach(Consumer<? super E> action) {
      action.accept(null);
      System.out.println("forEach called");
    }
  }

  @SuppressWarnings("ConstantConditions")
  static class MyCollection<E> implements MyCollectionInterface<E> {

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

    @NotNull
    @Override
    public Iterator<E> iterator() {
      return null;
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return new Object[0];
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
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
    public boolean containsAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
      return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {}
  }

  interface MyCollectionInterfaceAbstract<E> extends Collection<E> {

    // The following method override a method from Iterable and use a desugared type.
    // API conversion is required.
    @Override
    void forEach(Consumer<? super E> action);
  }
}
