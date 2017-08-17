# iOS Device Control Library

iOS Device Control is a Java library for controlling the iOS Simulator and real
(physical) iOS devices tethered to a device running macOS. The library offers
the ability to get device properties, install and start applications, take
screenshots, capture logs, and more!

Examples of how to use this library can be found
[here](src/com/google/iosdevicecontrol/examples). To build the examples, run:

```console
mvn assembly:assembly
```
which will create two runnable jars in the target directory.

## Installation

iOS Device Control only works on macOS with tethered real devices or with Xcode
8+ with the simctl tool installed for the iOS Simulator.

### Simulator Automation

Install [Xcode 8 or above](https://developer.apple.com/xcode/)
and verify the following command works:

```console
xcrun simctl --version
```

### Real Device Automation

The following dependencies can be installed easily with
[homebrew](http://brew.sh/):

```console
brew install autoconf automake libtool libxml2 libzip pkg-config openssl
```

Install libplist by building from source:
```console
git clone https://github.com/libimobiledevice/libplist.git
cd libplist
./autogen.sh
make
sudo make install
```

Install libusbmuxd by building from source:
```console
git clone https://github.com/libimobiledevice/libusbmuxd.git
cd libusbmuxd
./autogen.sh
make
sudo make install
```

Install libimobiledevice by building from source:
```console
git clone https://github.com/libimobiledevice/libimobiledevice.git
cd libimobiledevice
./autogen.sh
make
sudo make install
```

Install ideviceinstaller by building from source:
```console
git clone https://github.com/libimobiledevice/ideviceinstaller.git
cd ideviceinstaller
./autogen.sh
make
sudo make install
```

Install idevice_app_runner and idevicewebinspectorproxy by building from
source. This can be done by following the instructions outlined in the
READMEs of the respective projects in the [third_party directory](third_party).

#### Optional Utilities

Most of the iOS Device Control library can be used with just the above tools.
For additional control of real devices, the following tools can optionally be
installed:

Install [Apple Configurator 2](https://support.apple.com/apple-configurator)
and install the automation tools by selecting the "Install Automation Tools..."
option under the Apple Configurator 2 menu.

Install the provided OpenUrl app by following the instructions
[here](OpenUrlApp/README) to automate Safari on real devices.

## Troubleshooting

For real devices, make sure that both the device is trusted and the lockdown
folder has the correct permissions.
```console
sudo chmod -R 777 /var/db/lockdown
```

## License

iOS Device Control is licensed under the open-source
[Apache 2.0 license](LICENSE).

## Contributing

Please [see the guidelines for contributing](CONTRIBUTING.md) before creating
pull requests.
