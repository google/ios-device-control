PREFIX=/usr/local

idevice-app-runner: idevice-app-runner.c
	gcc -g -pthread $^ -o $@ -I$(PREFIX)/include -L$(PREFIX)/lib -lplist -limobiledevice

install: idevice-app-runner
	# Use mkdir -p first, because "install -D" not support on Mac
	mkdir -p $(PREFIX)/bin/
	install $^ $(PREFIX)/bin/$^
