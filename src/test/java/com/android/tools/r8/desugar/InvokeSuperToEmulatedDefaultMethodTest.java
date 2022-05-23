// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSuperToEmulatedDefaultMethodTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED = StringUtils.lines("bar", "bar");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public InvokeSuperToEmulatedDefaultMethodTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private boolean needsDefaultInterfaceMethodDesugaring() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue("Run only once", libraryDesugaringSpecification == JDK11);
    assumeFalse(needsDefaultInterfaceMethodDesugaring());
    testForRuntime(parameters)
        .addInnerClasses(InvokeSuperToEmulatedDefaultMethodTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Throwable {
    assumeTrue(needsDefaultInterfaceMethodDesugaring());
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public interface StringMap extends Map<String, String> {

    @Override
    default String getOrDefault(Object key, String defaultValue) {
      // Simple forward that targets the desugared library.
      return Map.super.getOrDefault(key + "oo", defaultValue);
    }
  }

  public interface StringMapIndirection extends StringMap {

    @Override
    default String getOrDefault(Object key, String defaultValue) {
      // Simple forward to a program defined default method.
      return StringMap.super.getOrDefault("f", defaultValue);
    }
  }

  public static class StringHashMap implements StringMapIndirection {
    HashMap<String, String> delegate = new HashMap<>();

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return delegate.containsValue(value);
    }

    @Override
    public String get(Object key) {
      return delegate.get(key);
    }

    @Override
    public String put(String key, String value) {
      return delegate.put(key, value);
    }

    @Override
    public String remove(Object key) {
      return delegate.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
      delegate.putAll(m);
    }

    @Override
    public void clear() {
      delegate.clear();
    }

    @Override
    public Set<String> keySet() {
      return delegate.keySet();
    }

    @Override
    public Collection<String> values() {
      return delegate.values();
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
      return delegate.entrySet();
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      StringHashMap map = new StringHashMap();
      map.put("foo", "bar");
      System.out.println(map.getOrDefault("foo", "not found"));
      System.out.println(map.getOrDefault("bar", "not found"));
    }
  }
}
