# From sherpa-onnx; build espeak-ng for Piper phonemize (TTS without lexicon).
function(download_espeak_ng_for_piper)
  # Keep espeak-ng minimal for Android JNI usage.
  # These options must apply to both local-source and FetchContent paths.
  set(BUILD_ESPEAK_NG_TESTS OFF CACHE BOOL "" FORCE)
  set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
  set(USE_ASYNC OFF CACHE BOOL "" FORCE)
  set(USE_MBROLA OFF CACHE BOOL "" FORCE)
  set(USE_LIBSONIC OFF CACHE BOOL "" FORCE)
  set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
  set(USE_KLATT OFF CACHE BOOL "" FORCE)
  set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
  set(EXTRA_cmn ON CACHE BOOL "" FORCE)
  set(EXTRA_ru ON CACHE BOOL "" FORCE)
  set(BUILD_ESPEAK_NG_EXE OFF CACHE BOOL "" FORCE)

  if(SHERPA_TTS_ESPEAK_NG_SOURCE_DIR)
    if(NOT EXISTS "${SHERPA_TTS_ESPEAK_NG_SOURCE_DIR}/CMakeLists.txt")
      message(FATAL_ERROR "SHERPA_TTS_ESPEAK_NG_SOURCE_DIR is invalid: ${SHERPA_TTS_ESPEAK_NG_SOURCE_DIR}")
    endif()
    set(espeak_ng_SOURCE_DIR "${SHERPA_TTS_ESPEAK_NG_SOURCE_DIR}")
    set(espeak_ng_BINARY_DIR "${CMAKE_BINARY_DIR}/_deps/espeak_ng-build-local")
    message(STATUS "Using local espeak-ng source: ${espeak_ng_SOURCE_DIR}")
  else()
  include(FetchContent)
  set(espeak_ng_URL  "https://github.com/csukuangfj/espeak-ng/archive/f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip")
  set(espeak_ng_URL2 "https://hf-mirror.com/csukuangfj/sherpa-onnx-cmake-deps/resolve/main/espeak-ng-f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip")
  set(espeak_ng_HASH "SHA256=70cbf4050e7a014aae19140b05e57249da4720f56128459fbe3a93beaf971ae6")
  set(possible_file_locations
    $ENV{HOME}/Downloads/espeak-ng-f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip
    ${CMAKE_SOURCE_DIR}/espeak-ng-f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip
    ${CMAKE_BINARY_DIR}/espeak-ng-f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip
    /tmp/espeak-ng-f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip)
  foreach(f IN LISTS possible_file_locations)
    if(EXISTS ${f})
      set(espeak_ng_URL  "${f}")
      file(TO_CMAKE_PATH "${espeak_ng_URL}" espeak_ng_URL)
      message(STATUS "Found local espeak-ng zip: ${espeak_ng_URL}")
      set(espeak_ng_URL2)
      break()
    endif()
  endforeach()
  if(SHERPA_TTS_ESPEAK_NG_ARCHIVE)
    if(NOT EXISTS "${SHERPA_TTS_ESPEAK_NG_ARCHIVE}")
      message(FATAL_ERROR "SHERPA_TTS_ESPEAK_NG_ARCHIVE not found: ${SHERPA_TTS_ESPEAK_NG_ARCHIVE}")
    endif()
    set(espeak_ng_URL "${SHERPA_TTS_ESPEAK_NG_ARCHIVE}")
    file(TO_CMAKE_PATH "${espeak_ng_URL}" espeak_ng_URL)
    set(espeak_ng_URL2)
    message(STATUS "Using local espeak-ng archive: ${espeak_ng_URL}")
  endif()

  FetchContent_Declare(espeak_ng
    URL
      ${espeak_ng_URL}
      ${espeak_ng_URL2}
    URL_HASH ${espeak_ng_HASH}
  )

  FetchContent_GetProperties(espeak_ng)
  if(NOT espeak_ng_POPULATED)
    message(STATUS "Downloading espeak-ng from ${espeak_ng_URL}")
    FetchContent_Populate(espeak_ng)
  endif()
  endif()

  set(_build_shared_libs_bak OFF)
  if(BUILD_SHARED_LIBS)
    set(_build_shared_libs_bak ON)
    set(BUILD_SHARED_LIBS OFF)
  endif()

  add_subdirectory(${espeak_ng_SOURCE_DIR} ${espeak_ng_BINARY_DIR})

  if(_build_shared_libs_bak)
    set(BUILD_SHARED_LIBS ON)
  endif()

  set(espeak_ng_SOURCE_DIR ${espeak_ng_SOURCE_DIR} PARENT_SCOPE)
  if(UNIX AND NOT APPLE)
    target_compile_options(espeak-ng PRIVATE -Wno-unused-result -Wno-format-overflow -Wno-format-truncation -Wno-uninitialized -Wno-format)
  endif()
  target_include_directories(espeak-ng INTERFACE ${espeak_ng_SOURCE_DIR}/src/include ${espeak_ng_SOURCE_DIR}/src/ucd-tools/src/include)
endfunction()
download_espeak_ng_for_piper()
