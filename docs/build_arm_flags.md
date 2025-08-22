# Building llama.cpp for ARM-specific CPU flags

- llama.cpp can be compiled with Arm64-specific CPU extensions to accelerate inference on supported devices.
- Referring [llama.rn](https://github.com/mybigday/llama.rn), the React Native bindings for llama.cpp, we 
  build/compile multiple shared libraries, each 
  targeting a specific set of CPU extensions and a Arm64-v8 version. The app then, at runtime, loads the appropriate shared library by determining the CPU extensions available on the device and the Arm version using `System.loadLibrary`.
- To see how multiple shared libraries are compiled, check [`lobstachat/src/main/cpp/CMakeLists.txt`](https://github.com/RobLobsta/LobstaChat-Android/blob/main/lobstachat/src/main/cpp/CMakeLists.txt).
- To see how the app loads the appropriate shared library, check [`lobstachat/src/main/java/com/roblobsta/lobstachat/smollm/LobstaChatLM.kt`](https://github.com/RobLobsta/LobstaChat-Android/blob/main/lobstachat/src/main/java/com/roblobsta/lobstachat/smollm/LobstaChatLM.kt).

> [!NOTE]
> The APK contains multiple `.so` files for the `arm64-v8a` and `armeabi-v7a` ABIs. As the size of each `.so` file < 1 MB, the increase 
> in the size of the APK should be insignificant.

### CPU Extensions

We are compiling against the following [Arm64-specific CPU flags (feature modifiers)](https://gcc.gnu.org/onlinedocs/gcc/AArch64-Options.html#aarch64-feature-modifiers):

- `fp16`: Enable FP16 extension. This also enables floating-point instructions.

- `dotprod`: Enable the Dot Product extension. This also enables Advanced SIMD instructions.

- `i8mm`: Enable 8-bit Integer Matrix Multiply instructions. This also enables Advanced SIMD and floating-point instructions. This option is enabled by default for -march=armv8.6-a. Use of this option with architectures prior to Armv8.2-A is not supported.

- `sve`: Enable Scalable Vector Extension instructions. This also enables Advanced SIMD and floating-point instructions.

#### CPU Extensions for Arm-v7 (32-bit)

The app compiles llama.cpp with the following flags for Arm-v7a (32-bit) devices:

- `-mfpu=neon-vfpv4`: Enable the Armv7 VFPv4 floating-point extension and the Advanced SIMD extension.

- `-mfloat-abi=softfp`: Specifies whether to use hardware instructions or software library functions for floating-point operations, and which registers are used to pass floating-point parameters and return values. `softfp` enables hardware floating-point instructions and software floating-point linkage.

You may also check the official [Arm Compiler CMD options](https://developer.arm.com/documentation/dui0774/l/Compiler-Command-line-Options).

The following metrics were observed on a [Samsung M02 device](https://www.gsmarena.com/samsung_galaxy_m02-10709.php) when benchmarking llama.cpp using different arm-v7a flags. The model used was [SmolLM2-360M-Instruct-GGUF](https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/tree/main):

| Flags                                              | pp (512) | tg (128) |
|----------------------------------------------------|----------|----------|
| -march=armv7-a -mfpu=neon-vfpv4 -mfloat-abi=softfp |      4.7 |      7.5 |
| -march=armv7-a                                     |       11 |      8.4 |
| <None>                                             |       11 |      8.4 |

### Configuring CMake

- In [`lobstachat/src/main/cpp/CMakeLists.txt`](https://github.com/RobLobsta/LobstaChat-Android/blob/main/lobstachat/src/main/cpp/CMakeLists.txt),
we list all source files from llama.cpp and `lobstachat/src/main/cpp` to compile them into a single target.

- Normally, we would compile the target `lobstachat` and link it dynamically with targets `llama`, `common` and `ggml`
defined by llama.cpp. As we need to compile with different CPU flags, we combine the source files of all llama.cpp 
targets and our own JNI bindings into one single target `lobstachat` and then apply the CPU flags with `target_compile_options`.
