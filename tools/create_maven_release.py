#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import hashlib
import argparse
from os import makedirs
from os.path import join
from shutil import copyfile, make_archive, rmtree
import subprocess
import sys
from string import Template
import tempfile

LICENSETEMPLATE = Template(
"""
    <license>
      <name>$name</name>
      <url>$url</url>
      <distribution>repo</distribution>
    </license>""")

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
    </license>$library_licenses
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
  result.add_argument('--jar', help='jar file to package')
  result.add_argument('--out', help='directory in which to put the output zip file')
  return result.parse_args(argv)

def determine_version(jar):
  cmd = []
  cmd.append('java')
  cmd.extend(['-jar', jar]);
  cmd.append('--version')
  output = subprocess.check_output(cmd)
  version_string = output.split()[1]
  assert version_string.startswith("v")
  return version_string[1:]

def generate_library_licenses():
  license_prefix = 'license: '
  licenses = []
  license_url_prefix = 'licenseUrl: '
  license_urls = []
  with open('LIBRARY-LICENSE', 'r') as file:
    for line in file:
      trimmed = line.strip()
      if trimmed.startswith(license_prefix):
        // Assert checking that licenses come in name/url pairs.
        assert len(licenses) == len(license_urls)
        name = trimmed[len(license_prefix):]
        if not name in licenses:
          licenses.append(name)
      if trimmed.startswith(license_url_prefix):
        url = trimmed[len(license_url_prefix):]
        if not url in license_urls:
          license_urls.append(url)
        // Assert checking that licenses come in name/url pairs.
        assert len(licenses) == len(license_urls)
  result = ''
  for i in range(len(licenses)):
    name = licenses[i]
    url = license_urls[i]
    result += LICENSETEMPLATE.substitute(name=name, url=url)
  return result

def write_pom_file(version, pom_file):
  library_licenses = generate_library_licenses()
  version_pom = POMTEMPLATE.substitute(version=version, library_licenses=library_licenses)
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
  jar = options.jar
  outdir = options.out
  if jar == None or outdir == None:
    print 'Need to supply --jar and --out.'
    exit(1)
  # Create directory structure for this version.
  version = determine_version(jar)
  tmp_dir = tempfile.mkdtemp()
  version_dir = join(
      tmp_dir, 'com', 'google', 'android', 'tools', 'r8', version, 'r8')
  makedirs(version_dir)
  # Write the pom file.
  pom_file = join(version_dir, 'r8-' + version + '.pom')
  write_pom_file(version, pom_file)
  # Copy the jar to the output.
  target_jar = join(version_dir, 'r8-' + version + '.jar')
  copyfile(jar, target_jar)
  # Create check sums.
  write_md5_for(target_jar)
  write_md5_for(pom_file)
  write_sha1_for(target_jar)
  write_sha1_for(pom_file)
  # Zip it up.
  make_archive(join(outdir, 'r8'), 'zip', tmp_dir)
  # Cleanup.
  rmtree(tmp_dir)

if __name__ == "__main__":
  exit(main(sys.argv[1:]))
