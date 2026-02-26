cmake_minimum_required(VERSION 3.22.1)
project("sherpa-tts-jni")

# 无 lexicon 时用 espeak-ng 将文本转为音素（需打包 espeak-ng-data 并传 dataDir）。默认 OFF 避免下载/编译耗时。
option(SHERPA_TTS_ENABLE_ESPEAK_NG "Build with espeak-ng+piper-phonemize for TTS when no lexicon" OFF)

# 本项目 TTS 实现：不包含 sherpa-onnx。可选依赖 ONNX Runtime 以启用真实 VITS 推理。
# 方式一：ONNXRUNTIME_ROOT = 同一目录（含 include 与 lib/<abi>），如自动准备的 app/build/onnxruntime
# 方式二：ONNXRUNTIME_INCLUDE_DIR + ONNXRUNTIME_LIB_DIR = 头文件用手动放的包（如 Linux 预编译），库用 AAR 解出的 Android .so
set(ONNXRUNTIME_ROOT "" CACHE PATH "Path to ONNX Runtime (include + lib). If set, enable TTS.")
set(ONNXRUNTIME_INCLUDE_DIR "" CACHE PATH "Override: include dir only (e.g. from Linux prebuilt). Use with ONNXRUNTIME_LIB_DIR.")
set(ONNXRUNTIME_LIB_DIR "" CACHE PATH "Override: lib dir for Android .so (e.g. from AAR). Use with ONNXRUNTIME_INCLUDE_DIR.")

set(USE_ONNX OFF)
if(ONNXRUNTIME_INCLUDE_DIR AND ONNXRUNTIME_LIB_DIR)
  set(USE_ONNX ON)
  set(ONNX_INCLUDE "${ONNXRUNTIME_INCLUDE_DIR}")
  set(ONNX_LIB "${ONNXRUNTIME_LIB_DIR}")
elseif(ONNXRUNTIME_ROOT)
  set(USE_ONNX ON)
  set(ONNX_INCLUDE "${ONNXRUNTIME_ROOT}/include")
  set(ONNX_LIB "${ONNXRUNTIME_ROOT}/lib")
endif()

set(TTS_SOURCES tts_jni.cpp)
if(USE_ONNX)
  list(APPEND TTS_SOURCES token_table.cpp lexicon.cpp wave_writer.cpp vits_engine.cpp espeak_phonemize.cpp frontend_router.cpp)
endif()

if(SHERPA_TTS_ENABLE_ESPEAK_NG AND USE_ONNX)
  list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
  include(espeak-ng-for-piper)
  set(ESPEAK_NG_DIR ${espeak_ng_SOURCE_DIR})
  include(piper-phonemize)
endif()

add_library(sherpa-tts-jni SHARED ${TTS_SOURCES})
target_include_directories(sherpa-tts-jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
find_library(log-lib log)
target_link_libraries(sherpa-tts-jni ${log-lib})

if(USE_ONNX)
  target_compile_definitions(sherpa-tts-jni PRIVATE SHERPA_TTS_USE_ONNXRUNTIME=1)
  target_include_directories(sherpa-tts-jni PRIVATE
    ${ONNX_INCLUDE}
    ${ONNX_INCLUDE}/onnxruntime/core/session
  )
  target_link_directories(sherpa-tts-jni PRIVATE
    ${ONNX_LIB}/${ANDROID_ABI}
    ${ONNX_LIB}
  )
  target_link_libraries(sherpa-tts-jni onnxruntime)
  if(SHERPA_TTS_ENABLE_ESPEAK_NG)
    target_compile_definitions(sherpa-tts-jni PRIVATE SHERPA_TTS_USE_ESPEAK_NG=1)
    target_link_libraries(sherpa-tts-jni piper_phonemize espeak-ng)
    if(NOT BUILD_SHARED_LIBS)
      target_link_libraries(sherpa-tts-jni ucd)
    endif()
  endif()
endif()
