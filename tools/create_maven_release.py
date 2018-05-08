#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import gradle
import hashlib
from os import makedirs
from os.path import join
from shutil import copyfile, make_archive, rmtree
import subprocess
import sys
from string import Template
import tempfile
import utils

DEPENDENCYTEMPLATE = Template(
"""
    <dependency>
        <groupId>$group</groupId>
        <artifactId>$artifact</artifactId>
        <version>$version</version>
    </dependency>""")

POMTEMPLATE = Template(
"""<project
    xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.android.tools</groupId>
  <artifactId>r8</artifactId>
  <version>$version</version>
  <name>D8 dexer and R8 shrinker</name>
  <description>
  D8 dexer and R8 shrinker.
  </description>
  <url>http://r8.googlesource.com/r8</url>
  <inceptionYear>2016</inceptionYear>
  <licenses>
    <license>
      <name>BSD-3-Clause</name>
      <url>https://opensource.org/licenses/BSD-3-Clause</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <dependencies>$dependencies
  </dependencies>
  <developers>
    <developer>
      <name>The Android Open Source Project</name>
    </developer>
  </developers>
  <scm>
    <connection>
      https://r8.googlesource.com/r8.git
    </connection>
    <url>
      https://r8.googlesource.com/r8
    </url>
  </scm>
</project>
""")

def parse_options(argv):
  result = argparse.ArgumentParser()
  result.add_argument('--out', help='directory in which to put the output zip file')
  return result.parse_args(argv)

def determine_version():
  version_file = join(
      utils.SRC_ROOT, 'com', 'android', 'tools', 'r8', 'Version.java')
  with open(version_file, 'r') as file:
    for line in file:
      if 'final String LABEL ' in line:
        result = line[line.find('"') + 1:]
        result = result[:result.find('"')]
        return result
  raise Exception('Unable to determine version.')

def generate_library_licenses():
  license_prefix = 'license: '
  licenses = []
  license_url_prefix = 'licenseUrl: '
  license_urls = []
  with open('LIBRARY-LICENSE', 'r') as file:
    for line in file:
      trimmed = line.strip()
      if trimmed.startswith(license_prefix):
        # Assert checking that licenses come in name/url pairs.
        assert len(licenses) == len(license_urls)
        name = trimmed[len(license_prefix):]
        if not name in licenses:
          licenses.append(name)
      if trimmed.startswith(license_url_prefix):
        url = trimmed[len(license_url_prefix):]
        if not url in license_urls:
          license_urls.append(url)
        # Assert checking that licenses come in name/url pairs.
        assert len(licenses) == len(license_urls)
  result = ''
  for i in range(len(licenses)):
    name = licenses[i]
    url = license_urls[i]
    result += LICENSETEMPLATE.substitute(name=name, url=url)
  return result


# Generate the dependencies block for the pom file.
#
# We ask gradle to list all dependencies. In that output
# we locate the runtimeClasspath block for 'main' which
# looks something like:
#
# runtimeClasspath - Runtime classpath of source set 'main'.
# +--- net.sf.jopt-simple:jopt-simple:4.6
# +--- com.googlecode.json-simple:json-simple:1.1
# +--- com.google.guava:guava:23.0
# +--- it.unimi.dsi:fastutil:7.2.0
# +--- org.ow2.asm:asm:6.0
# +--- org.ow2.asm:asm-commons:6.0
# |    \--- org.ow2.asm:asm-tree:6.0
# |         \--- org.ow2.asm:asm:6.0
# +--- org.ow2.asm:asm-tree:6.0 (*)
# +--- org.ow2.asm:asm-analysis:6.0
# |    \--- org.ow2.asm:asm-tree:6.0 (*)
# \--- org.ow2.asm:asm-util:6.0
#      \--- org.ow2.asm:asm-tree:6.0 (*)
#
# We filter out the repeats that are marked by '(*)'.
#
# For each remaining line, we remove the junk at the start
# in chunks. As an example:
#
# '  |    \--- org.ow2.asm:asm-tree:6.0  '  --strip-->
# '|    \--- org.ow2.asm:asm-tree:6.0'  -->
# '\--- org.ow2.asm:asm-tree:6.0'  -->
# 'org.ow2.asm:asm-tree:6.0'
#
# The end result is the dependency we are looking for:
#
# groupId: org.ow2.asm
# artifact: asm-tree
# version: 6.0
def generate_dependencies():
  dependencies = gradle.RunGradleGetOutput(['dependencies'])
  dependency_lines = []
  collect = False
  for line in dependencies.splitlines():
    if 'runtimeClasspath' in line and "'main'" in line:
      collect = True
      continue
    if collect:
      if not len(line) == 0:
        if not '(*)' in line:
          trimmed = line.strip()
          while trimmed.find(' ') != -1:
            trimmed = trimmed[trimmed.find(' ') + 1:].strip()
          if not trimmed in dependency_lines:
            dependency_lines.append(trimmed)
      else:
        break
  result = ''
  for dep in dependency_lines:
    components = dep.split(':')
    assert len(components) == 3
    group = components[0]
    artifact = components[1]
    version = components[2]
    result += DEPENDENCYTEMPLATE.substitute(
        group=group, artifact=artifact, version=version)
  return result

def write_pom_file(version, pom_file):
  dependencies = generate_dependencies()
  version_pom = POMTEMPLATE.substitute(
      version=version, dependencies=dependencies)
  with open(pom_file, 'w') as file:
    file.write(version_pom)

def hash_for(file, hash):
  with open(file, 'rb') as f:
    while True:
      # Read chunks of 1MB
      chunk = f.read(2 ** 20)
      if not chunk:
        break
      hash.update(chunk)
  return hash.hexdigest()

def write_md5_for(file):
  hexdigest = hash_for(file, hashlib.md5())
  with (open(file + '.md5', 'w')) as file:
    file.write(hexdigest)

def write_sha1_for(file):
  hexdigest = hash_for(file, hashlib.sha1())
  with (open(file + '.sha1', 'w')) as file:
    file.write(hexdigest)

def main(argv):
  options = parse_options(argv)
  outdir = options.out
  if outdir == None:
    print 'Need to supply output dir with --out.'
    exit(1)
  # Build the R8 no deps artifact.
  gradle.RunGradleExcludeDeps([utils.R8])
  # Create directory structure for this version.
  version = determine_version()
  with utils.TempDir() as tmp_dir:
    version_dir = join(tmp_dir, 'com', 'android', 'tools', 'r8', version)
    makedirs(version_dir)
    # Write the pom file.
    pom_file = join(version_dir, 'r8-' + version + '.pom')
    write_pom_file(version, pom_file)
    # Copy the jar to the output.
    target_jar = join(version_dir, 'r8-' + version + '.jar')
    copyfile(utils.R8_JAR, target_jar)
    # Create check sums.
    write_md5_for(target_jar)
    write_md5_for(pom_file)
    write_sha1_for(target_jar)
    write_sha1_for(pom_file)
    # Zip it up.
    make_archive(join(outdir, 'r8'), 'zip', tmp_dir)

if __name__ == "__main__":
  exit(main(sys.argv[1:]))
