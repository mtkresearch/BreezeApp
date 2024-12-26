# jni/prebuilt/src/executor/CMakeLists.txt

# Set the minimum required CMake version
cmake_minimum_required(VERSION 3.10)

# Add the executor library
add_library(executor STATIC
        allocator.cpp
        shared_weights.cpp
        multi_runtime_handler.cpp
        executor.cpp
        tflite_executor.cpp
        neuron_executor.cpp
        neuron_usdk_executor.cpp
        llm_executor.cpp
        llm_medusa_executor.cpp
        llm_ringbuffer_executor.cpp
)

# Include directories
target_include_directories(executor
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link against the llm_helper and common libraries
target_link_libraries(executor
        PUBLIC
        llm_helper
        common
)