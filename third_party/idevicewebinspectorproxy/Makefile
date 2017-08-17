PREFIX=/usr/local
DEPS = $(LIBIMD_ROOT)/common/socket.h $(LIBIMD_ROOT)/common/thread.h $(LIBIMD_ROOT)/include/endianness.h
OBJ = socket.o thread.o idevicewebinspectorproxy.o

%.o: $(LIBIMD_ROOT)/common/%.c $(DEPS)
	gcc -c -o $@ $<

idevicewebinspectorproxy: test-libimd-root $(OBJ)
	gcc -g $(filter-out $<,$^) -o $@  -lplist -limobiledevice
	rm *.o

idevicewebinspectorproxy.o: idevicewebinspectorproxy.c
	gcc -c -o $@ -I$(LIBIMD_ROOT) -I$(LIBIMD_ROOT)/include $<

test-libimd-root:
	test -n "$(LIBIMD_ROOT)" # $$LIBIMD_ROOT

install: idevicewebinspectorproxy
	# Use mkdir -p first, because "install -D" not support on Mac
	mkdir -p $(PREFIX)/bin/
	install $^ $(PREFIX)/bin/$^
