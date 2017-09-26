# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
FROM debian:stretch

RUN apt-get update && apt-get install -y \
	bzip2 \
	g++ \
	gcc \
	git \
	nano \
	python \
	python-dev \
	sed \
	texinfo \
	unzip \
	wget \
        openjdk-8-jdk \
    sudo

# Set the timezone.
RUN echo "Europe/Copenhagen" > /etc/timezone && \
    dpkg-reconfigure -f noninteractive tzdata

ENV user r8

# Create user without password and sudo access.
RUN useradd -m -G dialout,sudo $user && \
    echo "$user ALL=(ALL:ALL) NOPASSWD:ALL" > /etc/sudoers.d/$user && \
    chmod 440 /etc/sudoers.d/$user

USER $user

CMD (cd /home/$user && /bin/bash)
