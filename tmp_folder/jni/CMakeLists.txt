add_library(main STATIC
        main.cpp
        main_batch_gen.cpp
        main_Jeff.cpp
        main_llava.cpp
        main_llava_spec_dec.cpp
        main_medusa.cpp
        main_origin.cpp
        main_spec_dec.cpp
)

target_include_directories(main PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})