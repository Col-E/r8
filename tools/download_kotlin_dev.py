#!/usr/bin/env python3
# Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import utils
if utils.is_python3():
    from html.parser import HTMLParser
    import urllib.request
    url_request = urllib.request
else:
    from HTMLParser import HTMLParser
    import urllib
    url_request = urllib
import os
import sys

JETBRAINS_KOTLIN_MAVEN_URL = "https://maven.pkg.jetbrains.space/kotlin/p/" \
                             "kotlin/bootstrap/org/jetbrains/kotlin/"
KOTLIN_RELEASE_URL = JETBRAINS_KOTLIN_MAVEN_URL + "kotlin-compiler/"


def download_newest():
    response = url_request.urlopen(KOTLIN_RELEASE_URL)
    if response.getcode() != 200:
        raise Exception('Url: %s \n returned %s' %
                        (KOTLIN_RELEASE_URL, response.getcode()))
    content = str(response.read())
    release_candidates = []

    class HTMLContentParser(HTMLParser):

        def handle_data(self, data):
            if ('-dev-' in data):
                release_candidates.append(data)

    parser = HTMLContentParser()
    parser.feed(content)

    top_most_version = (0, 0, 0, 0)
    top_most_version_and_build = None

    for version in release_candidates:
        # The compiler version is on the form <major>.<minor>.<revision>-dev-<build>/
        version = version.replace('/', '')
        version_build_args = version.split('-')
        version_components = version_build_args[0].split('.')
        version_components.append(version_build_args[2])
        current_version = tuple(map(int, version_components))
        if (current_version > top_most_version):
            top_most_version = current_version
            top_most_version_and_build = version

    if (top_most_version_and_build is None):
        raise Exception('Url: %s \n returned %s' %
                        (KOTLIN_RELEASE_URL, response.getcode()))

    # We can now download all files related to the kotlin compiler version.
    print("Downloading version: " + top_most_version_and_build)

    kotlinc_lib = os.path.join(utils.THIRD_PARTY, "kotlin",
                               "kotlin-compiler-dev", "kotlinc", "lib")

    utils.DownloadFromGoogleCloudStorage(
        os.path.join(utils.THIRD_PARTY, "kotlin",
                     "kotlin-compiler-dev.tar.gz.sha1"))

    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-compiler/{0}/kotlin-compiler-{0}.jar".format(
            top_most_version_and_build), kotlinc_lib, "kotlin-compiler.jar")
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-stdlib/{0}/kotlin-stdlib-{0}.jar".format(
            top_most_version_and_build), kotlinc_lib, "kotlin-stdlib.jar")
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-reflect/{0}/kotlin-reflect-{0}.jar".format(
            top_most_version_and_build), kotlinc_lib, "kotlin-reflect.jar")
    download_and_save(
        JETBRAINS_KOTLIN_MAVEN_URL +
        "kotlin-script-runtime/{0}/kotlin-script-runtime-{0}.jar".format(
            top_most_version_and_build), kotlinc_lib,
        "kotlin-script-runtime.jar")


def download_and_save(url, path, name):
    print('Downloading: ' + url)
    url_request.urlretrieve(url, os.path.join(path, name))


if __name__ == '__main__':
    sys.exit(download_newest())
