# The on-device assistant: llama.cpp (pinned submodule) built into one small library, CPU only, arm64.
cmake_minimum_required(VERSION 3.22)
project(stillpoint_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Only the library: no tools, tests, examples, server or curl.
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
# Dot-product instructions: every arm64 phone since about 2018. Q4 models lean on them.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)

# The light (F-Droid) build skips the engine altogether.
if(STILLPOINT_NO_LLM)
  return()
endif()

add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

add_library(stillpoint_llm SHARED stillpoint_llm.cpp)
target_link_libraries(stillpoint_llm PRIVATE llama log)
