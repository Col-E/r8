// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.GlobalSyntheticsResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.Version;
import com.android.tools.r8.origin.Origin;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class InternalGlobalSyntheticsProgramProvider implements ProgramResourceProvider {

  public static class GlobalsEntryOrigin extends Origin {

    private final String entryName;

    public GlobalsEntryOrigin(String entryName, Origin parent) {
      super(parent);
      this.entryName = entryName;
    }

    @Override
    public String part() {
      return "global(" + entryName + ")";
    }
  }

  private final List<GlobalSyntheticsResourceProvider> providers;
  private List<ProgramResource> resources = null;

  public InternalGlobalSyntheticsProgramProvider(List<GlobalSyntheticsResourceProvider> providers) {
    this.providers = providers;
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    if (resources == null) {
      ensureResources();
    }
    return resources;
  }

  private synchronized void ensureResources() throws ResourceException {
    if (resources != null) {
      return;
    }
    List<ProgramResource> resources = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (GlobalSyntheticsResourceProvider provider : providers) {
      List<Function<Kind, ProgramResource>> delayedResouces = new ArrayList<>();
      Kind providerKind = null;
      try (ZipInputStream stream = new ZipInputStream(provider.getByteStream())) {
        ZipEntry entry;
        while (null != (entry = stream.getNextEntry())) {
          String name = entry.getName();
          if (name.equals(InternalGlobalSyntheticsProgramConsumer.OUTPUT_KIND_ENTRY_NAME)) {
            providerKind =
                Kind.valueOf(new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8));
          } else if (name.equals(
              InternalGlobalSyntheticsProgramConsumer.COMPILER_INFO_ENTRY_NAME)) {
            String version = new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8);
            if (!Version.getVersionString().equals(version)) {
              throw new ResourceException(
                  provider.getOrigin(),
                  "Outdated or inconsistent global synthetics information."
                      + "\nGlobal synthetics information version: "
                      + version
                      + "\nCompiler version: "
                      + Version.getVersionString());
            }
          } else if (name.endsWith(FileUtils.GLOBAL_SYNTHETIC_EXTENSION) && seen.add(name)) {
            GlobalsEntryOrigin origin = new GlobalsEntryOrigin(name, provider.getOrigin());
            String descriptor = guessTypeDescriptor(name);
            byte[] bytes = ByteStreams.toByteArray(stream);
            Set<String> descriptors = Collections.singleton(descriptor);
            delayedResouces.add(
                kind -> OneShotByteResource.create(kind, origin, bytes, descriptors));
          }
        }
      } catch (IOException e) {
        throw new ResourceException(provider.getOrigin(), e);
      }
      if (providerKind == null) {
        throw new ResourceException(
            provider.getOrigin(),
            "Invalid global synthetics provider does not specify its content kind.");
      }
      for (Function<Kind, ProgramResource> fn : delayedResouces) {
        resources.add(fn.apply(providerKind));
      }
    }
    this.resources = resources;
  }

  private String guessTypeDescriptor(String name) {
    String noExt = name.substring(0, name.length() - FileUtils.GLOBAL_SYNTHETIC_EXTENSION.length());
    String classExt = noExt + FileUtils.CLASS_EXTENSION;
    return DescriptorUtils.guessTypeDescriptor(classExt);
  }
}
