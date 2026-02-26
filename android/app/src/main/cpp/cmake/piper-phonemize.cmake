# From sherpa-onnx; requires ESPEAK_NG_DIR and espeak-ng target.
function(download_piper_phonemize)
  if(SHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR)
    if(NOT EXISTS "${SHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR}/CMakeLists.txt")
      message(FATAL_ERROR "SHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR is invalid: ${SHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR}")
    endif()
    set(piper_phonemize_SOURCE_DIR "${SHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR}")
    set(piper_phonemize_BINARY_DIR "${CMAKE_BINARY_DIR}/_deps/piper_phonemize-build-local")
    message(STATUS "Using local piper-phonemize source: ${piper_phonemize_SOURCE_DIR}")
  else()
  include(FetchContent)
  set(piper_phonemize_URL  "https://github.com/csukuangfj/piper-phonemize/archive/78a788e0b719013401572d70fef372e77bff8e43.zip")
  set(piper_phonemize_URL2 "https://hf-mirror.com/csukuangfj/sherpa-onnx-cmake-deps/resolve/main/piper-phonemize-78a788e0b719013401572d70fef372e77bff8e43.zip")
  set(piper_phonemize_HASH "SHA256=89641a46489a4898754643ce57bda9c9b54b4ca46485fdc02bf0dc84b866645d")
  set(possible_file_locations
    $ENV{HOME}/Downloads/piper-phonemize-78a788e0b719013401572d70fef372e77bff8e43.zip
    ${CMAKE_SOURCE_DIR}/piper-phonemize-78a788e0b719013401572d70fef372e77bff8e43.zip
    ${CMAKE_BINARY_DIR}/piper-phonemize-78a788e0b719013401572d70fef372e77bff8e43.zip
    /tmp/piper-phonemize-78a788e0b719013401572d70fef372e77bff8e43.zip)
  foreach(f IN LISTS possible_file_locations)
    if(EXISTS ${f})
      set(piper_phonemize_URL  "${f}")
      file(TO_CMAKE_PATH "${piper_phonemize_URL}" piper_phonemize_URL)
      message(STATUS "Found local piper-phonemize zip: ${piper_phonemize_URL}")
      set(piper_phonemize_URL2)
      break()
    endif()
  endforeach()
  if(SHERPA_TTS_PIPER_PHONEMIZE_ARCHIVE)
    if(NOT EXISTS "${SHERPA_TTS_PIPER_PHONEMIZE_ARCHIVE}")
      message(FATAL_ERROR "SHERPA_TTS_PIPER_PHONEMIZE_ARCHIVE not found: ${SHERPA_TTS_PIPER_PHONEMIZE_ARCHIVE}")
    endif()
    set(piper_phonemize_URL "${SHERPA_TTS_PIPER_PHONEMIZE_ARCHIVE}")
    file(TO_CMAKE_PATH "${piper_phonemize_URL}" piper_phonemize_URL)
    set(piper_phonemize_URL2)
    message(STATUS "Using local piper-phonemize archive: ${piper_phonemize_URL}")
  endif()

  FetchContent_Declare(piper_phonemize
    URL
      ${piper_phonemize_URL}
      ${piper_phonemize_URL2}
    URL_HASH ${piper_phonemize_HASH}
  )

  FetchContent_GetProperties(piper_phonemize)
  if(NOT piper_phonemize_POPULATED)
    message(STATUS "Downloading piper-phonemize from ${piper_phonemize_URL}")
    FetchContent_Populate(piper_phonemize)
  endif()
  endif()

  set(_build_shared_libs_bak OFF)
  if(BUILD_SHARED_LIBS)
    set(_build_shared_libs_bak ON)
    set(BUILD_SHARED_LIBS OFF)
  endif()

  add_subdirectory(${piper_phonemize_SOURCE_DIR} ${piper_phonemize_BINARY_DIR} EXCLUDE_FROM_ALL)

  if(_build_shared_libs_bak)
    set(BUILD_SHARED_LIBS ON)
  endif()
  target_include_directories(piper_phonemize INTERFACE ${piper_phonemize_SOURCE_DIR}/src/include)
endfunction()
set(ESPEAK_NG_DIR ${espeak_ng_SOURCE_DIR})
download_piper_phonemize()
