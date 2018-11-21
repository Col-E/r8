// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class Main {

  private void method() {}

  public static void main(String[] args) {
    new Main().method();
  }
}

@RunWith(Parameterized.class)
public class KeepDirectoriesTest extends ProguardCompatibilityTestBase {

  private Backend backend;
  private final boolean minify;

  @Parameterized.Parameters(name = "Backend: {0}, Minify: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public KeepDirectoriesTest(Backend backend, boolean minify) {
    this.backend = backend;
    this.minify = minify;
  }

  // Return the original package name for this package.
  private String pathForThisPackage() {
    return getClass().getPackage().getName().replace('.', '/');
  }

  // Return the package name in the app for this package.
  private String pathForThisPackage(AndroidApp app) throws Exception {
    ClassNameMapper mapper =
        ClassNameMapper.mapperFromString(app.getProguardMapOutputData().getString());
    String x = mapper.getObfuscatedToOriginalMapping().inverse().get(Main.class.getCanonicalName());
    return x.substring(0, x.lastIndexOf('.')).replace('.', '/');
  }

  private void checkResourceNames(Set<String> resourceNames, String expectedPackageName) {
    resourceNames.stream()
        .filter(name -> !name.startsWith("org"))
        .forEach(name -> assertTrue(name.startsWith(expectedPackageName)));
  }

  @Test
  public void testKeepNone() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8("", minify, dataResourceConsumer);
    assertEquals(0, dataResourceConsumer.size());
  }

  @Test
  public void testKeepAll() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    AndroidApp output = compileWithR8("-keepdirectories", minify, dataResourceConsumer);
    assertEquals(getDataResources().size(), dataResourceConsumer.size());
    checkResourceNames(dataResourceConsumer.getResourceNames(), pathForThisPackage(output));
  }

  @Test
  public void testKeepSome1() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8("-keepdirectories org/example/*", minify, dataResourceConsumer);
    Set<String> expected =
        getDataResources().stream()
            .map(DataResource::getName)
            .filter(name -> name.startsWith("org/example/"))
            .collect(Collectors.toSet());
    assertEquals(expected, dataResourceConsumer.getResourceNames());
  }

  private int numberOfPathSeparators(String s) {
    return (int) s.chars().filter(c -> c == '/').count();
  }

  class PrefixMapper implements Function<String, String> {
    private final String original;
    private final String mapped;

    PrefixMapper(String original, String mapped) {
      this.original = original;
      this.mapped = mapped;
    }

    @Override
    public String apply(String s) {
      if (s.startsWith(original)) {
        return mapped + s.substring(original.length());
      } else {
        return s;
      }
    }
  }

  @Test
  public void testKeepSome2() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    AndroidApp output =
        compileWithR8(
            "-keepdirectories org/*," + pathForThisPackage() + "/**", minify, dataResourceConsumer);
    Set<String> expected1 =
        getDataResources().stream()
            .map(DataResource::getName)
            .filter(name -> name.startsWith("org/"))
            .filter(name -> numberOfPathSeparators(name) < 2)
            .collect(Collectors.toSet());
    PrefixMapper prefixMapper = new PrefixMapper(pathForThisPackage(), pathForThisPackage(output));
    Set<String> expected2 =
        getDataResources().stream()
            .map(DataResource::getName)
            .filter(name -> name.startsWith(pathForThisPackage() + "/"))
            .map(prefixMapper)
            .collect(Collectors.toSet());
    assertEquals(Sets.union(expected1, expected2), dataResourceConsumer.getResourceNames());
  }

  private AndroidApp compileWithR8(
      String proguardConfig, boolean allowMinification, DataResourceConsumer dataResourceConsumer)
      throws CompilationFailedException {
    String r =
        "-keep"
            + (allowMinification ? ",allowobfuscation" : "")
            + " class "
            + Main.class.getCanonicalName();
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(getAndroidApp(), emptyConsumer(backend))
            .addProguardConfiguration(
                ImmutableList.of(proguardConfig, "-printmapping", r), Origin.unknown())
            .addLibraryFiles(runtimeJar(backend))
            .build();
    return ToolHelper.runR8(
        command, options -> options.dataResourceConsumer = dataResourceConsumer);
  }

  private AndroidApp getAndroidApp() {
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class));
    getDataResources().forEach(builder::addDataResource);
    return builder.build();
  }

  protected static class CustomDataResourceConsumer implements DataResourceConsumer {

    private final Set<String> resourceNames = Sets.newHashSet();

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
      assertFalse(resourceNames.contains(directory.getName()));
      resourceNames.add(directory.getName());
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
      throw new Unreachable();
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}

    public boolean isPresent(String name) {
      return resourceNames.contains(name);
    }

    public Set<String> getResourceNames() {
      return resourceNames;
    }

    public int size() {
      return resourceNames.size();
    }
  }

  private List<DataResource> getDataResources() {
    return ImmutableList.of(
        DataDirectoryResource.fromName("org", Origin.unknown()),
        DataDirectoryResource.fromName("org/example", Origin.unknown()),
        DataDirectoryResource.fromName("org/example/test", Origin.unknown()),
        DataDirectoryResource.fromName(pathForThisPackage(), Origin.unknown()),
        DataDirectoryResource.fromName(pathForThisPackage() + "/subpackage1", Origin.unknown()),
        DataDirectoryResource.fromName(pathForThisPackage() + "/subpackage2", Origin.unknown()),
        DataDirectoryResource.fromName(
            pathForThisPackage() + "/subpackage2/subpackage", Origin.unknown()));
  }
}
