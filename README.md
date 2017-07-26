# Ios Device Control Library

Ios Device Control is a Java library for controlling real (physical) devices and
simulated devices tethered to a device running macOS. This includes the ability
to get system information, install and start applications, take screenshots,
capture logs, and more!

The devices are controlled by utilizing the built in Apple tools for the
Simulator (simctl) and the open source libimobiledevice binaries for real
devices. Examples of how to use this library can be found
[here](src/com/google/iosdevicecontrol/examples). To run the examples, run:

<code>mvn assembly:assembly</code>

which will create two runnable jars in the target directory.

## License

Ios Device Control is licensed under the open-source [Apache 2.0
license](LICENSE)

## Contributing

Please [see the guidelines for contributing](CONTRIBUTING.md) before creating
pull requests
