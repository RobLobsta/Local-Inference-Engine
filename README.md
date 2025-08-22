<img src="resources/app_icon/icon.png" alt="app icon" width="256"/>

# LobstaChat - On-Device Inference of SLMs in Android

<table>
<tr>
<td>
<img src="resources/app_screenshots/phone/1.png" alt="app_img_01">
</td>
<td>
<img src="resources/app_screenshots/phone/2.png" alt="app_img_02">
</td>
<td>
<img src="resources/app_screenshots/phone/3.png" alt="app_img_03">
</td>
</tr>
<tr>
<td>
<img src="resources/app_screenshots/phone/4.png" alt="app_img_04">
</td>
<td>
<img src="resources/app_screenshots/phone/5.png" alt="app_img_05">
</td>
<td>
<img src="resources/app_screenshots/phone/6.png" alt="app_img_06">
</td>
</tr>
</table>

## Installation

### GitHub

1. Download the latest APK from [GitHub Releases](https://github.com/RobLobsta/LobstaChat-Android/releases/) and transfer it to your Android device.
2. If your device does not downloading APKs from untrusted sources, search for **how to allow downloading APKs from unknown sources** for your device.

### Obtainium

[Obtainium](https://obtainium.imranr.dev/) allows users to update/download apps directly from their sources, like GitHub or FDroid. 

1. [Download the Obtainium app](https://obtainium.imranr.dev/) by choosing your device architecture or 'Download Universal APK'.
2. From the bottom menu, select '➕Add App'
3. In the text field labelled 'App source URL *', enter the following URL and click 'Add' besides the text field: `https://github.com/RobLobsta/LobstaChat-Android`
4. LobstaChat should now be visible in the 'Apps' screen. You can get notifications about newer releases and download them directly without going to the GitHub repo.

## Project Goals

- Provide a usable user interface to interact with local SLMs (small language models) locally, on-device
- Allow users to add/remove SLMs (GGUF models) and modify their system prompts or inference parameters (temperature, 
  min-p)
- Allow users to create specific-downstream tasks quickly and use SLMs to generate responses
- Simple, easy to understand, extensible codebase

## Setup

1. Clone the repository with its submodule originating from llama.cpp,

```commandline
git clone --depth=1 https://github.com/RobLobsta/LobstaChat-Android
cd LobstaChat-Android
git submodule update --init --recursive
```

2. Android Studio starts building the project automatically. If not, select **Build > Rebuild Project** to start a project build.

3. After a successful project build, [connect an Android device](https://developer.android.com/studio/run/device) to your system. Once connected, the name of the device must be visible in top menu-bar in Android Studio.

## Working

1. The application uses llama.cpp to load and execute GGUF models. As llama.cpp is written in pure C/C++, it is easy 
   to compile on Android-based targets using the [NDK](https://developer.android.com/ndk). 

2. The `lobstachat` module uses a `llm_inference.cpp` class which interacts with llama.cpp's C-style API to execute the
   GGUF model and a JNI binding `lobstachat.cpp`. Check the [C++ source files here](https://github.com/RobLobsta/LobstaChat-Android/tree/main/lobstachat/src/main/cpp). On the Kotlin side, the [`LobstaChatLM`](https://github.com/RobLobsta/LobstaChat-Android/blob/main/lobstachat/src/main/java/com/roblobsta/lobstachat/smollm/LobstaChatLM.kt) class provides
   the required methods to interact with the JNI (C++ side) bindings.

3. The `app` module contains the application logic and UI code. Whenever a new chat is opened, the app instantiates 
   the `LobstaChatLM` class and provides it the model file-path which is stored by the [`LLMModel`](https://github.com/RobLobsta/LobstaChat-Android/blob/main/app/src/main/java/com/roblobsta/lobstachat/data/DataModels.kt) entity.
   Next, the app adds messages with role `user` and `system` to the chat by retrieving them from the database and
   using `LLMInference::addChatMessage`.

4. For tasks, the messages are not persisted, and we inform to `LLMInference` by passing `_storeChats=false` to
   `LLMInference::loadModel`.

## Technologies

* [ggerganov/llama.cpp](https://github.com/ggerganov/llama.cpp) is a pure C/C++ framework to execute machine learning 
  models on multiple execution backends. It provides a primitive C-style API to interact with LLMs 
  converted to the [GGUF format](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md) native to [ggml](https://github.com/ggerganov/ggml)/llama.cpp. The app uses JNI bindings to interact with a small class `smollm.
  cpp` which uses llama.cpp to load and execute GGUF models.

* [noties/Markwon](https://github.com/noties/Markwon) is a markdown rendering library for Android. The app uses 
  Markwon and [Prism4j](https://github.com/noties/Prism4j) (for code syntax highlighting) to render Markdown responses 
  from the SLMs.

## Future

The following features/tasks are planned for the future releases of the app:

- **Desktop Integration:** Add a background service that uses BlueTooth/HTTP/WiFi to communicate with a desktop application. This would allow sending queries from the desktop to the mobile device for inference.
- **Vulkan for Inference:** Investigate if llama.cpp can be compiled to use Vulkan for inference on Android devices, which would leverage the mobile GPU for faster performance.